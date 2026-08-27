package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
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
 * @property pathPoints the ordered points of the currently displayed travel.
 * @property pois the points of interest to render on the map.
 * @property ghosts the simulated travelers to render on the map (the hidden
 *   ones are already filtered out here).
 * @property nearbyGhosts the full list of travelers around the user BEFORE
 *   the hide filter, used by the travelers menu to show names and restore
 *   hidden ones.
 * @property hiddenGhostIds the travelers the user hid from the map (their
 *   ids), so the menu can restore them and the rendering can skip them.
 * @property travels the journeys available for the one-at-a-time map filter.
 * @property travelCities the detected destinations grouped by travel id, used
 *   to categorize each trip in the filter (travelId -> city names).
 * @property displayedTravelId the travel whose path is shown; null = active travel.
 * @property isLoading true until the first combined emission arrives.
 */
data class MapUiState(
    val userLocation: LocationSample? = null,
    val pathPoints: List<TravelPathPointEntity> = emptyList(),
    val pois: List<PoiEntity> = emptyList(),
    val ghosts: List<Ghost> = emptyList(),
    val nearbyGhosts: List<Ghost> = emptyList(),
    val hiddenGhostIds: Set<String> = emptySet(),
    val travels: List<TravelEntity> = emptyList(),
    val travelCities: Map<Long, List<String>> = emptyMap(),
    val displayedTravelId: Long? = null,
    val isLoading: Boolean = true
)
