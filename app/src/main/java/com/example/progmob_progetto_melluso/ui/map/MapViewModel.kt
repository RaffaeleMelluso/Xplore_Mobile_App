package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.map.MapRepository
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // Which travel is displayed on the map; null means "the active travel".
    private val selectedTravelId = MutableStateFlow<Long?>(null)

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
    ) { location, path, pois, ghosts, (travels, cities) ->
        MapUiState(
            userLocation = location,
            pathPoints = path,
            pois = pois,
            ghosts = ghosts,
            travels = travels,
            travelCities = cities,
            displayedTravelId = selectedTravelId.value,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MapUiState(isLoading = true)
    )

    init {
        // Populate the POI table on first launch (no-op when already seeded).
        viewModelScope.launch { mapRepository.ensurePoiSeed() }
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

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
