package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the map screen.
 *
 * How it works: it combines the repository flows (current location, PERSISTENT
 * travel path, points of interest, ghosts, journal) into a single immutable
 * [MapUiState] exposed as a [StateFlow]. The shown path follows either the
 * active travel (default) or a single past travel selected through the map
 * filter ([selectTravel]). It also seeds the demo POI table once on creation.
 * All asynchrony lives in [viewModelScope]; the UI only collects the state.
 *
 * @param mapRepository the repository exposing the map data streams.
 * @param poiVoteRepository the shared vote engine: the POI card on the map
 *   votes with the SAME rules as the journal and the trends page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val mapRepository: MapRepository,
    private val poiVoteRepository: PoiVoteRepository
) : ViewModel() {

    // Which travel is displayed on the map; null means "the active travel".
    private val selectedTravelId = MutableStateFlow<Long?>(null)

    // The travelers the user hid one by one with the map button. Kept here (not
    // in the repository) because it is a per-screen presentation preference;
    // the hidden ids are part of the UI state so the button can restore them.
    private val hiddenGhostIds = MutableStateFlow<Set<String>>(emptySet())

    // The path to draw: the active travel unless a specific one is selected.
    private val pathFlow = selectedTravelId.flatMapLatest { id ->
        if (id == null) {
            mapRepository.observeActivePath()
        } else {
            mapRepository.observePathFor(id)
        }
    }

    // Journal travels + their detected cities, combined so the filter can
    // label each trip with the places it visited (its category).
    private val travelsAndCitiesFlow = combine(
        mapRepository.observeTravels(),
        mapRepository.observeTravelCities()
    ) { travels, cities ->
        travels to cities
            .groupBy { it.travelId }
            .mapValues { (_, list) ->
                list.sortedBy { it.arrivedAtUtcMillis }.map { it.name }
            }
    }

    /**
     * Combined map state: every repository emission produces a new snapshot.
     *
     * How it works: `WhileSubscribed` keeps the upstream flows (and the sensor
     * subscription they trigger) alive only while the screen is visible, and
     * stops them shortly after the screen leaves the composition to save
     * battery. The initial value is the loading placeholder.
     */
    val uiState: StateFlow<MapUiState> = combine(
        mapRepository.observeCurrentLocation(),
        pathFlow,
        mapRepository.observePois(),
        mapRepository.observeGhosts(),
        travelsAndCitiesFlow
    ) { location, path, pois, ghosts, travelsAndCities ->
        val (travels, cities) = travelsAndCities
        MapUiState(
            userLocation = location,
            pathPoints = path,
            pois = pois,
            ghosts = ghosts,
            nearbyGhosts = ghosts,
            travels = travels,
            travelCities = cities,
            displayedTravelId = selectedTravelId.value,
            isLoading = false
        )
    }
        // The hidden set is composed afterwards: it only filters the ghost
        // list (and reports the hidden ids), so the base combine stays small.
        // The user's votes are composed afterwards: they only enrich the POI
        // card (poiId -> my vote), keeping the base combine small.
        .combine(poiVoteRepository.observeMyVotes()) { state, myVotes ->
            state.copy(
                poiVotes = myVotes.associate { it.poiId to it.voteValue }
            )
        }
        .combine(hiddenGhostIds) { state, hidden ->
            state.copy(
                ghosts = state.ghosts.filterNot { it.marker.userId in hidden },
                hiddenGhostIds = hidden
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MapUiState(isLoading = true)
        )

    init {
        viewModelScope.launch {
            // Populate the POI table on first launch (no-op when already
            // seeded), THEN the demo votes of the ghost community, so the
            // ghost cards and the trends page show real, consistent votes.
            mapRepository.ensurePoiSeed()
            poiVoteRepository.ensureDemoVotes()
        }
    }

    /**
     * Selects which travel is shown on the map.
     *
     * How it works: `null` restores the live/active travel; a travel id shows
     * that trip's stored path alone (the map filter, one trip at a time).
     */
    fun selectTravel(travelId: Long?) {
        selectedTravelId.value = travelId
    }

    /**
     * Hides the given traveler from the map (menu tap on its name).
     *
     * How it works: the id is added to [hiddenGhostIds], which the uiState
     * combine turns into a filtered ghost list for the map rendering.
     */
    fun hideGhost(userId: String) {
        hiddenGhostIds.value = hiddenGhostIds.value + userId
    }

    /** Restores the given traveler on the map (menu tap on a hidden name). */
    fun showGhost(userId: String) {
        hiddenGhostIds.value = hiddenGhostIds.value - userId
    }

    /** Restores every hidden traveler on the map. */
    fun showAllGhosts() {
        hiddenGhostIds.value = emptySet()
    }

    /**
     * Votes a POI up/down from its map card, through the shared vote engine
     * (same rules and score as the journal and the trends page).
     */
    fun votePoi(poiId: Long, voteValue: Int) {
        viewModelScope.launch { poiVoteRepository.votePoi(poiId, voteValue) }
    }

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
