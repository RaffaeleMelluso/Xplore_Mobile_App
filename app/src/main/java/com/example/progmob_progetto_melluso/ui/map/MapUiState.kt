package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.StaleReason

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
 * @property poiVotes the signed-in user's vote per POI (poiId -> 1/-1/0),
 *   so the POI card shows the same up/down state as the journal and trends.
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
 * @property staleTravel the ongoing trip that looks finished (too far from
 *   its start or too old): the map asks the user whether it is still in
 *   progress before closing it. Null when nothing is stale.
 * @property nearbyPoi the POI whose geofence the user is currently inside,
 *   when any: the map auto-opens its bottom sheet and the tracking service
 *   posts the near-POI notification (spec §7.3, geofencing). Null when the
 *   user is outside every fence.
 * @property shownGeofencePoiIds the POIs whose geofence sheet has already been
 *   auto-opened during the CURRENT active travel: once seen they do not reopen
 *   (switching tab and back does not re-show), reset when a new trip begins.
 *   Kept in the ViewModel so it survives tab switches.
 * @property liveSteps the pedometer steps of the ACTIVE travel (live), shown
 *   by the map trip-info screen.
 * @property liveDistanceMeters the distance in meters estimated from the
 *   steps of the ACTIVE travel (live), shown by the map trip-info screen.
 * @property isLoading true until the first combined emission arrives.
 */
/**
 * A stale ongoing travel the user should confirm the end of (spec §7.1,
 * confirmed close instead of a silent background close).
 */
data class StaleTravel(
    val travelId: Long,
    val reason: StaleReason
)

data class MapUiState(
    val userLocation: LocationSample? = null,
    val pathPoints: List<TravelPathPointEntity> = emptyList(),
    val pois: List<PoiEntity> = emptyList(),
    val poiVotes: Map<Long, Int> = emptyMap(),
    val ghosts: List<Ghost> = emptyList(),
    val nearbyGhosts: List<Ghost> = emptyList(),
    val hiddenGhostIds: Set<String> = emptySet(),
    val travels: List<TravelEntity> = emptyList(),
    val travelCities: Map<Long, List<String>> = emptyMap(),
    val displayedTravelId: Long? = null,
    val staleTravel: StaleTravel? = null,
    val nearbyPoi: PoiEntity? = null,
    val shownGeofencePoiIds: Set<Long> = emptySet(),
    val liveSteps: Long = 0,
    val liveDistanceMeters: Double = 0.0,
    val isLoading: Boolean = true
)
