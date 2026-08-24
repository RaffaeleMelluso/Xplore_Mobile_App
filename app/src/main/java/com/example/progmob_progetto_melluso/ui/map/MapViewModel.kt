package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.map.MapRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the map screen.
 *
 * How it works: it combines the four repository flows (current location, active
 * travel path, points of interest, ghosts) into a single immutable
 * [MapUiState] exposed as a [StateFlow]. It also seeds the demo POI table once
 * on creation. All asynchrony lives in [viewModelScope]; the UI only collects
 * the state, with no business logic (spec §4).
 *
 * @param mapRepository the repository exposing the map data streams.
 */
class MapViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

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
        mapRepository.observeActivePath(),
        mapRepository.observePois(),
        mapRepository.observeGhosts()
    ) { location, path, pois, ghosts ->
        MapUiState(
            userLocation = location,
            pathPoints = path,
            pois = pois,
            ghosts = ghosts,
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

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
