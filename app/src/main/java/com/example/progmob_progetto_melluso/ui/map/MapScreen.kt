package com.example.progmob_progetto_melluso.ui.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.progmob_progetto_melluso.R
import kotlinx.coroutines.delay
import com.google.gson.JsonObject
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalTransport
import com.example.progmob_progetto_melluso.journal.JournalVisit
import com.example.progmob_progetto_melluso.journal.TransportMode
import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.service.TrackingService
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.PathSegmenter
import com.example.progmob_progetto_melluso.travel.StaleReason
import com.example.progmob_progetto_melluso.ui.common.PoiImage
import com.example.progmob_progetto_melluso.ui.common.PoiVoteButtons
import com.example.progmob_progetto_melluso.ui.poi.AddPoiDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** OpenFreeMap vector style (free, no API key, no OSM tile-policy issues). */
// internal so the journal's off-screen MapSnapshotter reuses the same style
// (single source of truth for the OpenFreeMap tiles).
internal const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Map screen: MapLibre Native with OpenFreeMap vector tiles, the user position,
 * the active travel path, the points of interest and the simulated ghosts.
 *
 * How it works: the MapLibre [MapView] is a classic View hosted in an
 * [AndroidView], so Compose never re-composes the map itself. All data is
 * rendered through GeoJSON sources + style layers, updated only when the
 * underlying state changes (diff strategy in [MapOverlays]), which keeps the
 * load on old smartphones low.
 *
 * The camera is initially centered on the first usable location and then
 * remains under the user's control; the floating button forces a recenter.
 * Tapping a POI or ghost opens a small info card; tapping elsewhere
 * closes it.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    cameraState: MapCameraState? = null,
    onCameraChanged: (MapCameraState) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(
        factory = remember(context) {
            MapViewModelFactory(
                mapRepository = AppContainer.mapRepository(context),
                poiRepository = AppContainer.poiRepository(context),
                poiVoteRepository = AppContainer.poiVoteRepository(context),
                stepRepository = AppContainer.stepRepository(context)
            )
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val overlaysRef = remember { mutableStateOf<MapOverlays?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var handledRecenterTick by remember { mutableIntStateOf(0) }
    var cameraInitialized by remember { mutableStateOf(false) }

    // Transient UI state is rememberSaveable so a rotation (activity
    // recreation) keeps the user exactly where they were — selected tab, open
    // dialog, picked map point — instead of resetting the map and cancelling
    // any field being edited.

    // Whether the travel-filter bottom sheet is open (via button).
    var showTravelFilter by rememberSaveable { mutableStateOf(false) }

    // Whether the nearby-travelers menu (hide/show by name) is open.
    var showNearbyTravelers by rememberSaveable { mutableStateOf(false) }

    // Whether the user is choosing ON THE MAP where to place the new POI
    // (user reporting, the POI-reporting requirement): any map tap then places the point.
    var pickingPoiLocation by rememberSaveable { mutableStateOf(false) }

    // The map point the user tapped while choosing the POI location; stored as
    // plain doubles (LatLng is not saveable across process/config changes) and
    // reconstructed here. Non-null opens the creation form prefilled.
    var pickedPoiLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickedPoiLon by rememberSaveable { mutableStateOf<Double?>(null) }
    val pickedPoiLocation: LatLng? =
        pickedPoiLat?.let { lat -> pickedPoiLon?.let { lon -> LatLng(lat, lon) } }

    // Selected marker info window: the tapped POI (by id, re-resolved from the
    // reactive list) or ghost (ephemeral, kept in memory), shown as a sheet.
    var selectedPoiId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedPoi: PoiEntity? =
        selectedPoiId?.let { id -> uiState.pois.firstOrNull { it.id == id } }
    var selectedGhost by remember { mutableStateOf<Ghost?>(null) }

    // Trip-info screen (live timer/steps/distance) opened by the top-right button.
    var showTripInfo by rememberSaveable { mutableStateOf(false) }

    // Whether the compact map-actions menu (three dots, bottom-left) is expanded.
    var showMapActions by rememberSaveable { mutableStateOf(false) }

    // Drives the geofence auto-opened sheet. Plain remember: it resets when
    // the map screen leaves composition; the per-trip "already shown" POI set
    // lives in the ViewModel, so switching tab and back does not re-open a
    // place that was already shown.
    var autoSheetVisible by remember { mutableStateOf(false) }

    // Location state is re-checked on every ON_RESUME (returning from the
    // system settings) AND polled while the screen is composed: enabling GPS
    // from the quick settings toggle does NOT pause the activity, so no
    // lifecycle event fires and ON_RESUME alone would leave the block screen
    // stuck until the app is restarted. The poll is cheap (two system calls
    // every couple of seconds) and makes the map appear right after the user
    // turns location on, without restarting anything.
    var locationRefreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LOCATION_POLL_MILLIS)
            locationRefreshTick++
        }
    }
    val locationPermissionGranted = remember(locationRefreshTick) { hasLocationPermission(context) }
    val gpsEnabled = remember(locationRefreshTick) { isGpsEnabled(context) }
    val mapReady = locationPermissionGranted && gpsEnabled

    // If permission was granted from this screen (rather than by the initial
    // request in MainActivity), the tracking service may not be running yet.
    // Start it as soon as both prerequisites are available so the first fix is
    // published without requiring an app restart. The effect also covers a
    // return from system settings and a GPS toggle made outside the app.
    LaunchedEffect(mapReady) {
        if (mapReady) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier.fillMaxSize()) {
        // The map is ALWAYS created here, even before location is usable: the
        // block screen below is just an opaque overlay on top. Creating the
        // MapView on demand (block -> GPS enabled) produced a view that missed
        // onStart/onResume and stayed blank until the app was restarted.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // One-time MapLibre initialization for this process.
                MapLibre.getInstance(ctx)

                MapView(ctx).apply {
                    mapViewRef.value = this
                    getMapAsync { map ->
                        map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                            val overlays = MapOverlays(ctx, map, style)
                            overlaysRef.value = overlays
                            overlays.update(uiState)
                            // Restore the camera chosen by the user when the
                            // map tab was previously visible. Only a map with
                            // no saved camera uses the city fallback.
                            if (cameraState != null) {
                                map.cameraPosition = cameraState.toCameraPosition()
                                cameraInitialized = true
                            } else {
                                val initialTarget = uiState.userLocation?.let {
                                    LatLng(it.latitude, it.longitude)
                                } ?: DEFAULT_PRE_FIX_CAMERA
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(initialTarget)
                                    .zoom(if (uiState.userLocation == null) INITIAL_ZOOM else FOLLOW_ZOOM)
                                    .build()
                                if (uiState.userLocation != null) {
                                    cameraInitialized = true
                                }
                            }
                        }
                        // Tap on empty map closes the info card; the card is
                        // only shown when a POI/ghost is actually hit. While
                        // picking a POI location, ANY tap places the point
                        // and consumes the gesture (no card opens).
                        map.addOnCameraIdleListener {
                            map.cameraPosition?.let { position ->
                                position.target?.let { target ->
                                    onCameraChanged(
                                        MapCameraState(
                                            latitude = target.latitude,
                                            longitude = target.longitude,
                                            zoom = position.zoom,
                                            bearing = position.bearing,
                                            tilt = position.tilt
                                        )
                                    )
                                }
                            }
                        }
                        map.addOnMapClickListener { latLng ->
                            if (pickingPoiLocation) {
                                pickedPoiLat = latLng.latitude
                                pickedPoiLon = latLng.longitude
                                return@addOnMapClickListener true
                            }
                            val poi = overlaysRef.value?.poiAt(latLng)
                            val ghost = overlaysRef.value?.ghostAt(latLng)
                            // The route polyline is clickable too: tapping a
                            // ghost's path opens the same card as its marker.
                            val ghostViaPath = overlaysRef.value?.ghostAtPath(latLng)
                            if (poi != null) {
                                selectedPoiId = poi.id
                                selectedGhost = null
                            } else if (ghost != null || ghostViaPath != null) {
                                selectedGhost = ghost ?: ghostViaPath
                                selectedPoiId = null
                            } else {
                                selectedPoiId = null
                                selectedGhost = null
                            }
                            false
                        }
                    }
                }
            },
            update = { mapView ->
                mapViewRef.value = mapView
                overlaysRef.value?.update(uiState)
            },
            // MapLibre must be destroyed to release GL resources and avoid leaks.
            onRelease = { mapView ->
                mapView.onDestroy()
                mapViewRef.value = null
                overlaysRef.value = null
            }
        )

        // Forward the Activity lifecycle so the map can pause/resume GL rendering.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapViewRef.value?.onStart()
                    Lifecycle.Event.ON_RESUME -> {
                        mapViewRef.value?.onResume()
                        locationRefreshTick++
                    }
                    Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
                    Lifecycle.Event.ON_STOP -> mapViewRef.value?.onStop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            // LifecycleEventObserver only receives FUTURE events: forward the
            // CURRENT state right away, otherwise a map composed while the
            // activity is already RESUMED (login -> home, or block -> GPS on)
            // would never receive onStart/onResume and stay blank.
            val currentState = lifecycleOwner.lifecycle.currentState
            if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapViewRef.value?.onStart()
                mapViewRef.value?.onResume()
            } else if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapViewRef.value?.onStart()
            }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Do not follow location updates: panning and zooming the map must not
        // be interrupted while the user is inspecting another area. Center on
        // the first fix only, or when the explicit recenter button is pressed.
        LaunchedEffect(uiState.userLocation, recenterTick, mapReady) {
            if (!mapReady || uiState.userLocation == null ||
                cameraInitialized && recenterTick == handledRecenterTick
            ) {
                return@LaunchedEffect
            }
            val mapView = mapViewRef.value ?: return@LaunchedEffect
            val location = uiState.userLocation ?: return@LaunchedEffect
            mapView.getMapAsync { map ->
                val current = map.cameraPosition
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(current?.zoom ?: FOLLOW_ZOOM)
                    .bearing(current?.bearing ?: 0.0)
                    .tilt(current?.tilt ?: 0.0)
                    .build()
                cameraInitialized = true
                handledRecenterTick = recenterTick
            }
        }

        // Block the map with a full-screen notice until location can be used.
        LocationBlockScreen(
            locationPermissionGranted = locationPermissionGranted,
            gpsEnabled = gpsEnabled,
            onLocationPermissionGranted = { locationRefreshTick++ }
        )


        // Picking banner: tells the user what to do and lets them cancel.
        if (pickingPoiLocation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.map_add_poi_picking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        pickingPoiLocation = false
                        pickedPoiLat = null
                        pickedPoiLon = null
                    }) {
                        Text(stringResource(R.string.add_poi_cancel))
                    }
                }
            }
        }

        if (showTravelFilter) {
            TravelFilterSheet(
                travels = uiState.travels,
                travelCities = uiState.travelCities,
                displayedTravelId = uiState.displayedTravelId,
                onSelect = { travelId ->
                    viewModel.selectTravel(travelId)
                    showTravelFilter = false
                },
                onDismiss = { showTravelFilter = false }
            )
        }

        // Close the ghost card when its ghost disappears from the map (e.g. it
        // was hidden with the hide button): no stale card on screen.
        LaunchedEffect(uiState.ghosts) {
            val current = selectedGhost ?: return@LaunchedEffect
            if (uiState.ghosts.none { it.marker.userId == current.marker.userId }) {
                selectedGhost = null
            }
        }

        // Map actions: ONE trigger (three dots, bottom-left) anchored at the
        // bottom, in line with the recenter button. Tapping it expands a column
        // of labeled pills (Add POI, My trips, Nearby travelers) ABOVE the
        // trigger; the trigger itself never moves. The badge reports how many
        // travelers are currently hidden.
        if (mapReady) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // The three actions, expanding ABOVE the trigger: they are
                // inserted before it in the column, so the FAB stays anchored
                // at the bottom.
                if (showMapActions) {
                    // Add POI (the POI-reporting requirement): starts/cancels the location picking
                    // on the map; the form opens on the chosen point and the
                    // POI joins the shared pool.
                    MapActionPill(
                        icon = if (pickingPoiLocation) Icons.Filled.Close else Icons.Filled.Add,
                        label = stringResource(
                            if (pickingPoiLocation) R.string.map_add_poi_cancel_label else R.string.map_add_poi_label
                        ),
                        onClick = {
                            if (pickingPoiLocation) {
                                pickingPoiLocation = false
                                pickedPoiLat = null
                                pickedPoiLon = null
                            } else {
                                pickingPoiLocation = true
                                pickedPoiLat = null
                                pickedPoiLon = null
                            }
                            showMapActions = false
                        }
                    )
                    // My trips: opens the sheet listing the trips, one at a
                    // time on the map.
                    MapActionPill(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = stringResource(R.string.map_my_trips_label),
                        onClick = {
                            showTravelFilter = true
                            showMapActions = false
                        }
                    )
                    // Nearby travelers: opens the sheet listing the travelers
                    // by name to hide/restore them one by one.
                    MapActionPill(
                        icon = Icons.Filled.Person,
                        label = stringResource(R.string.map_nearby_travelers_title),
                        onClick = {
                            showNearbyTravelers = true
                            showMapActions = false
                        }
                    )
                }
                Box {
                    MapActionFab(
                        onClick = { showMapActions = !showMapActions },
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.map_actions_button)
                    )
                    if (uiState.hiddenGhostIds.isNotEmpty()) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 10.dp, y = (-10).dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(text = uiState.hiddenGhostIds.size.toString())
                        }
                    }
                }
            }
        }
        if (showNearbyTravelers) {
            NearbyTravelersSheet(
                nearbyGhosts = uiState.nearbyGhosts,
                hiddenGhostIds = uiState.hiddenGhostIds,
                onToggleGhost = { userId ->
                    if (userId in uiState.hiddenGhostIds) {
                        viewModel.showGhost(userId)
                    } else {
                        viewModel.hideGhost(userId)
                    }
                },
                onShowAll = { viewModel.showAllGhosts() },
                onDismiss = { showNearbyTravelers = false }
            )
        }

        // Demo POI creation form, opened on the point the user picked on the
        // map (lat/lon prefilled, still editable): the ViewModel stores the
        // POI and the reactive flows sync map, ghosts and trends automatically.
        pickedPoiLocation?.let { picked ->
            AddPoiDialog(
                initialLat = picked.latitude,
                initialLon = picked.longitude,
                onConfirm = { poi ->
                    viewModel.addPoi(poi)
                    pickedPoiLat = null
                    pickedPoiLon = null
                    pickingPoiLocation = false
                },
                onDismiss = {
                    pickedPoiLat = null
                    pickedPoiLon = null
                    pickingPoiLocation = false
                }
            )
        }

        if (uiState.isLoading && mapReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // POI details as a LARGE bottom sheet (about twice the old top card):
        // photo, name, description and votes in a readable layout. It opens
        // when a marker is tapped AND automatically when the geofencing puts
        // the user inside a POI radius (the POI proximity requirement); the shared vote engine
        // keeps the score in sync with the journal and the trends page.
        val poi = selectedPoi
        val nearby = uiState.nearbyPoi

        // Geofence auto-open, shown ONLY when the map is usable (permission +
        // GPS on), AFTER a 5-second grace so the user orients on entering the
        // app or enabling location, and ONCE PER TRIP: the "already shown" set
        // lives in the ViewModel (survives tab switches), so changing tab and
        // coming back does NOT re-open a seen POI.
        val inFence = mapReady && poi == null && selectedGhost == null && nearby != null

        LaunchedEffect(nearby?.id, mapReady) {
            if (!inFence || nearby == null || nearby.id in uiState.shownGeofencePoiIds) {
                autoSheetVisible = false
            } else {
                autoSheetVisible = false
                delay(5_000)
                // Re-check after the wait: the user may have navigated away or
                // selected another marker during the grace period.
                if (nearby != null && nearby.id !in uiState.shownGeofencePoiIds) {
                    viewModel.markGeofenceShown(nearby.id)
                    autoSheetVisible = true
                }
            }
        }

        val autoPoi = if (
            autoSheetVisible && inFence && poi == null && selectedGhost == null && nearby != null
        ) nearby else null
        val shownPoi = poi ?: autoPoi
        if (shownPoi != null) {
            // The live POI row: after voting, `pois` re-emits with the updated
            // cached score, so the score shown in the sheet never goes stale.
            val livePoi = uiState.pois.firstOrNull { it.id == shownPoi.id } ?: shownPoi
            // Voting is reserved to the POI you are actually NEAR (the geofence
            // one): a place tapped from far away opens read-only, exactly like
            // the trends page, so you cannot vote somewhere you never visited.
            val canVote = livePoi.id == uiState.nearbyPoi?.id
            PoiDetailSheet(
                poi = livePoi,
                myVote = uiState.poiVotes[livePoi.id] ?: 0,
                canVote = canVote,
                onVotePoi = { id, voteValue -> viewModel.votePoi(id, voteValue) },
                onDismiss = {
                    if (poi != null) selectedPoiId = null
                    if (autoPoi != null) {
                        viewModel.markGeofenceShown(autoPoi.id)
                        autoSheetVisible = false
                    }
                }
            )
        }

        // Specialized card for a tapped ghost: reuses the journal media gallery
        // and the visited-POI rows (with votes), so a ghost reads exactly like
        // a real traveler's journey.
        val ghost = selectedGhost
        if (ghost != null) {
            GhostDetailCard(
                ghost = ghost,
                onDismiss = { selectedGhost = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }

        // Recenter button: only useful once the map is actually usable.
        if (mapReady) {
            MapActionFab(
                onClick = { recenterTick++ },
                icon = Icons.Filled.LocationOn,
                contentDescription = stringResource(R.string.map_recenter),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        // Trip-info button (top-right): opens the live screen with the trip
        // timer, the pedometer steps and the distance in km. Small and flat so
        // it never competes with the map content.
        if (mapReady) {
            Surface(
                onClick = { showTripInfo = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.map_trip_info_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (showTripInfo) {
            TripInfoOverlay(
                activeTravel = uiState.travels.firstOrNull { it.endUtcMillis == null },
                steps = uiState.liveSteps,
                distanceMeters = uiState.liveDistanceMeters,
                onDismiss = { showTripInfo = false }
            )
        }

        // Stale-travel confirmation (the travel lifecycle requirement): when the active trip looks
        // finished — the user is far from where it started or it began days
        // ago — the app ASKS instead of closing it silently in the background.
        val stale = uiState.staleTravel
        if (stale != null) {
            AlertDialog(
                onDismissRequest = { viewModel.keepStaleTravel() },
                title = { Text(stringResource(R.string.map_stale_travel_title)) },
                text = {
                    Text(
                        if (stale.reason == StaleReason.TOO_OLD) {
                            stringResource(R.string.map_stale_travel_body_old)
                        } else {
                            stringResource(R.string.map_stale_travel_body_far)
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::endStaleTravel) {
                        Text(stringResource(R.string.map_stale_travel_end))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::keepStaleTravel) {
                        Text(stringResource(R.string.map_stale_travel_keep))
                    }
                }
            )
        }
    }
}

/**
 * Large bottom sheet with the details of a POI (geofencing sheet, the POI proximity requirement).
 *
 * How it works: a [ModalBottomSheet] (about twice the size of the old top
 * card) showing the real photo, the name, the category/city, a readable
 * description and the shared up/down votes, in the app's surface style. It
 * opens from a marker tap AND automatically when the user enters the POI
 * geofence; dragging it down or tapping the X dismisses it.
 */

private fun MapCameraState.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(latitude, longitude))
    .zoom(zoom)
    .bearing(bearing)
    .tilt(tilt)
    .build()

private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0
private const val INITIAL_ZOOM = 15.0
private const val FOLLOW_ZOOM = 16.0
/** City-view camera used until the first location fix arrives. */
private val DEFAULT_PRE_FIX_CAMERA = LatLng(45.0703, 7.6869)

/** How often the location permission/GPS state is re-checked while visible. */
private const val LOCATION_POLL_MILLIS = 2_000L

/**
 * Full-screen block shown when the map cannot use location.
 *
 * How it works: when the location permission is missing or the device location
 * services are off, this covers the whole screen (theme background, no map
 * rendering behind it) with an explanation and a one-tap action: request the
 * runtime permission, or open the system location settings. When both
 * conditions are fine nothing is drawn, so the map shows through. The state is
 * re-read whenever the screen resumes, so fixing the issue hides the block
 * automatically.
 *
 * @param locationPermissionGranted whether the location permission is granted.
 * @param gpsEnabled whether a location provider (GPS/network) is enabled.
 * @param onLocationPermissionGranted callback used to refresh the map
 *   immediately after the user grants fine or coarse location access.
 */
@Composable
private fun LocationBlockScreen(
    locationPermissionGranted: Boolean,
    gpsEnabled: Boolean,
    onLocationPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            onLocationPermissionGranted()
        }
    }

    val message: Int
    val actionLabel: Int
    val onAction: () -> Unit

    when {
        !locationPermissionGranted -> {
            message = R.string.map_warning_permission
            actionLabel = R.string.map_warning_grant
            onAction = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        !gpsEnabled -> {
            message = R.string.map_warning_gps
            actionLabel = R.string.map_warning_enable
            onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }
        else -> return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = stringResource(actionLabel))
            }
        }
    }
}

/** True when either the fine or the coarse location permission is granted. */
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** True when at least one location provider (GPS or network) is enabled. */
private fun isGpsEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
