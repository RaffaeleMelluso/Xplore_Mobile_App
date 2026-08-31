package com.example.progmob_progetto_melluso.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.journal.TravelJournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the travel journal tab.
 *
 * How it works: it hosts the journal list flow, the currently opened travel
 * (mutated by user intents such as start/stop/open/edit/vote) and the detail
 * flow of that travel, merged into one immutable [TravelJournalUiState] via
 * stateIn. All asynchrony lives in [viewModelScope]; the UI only collects the
 * state and emits intents (spec §4).
 *
 * @param journalRepository the journal single source of truth.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelJournalViewModel(
    private val journalRepository: TravelJournalRepository
) : ViewModel() {

    // Which travel is opened, if any. The list screen keeps it null.
    private val selectedTravelId = MutableStateFlow<Long?>(null)

    // True while a start/stop action is being processed: guards the buttons
    // against spam taps (a second tap is ignored until the first completes).
    private val travelActionPending = MutableStateFlow(false)

    /** The journal list + the active travel, as a continuous flow. */
    private val travelsFlow = journalRepository.observeJournal()

    /** The detail of the opened travel, or null when none is opened. */
    private val detailFlow = selectedTravelId.flatMapLatest { id ->
        if (id == null) flowOf(null) else journalRepository.observeTravelDetails(id)
    }

    /** Live pedometer totals of the ONGOING trip (journal card stats). */
    private val activeIdFlow = travelsFlow.map { travels ->
        travels.firstOrNull { it.endUtcMillis == null }?.travelId
    }
    private val activeStepsFlow = activeIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0L) else journalRepository.observeTravelSteps(id)
    }
    private val activeDistanceFlow = activeIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0.0) else journalRepository.observeTravelDistance(id)
    }

    /**
     * Combined journal state: everything the tab needs.
     *
     * How it works: `WhileSubscribed` keeps the upstream Room flows alive only
     * while the tab is on screen; the initial value is the loading placeholder.
     */
    val uiState: StateFlow<TravelJournalUiState> = combine(
        travelsFlow,
        detailFlow,
        travelActionPending,
        activeStepsFlow,
        activeDistanceFlow
    ) { travels, detail, pending, steps, distance ->
        TravelJournalUiState(
            travels = travels,
            activeTravel = travels.firstOrNull { it.endUtcMillis == null },
            activeSteps = steps,
            activeDistanceMeters = distance,
            selectedTravelId = selectedTravelId.value,
            detail = detail,
            isTravelActionPending = pending,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TravelJournalUiState(isLoading = true)
    )

    /** Starts a new travel (or continues the active one). */
    fun startTravel() {
        if (travelActionPending.value) return
        travelActionPending.value = true
        viewModelScope.launch {
            try {
                journalRepository.startTravel()
            } finally {
                travelActionPending.value = false
            }
        }
    }

    /** Stops the active travel. */
    fun stopTravel() {
        if (travelActionPending.value) return
        travelActionPending.value = true
        viewModelScope.launch {
            try {
                journalRepository.stopTravel()
            } finally {
                travelActionPending.value = false
            }
        }
    }

    /**
     * Opens a travel in the detail screen. The visited destinations and the
     * POI visits are detected from the path points first, so the detail always
     * reflects them (idempotent: renamed cities and existing visits are kept).
     */
    fun openTravel(travelId: Long) {
        viewModelScope.launch {
            journalRepository.detectCities(travelId)
            journalRepository.detectVisits(travelId)
            selectedTravelId.value = travelId
        }
    }

    /** Closes the detail screen, back to the list. */
    fun closeTravel() {
        selectedTravelId.value = null
    }

    /** Saves the user-edited title and description of the opened travel. */
    fun saveTravelDetails(title: String, description: String) {
        val id = selectedTravelId.value ?: return
        viewModelScope.launch {
            journalRepository.updateTravelDetails(
                id,
                title.trim().ifBlank { null },
                description.trim().ifBlank { null }
            )
        }
    }

    /** Adds a photo/video to the opened travel. */
    fun addMedia(uri: String, mediaType: String) {
        val id = selectedTravelId.value ?: return
        viewModelScope.launch { journalRepository.addMedia(id, uri, mediaType) }
    }

    /** Removes a photo/video from the opened travel. */
    fun removeMedia(mediaId: Long) {
        viewModelScope.launch { journalRepository.removeMedia(mediaId) }
    }

    /**
     * Reports a transport segment for the opened travel (mode + optional line
     * number), the simple flow of spec §3.1.
     */
    fun addTransport(mode: com.example.progmob_progetto_melluso.journal.TransportMode, lineName: String?) {
        val id = selectedTravelId.value ?: return
        viewModelScope.launch { journalRepository.addTransport(id, mode, lineName) }
    }

    /** Removes a reported transport segment from the opened travel. */
    fun removeTransport(transportId: Long) {
        viewModelScope.launch { journalRepository.removeTransport(transportId) }
    }

    /** Renames a detected destination (region/city) of the opened travel. */
    fun renameCity(cityId: Long, name: String) {
        viewModelScope.launch {
            journalRepository.renameCity(cityId, name.trim().ifBlank { return@launch })
        }
    }

    /** Removes a visited POI from the opened travel's journal. */
    fun removeVisit(visitId: Long) {
        viewModelScope.launch { journalRepository.removeVisit(visitId) }
    }

    /** Deletes a travel (closed first if it was still active). */
    fun deleteTravel(travelId: Long) {
        viewModelScope.launch { journalRepository.deleteTravel(travelId) }
    }

    /** Votes a POI up (+1) or down (-1); voting the same side again toggles it off. */
    fun votePoi(poiId: Long, voteValue: Int) {
        viewModelScope.launch { journalRepository.votePoi(poiId, voteValue) }
    }

    companion object {
        /** Delay before the upstream flows stop after the tab leaves the composition. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}