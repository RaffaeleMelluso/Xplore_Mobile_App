package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.LocationSample

/**
 * Immutable UI state for the map screen.
 *
 * How it works: the Compose screen only collects this state and never mutates
 * it directly. Every field is produced by [MapViewModel] from the repository
 * flows; the screen renders the values without any business logic.
 *
 * @property userLocation the latest location fix, null until the first one.
 * @property pathPoints the ordered points of the active travel path.
 * @property pois the points of interest to render on the map.
 * @property ghosts the simulated travelers to render on the map.
 * @property isLoading true until the first combined emission arrives.
 */
data class MapUiState(
    val userLocation: LocationSample? = null,
    val pathPoints: List<TravelPathPointEntity> = emptyList(),
    val pois: List<PoiEntity> = emptyList(),
    val ghosts: List<GhostEntity> = emptyList(),
    val isLoading: Boolean = true
)
