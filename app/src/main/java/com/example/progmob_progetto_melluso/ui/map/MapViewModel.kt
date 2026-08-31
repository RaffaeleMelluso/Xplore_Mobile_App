package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.poi.PoiRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.step.StepRepository
import com.example.progmob_progetto_melluso.travel.PoiProximity
import com.example.progmob_progetto_melluso.travel.TravelConfig
import com.example.progmob_progetto_melluso.travel.TravelStaleness
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
 * @param poiRepository the POI catalog: user-created POIs are stored here and
 *   immediately reach every screen (map, ghosts, trends) through Room flows.
 * @param poiVoteRepository the shared vote engine: the POI card on the map
 *   votes with the SAME rules as the journal and the trends page.
 * @param stepRepository the pedometer totals of the active travel, shown live
 *   by the map trip-info screen (timer, steps, distance).
 * @param config travel thresholds, including the staleness ones (how far / how
 *   old a trip can be before the user is asked whether it is still ongoing).
 * @param clock provides "now" in UTC millis; injectable for deterministic tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val mapRepository: MapRepository,
    private val poiRepository: PoiRepository,
    private val poiVoteRepository: PoiVoteRepository,
    private val stepRepository: StepRepository,
    private val config: TravelConfig = TravelConfig(),
    private val clock: () -> Long = System::currentTimeMillis
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

    // The stale trip the user already answered ("keep it"): the prompt must
    // not reappear on every emission while the same trip stays active.
    private val dismissedStaleTravelId = MutableStateFlow<Long?>(null)

    // POIs whose geofence sheet was already auto-opened during the current
    // active travel: once seen they do not reopen, even switching tab and
    // back (the ViewModel survives the MapScreen leaving the composition).
    // Reset when a new active travel begins.
    private val shownGeofencePoiIds = MutableStateFlow<Set<Long>>(emptySet())

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

    // Geofencing: the closest POI whose fence the user is currently inside
    // (null when outside every fence). The tracking service posts the
    // notification on the same signal; here it drives the auto-opened POI
    // bottom sheet on the map.
    private val nearbyPoiFlow = combine(
        mapRepository.observeCurrentLocation(),
        mapRepository.observePois()
    ) { location, pois ->
        if (location == null || pois.isEmpty()) {
            null
        } else {
            PoiProximity.near(
                location.latitude,
                location.longitude,
                pois,
                config.poiVisitRadiusMeters
            ).minByOrNull { poi ->
                GeoUtils.haversineMeters(location.latitude, location.longitude, poi.lat, poi.lon)
            }
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
        val activeTravel = travels.firstOrNull { it.endUtcMillis == null }
        // Staleness is only evaluated for the LIVE trip (not while a past
        // travel is selected on the map): far from the anchor or too old ->
        // the user is asked whether the vacation is still ongoing.
        val stale = if (selectedTravelId.value == null) {
            activeTravel?.let { travel ->
                val anchor = path.firstOrNull()
                TravelStaleness.evaluate(
                    startUtcMillis = travel.startUtcMillis,
                    anchorLat = anchor?.latitude,
                    anchorLon = anchor?.longitude,
                    currentLat = location?.latitude,
                    currentLon = location?.longitude,
                    maxDurationMillis = config.tripEndDurationMillis,
                    maxDistanceMeters = config.tripEndDistanceMeters,
                    nowMillis = clock()
                )?.let { reason -> StaleTravel(travel.id, reason) }
            }
        } else {
            null
        }
        MapUiState(
            userLocation = location,
            pathPoints = path,
            pois = pois,
            ghosts = ghosts,
            nearbyGhosts = ghosts,
            travels = travels,
            travelCities = cities,
            displayedTravelId = selectedTravelId.value,
            staleTravel = stale,
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
        .combine(dismissedStaleTravelId) { state, dismissed ->
            state.copy(
                staleTravel = state.staleTravel?.takeIf { it.travelId != dismissed }
            )
        }
        .combine(nearbyPoiFlow) { state, nearby ->
            state.copy(nearbyPoi = nearby)
        }
        .combine(shownGeofencePoiIds) { state, shown ->
            state.copy(shownGeofencePoiIds = shown)
        }
        .combine(stepRepository.observeActiveTravelSteps()) { state, steps ->
            state.copy(liveSteps = steps)
        }
        .combine(stepRepository.observeActiveTravelDistance()) { state, distance ->
            state.copy(liveDistanceMeters = distance)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MapUiState(isLoading = true)
        )

    init {
        viewModelScope.launch {
            // Populate the POI table on first launch (no-op when already
            // seeded), THEN the demo votes of the ghost community and of the
            // other demo cities, so the ghost cards and the trends page show
            // real, consistent votes.
            mapRepository.ensurePoiSeed()
            poiVoteRepository.ensureDemoVotes()
        }
        // The "already shown" geofence popups belong to ONE trip: when the
        // active travel changes (a new trip starts), a POI can be suggested
        // again.
        viewModelScope.launch {
            var lastActiveTravelId: Long? = null
            mapRepository.observeTravels().collect { travels ->
                val activeId = travels.firstOrNull { it.endUtcMillis == null }?.id
                if (activeId != lastActiveTravelId) {
                    lastActiveTravelId = activeId
                    if (shownGeofencePoiIds.value.isNotEmpty()) {
                        shownGeofencePoiIds.value = emptySet()
                    }
                }
            }
        }
    }

    /**
     * Marks a POI's geofence sheet as already shown during this trip, so it
     * will not reopen until a new travel begins (spec §7.3, one trigger per
     * trip for each place).
     */
    fun markGeofenceShown(poiId: Long) {
        shownGeofencePoiIds.value = shownGeofencePoiIds.value + poiId
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

    /**
     * Stores a user-created POI (spec §3.3, no moderation in the demo). The
     * reactive POI flow re-emits, so the new marker appears on the map and
     * the POI joins the trends pool without any extra wiring.
     */
    fun addPoi(poi: PoiEntity) {
        viewModelScope.launch { poiRepository.addPoi(poi) }
    }

    /**
     * The user says the stale trip is still ongoing: stop asking about it
     * while this travel stays active.
     */
    fun keepStaleTravel() {
        val id = uiState.value.staleTravel?.travelId ?: return
        dismissedStaleTravelId.value = id
    }

    /**
     * The user confirms the vacation is over: close the active travel. The
     * next recorded fix opens a fresh trip anchored at the new location.
     */
    fun endStaleTravel() {
        dismissedStaleTravelId.value = null
        viewModelScope.launch { mapRepository.stopTravel() }
    }

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
