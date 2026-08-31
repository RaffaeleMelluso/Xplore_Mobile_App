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
import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalTransport
import com.example.progmob_progetto_melluso.journal.JournalVisit
import com.example.progmob_progetto_melluso.journal.TransportMode
import com.example.progmob_progetto_melluso.ghost.Ghost
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
 * The camera follows the user only on significant movement (avoids
 * animation/re-layout churn on tiny GPS jitter); the floating button forces a
 * recenter. Tapping a POI or ghost opens a small info card; tapping elsewhere
 * closes it.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
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
    val lastCamera = remember { mutableStateOf<LatLng?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }

    // Transient UI state is rememberSaveable so a rotation (activity
    // recreation) keeps the user exactly where they were — selected tab, open
    // dialog, picked map point — instead of resetting the map and cancelling
    // any field being edited.

    // Whether the travel-filter bottom sheet is open (via button).
    var showTravelFilter by rememberSaveable { mutableStateOf(false) }

    // Whether the nearby-travelers menu (hide/show by name) is open.
    var showNearbyTravelers by rememberSaveable { mutableStateOf(false) }

    // Whether the user is choosing ON THE MAP where to place the new POI
    // (user reporting, spec §3.3): any map tap then places the point.
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
                            // Before the first fix, show a sensible city view
                            // instead of the whole-world default camera; the
                            // camera then jumps to the user when the fix lands.
                            if (uiState.userLocation == null) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(DEFAULT_PRE_FIX_CAMERA)
                                    .zoom(INITIAL_ZOOM)
                                    .build()
                            }
                        }
                        // Tap on empty map closes the info card; the card is
                        // only shown when a POI/ghost is actually hit. While
                        // picking a POI location, ANY tap places the point
                        // and consumes the gesture (no card opens).
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

        // Camera follows the user only on significant movement or explicit recenter.
        LaunchedEffect(uiState.userLocation, recenterTick, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val mapView = mapViewRef.value ?: return@LaunchedEffect
            val location = uiState.userLocation ?: return@LaunchedEffect
            val target = LatLng(location.latitude, location.longitude)
            val previous = lastCamera.value
            val forceRecenter = recenterTick > 0
            val movedSignificantly = previous == null || GeoUtils.haversineMeters(
                previous.latitude, previous.longitude, target.latitude, target.longitude
            ) >= CAMERA_MOVE_THRESHOLD_METERS
            if (forceRecenter || movedSignificantly) {
                lastCamera.value = target
                mapView.getMapAsync { map ->
                    val zoom = if (previous == null) FOLLOW_ZOOM else map.cameraPosition?.zoom ?: FOLLOW_ZOOM
                    map.cameraPosition = CameraPosition.Builder()
                        .target(target)
                        .zoom(zoom)
                        .build()
                }
            }
        }

        // Block the map with a full-screen notice until location can be used.
        LocationBlockScreen(
            locationPermissionGranted = locationPermissionGranted,
            gpsEnabled = gpsEnabled
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
                    // Add POI (spec §3.3): starts/cancels the location picking
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
        // the user inside a POI radius (spec §7.3); the shared vote engine
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

        // Stale-travel confirmation (spec §7.1): when the active trip looks
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
 * Large bottom sheet with the details of a POI (geofencing sheet, spec §7.3).
 *
 * How it works: a [ModalBottomSheet] (about twice the size of the old top
 * card) showing the real photo, the name, the category/city, a readable
 * description and the shared up/down votes, in the app's surface style. It
 * opens from a marker tap AND automatically when the user enters the POI
 * geofence; dragging it down or tapping the X dismisses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiDetailSheet(
    poi: PoiEntity,
    myVote: Int,
    canVote: Boolean,
    onVotePoi: (Long, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Real photo of the place: the bundled drawable for the demo POIs,
            // the user's gallery photo for the ones they added.
            PoiImage(
                imageResName = poi.imageResName,
                imageUri = poi.imageUri,
                contentDescription = poi.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = poi.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitle = listOfNotNull(poi.category, poi.city).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.map_close_info)
                        )
                    }
                }
                if (!poi.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (canVote) {
                    // You are inside the POI geofence: the shared vote engine
                    // is enabled (same rules as the journal).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.map_poi_votes_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        PoiVoteButtons(
                            poiId = poi.id,
                            score = poi.votes,
                            myVote = myVote,
                            onVotePoi = onVotePoi
                        )
                    }
                } else {
                    // Tapped from far away: the score is shown READ-ONLY (like
                    // the trends page and the ghost cards) — you can only vote
                    // a place you are actually close to.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.map_poi_votes_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        GhostVote(score = poi.votes, myVote = myVote)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.map_poi_vote_nearby_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Full-screen overlay of the map section with the LIVE trip info.
 *
 * How it works: while the active travel is ongoing a [LaunchedEffect] ticks
 * every second, so the timer updates in real time; the steps and the distance
 * come from the shared pedometer tables (live flows from [MapViewModel]). With
 * no active travel the timer is paused at 00:00 and a hint is shown.
 */
@Composable
private fun TripInfoOverlay(
    activeTravel: TravelEntity?,
    steps: Long,
    distanceMeters: Double,
    onDismiss: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val ongoing = activeTravel != null
    LaunchedEffect(ongoing) {
        if (ongoing) {
            while (true) {
                delay(1_000L)
                now = System.currentTimeMillis()
            }
        }
    }

    val start = activeTravel?.startUtcMillis
    val durationMillis = when {
        start == null -> 0L
        activeTravel?.endUtcMillis != null -> activeTravel.endUtcMillis - start
        else -> now - start
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.map_trip_info_back)
                    )
                }
                Text(
                    text = stringResource(R.string.map_trip_info_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.map_trip_info_timer_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTripDuration(durationMillis),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(40.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    TripInfoStat(
                        label = stringResource(R.string.stat_steps),
                        value = formatTripCount(steps),
                        modifier = Modifier.weight(1f)
                    )
                    TripInfoStat(
                        label = stringResource(R.string.stat_distance),
                        value = formatTripKm(distanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (activeTravel == null) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.map_trip_info_no_travel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** One live value + label column of the trip-info screen. */
@Composable
private fun TripInfoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Formats a live/closed trip duration as "Xh Ym Zs". */
private fun formatTripDuration(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** Formats a step count with thousands separators. */
private fun formatTripCount(value: Long): String =
    try {
        java.text.NumberFormat.getIntegerInstance().format(value)
    } catch (_: Exception) {
        value.toString()
    }

/** Formats meters as km (one decimal) or meters below 1 km. */
private fun formatTripKm(meters: Double): String =
    if (meters >= 1_000.0) "%.1f km".format(meters / 1_000.0).replace('.', ',')
    else "${meters.toInt()} m"

/**
 * Journal-like card shown when a ghost marker is tapped.
 *
 * How it works: it reuses the same data model as the travel journal
 * ([JournalMedia], [JournalVisit]) so a ghost reads exactly like a real
 * traveler's journey: name + online/past badge, description, the city of the
 * trip, a media gallery and the POIs they visited with their votes. The media
 * are simulated (no real files), so the gallery shows generated placeholder
 * thumbnails instead of decoded pictures. The card is anchored to the bottom
 * of the map and dismisses with the X.
 */
@Composable
private fun GhostDetailCard(
    ghost: Ghost,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The same color the ghost uses on the map, so the card is
                // instantly associated with its marker.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ghostColor(ghost.marker.userId))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ghost.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Online / past badge.
                val badgeColor = if (ghost.isOnlineNow) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    shape = CircleShape,
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (ghost.isOnlineNow) {
                            stringResource(R.string.map_ghost_online_now)
                        } else {
                            stringResource(R.string.map_ghost_trip, ghost.city)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_close_info)
                    )
                }
            }

            if (ghost.description.isNotBlank()) {
                Text(
                    text = ghost.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            // Media gallery (simulated thumbnails).
            if (ghost.media.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.map_ghost_media_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ghost.media, key = { it.id }) { item ->
                        GhostMediaPlaceholder(item)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Transport segments the traveler reported (examples of the
            // journal transport flow, same model as a real trip).
            if (ghost.transports.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.map_ghost_transports_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                ghost.transports.forEach { transport ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GhostTransportIcon(mode = transport.mode)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = ghostTransportLabel(transport.mode, transport.lineName),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Visited POIs with their votes.
            if (ghost.visits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.map_ghost_visits_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                ghost.visits.forEach { visit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = visit.poiTitle ?: stringResource(R.string.journal_untitled),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(ghostDate(visit.visitedAtUtcMillis), style = MaterialTheme.typography.bodySmall)
                            }
                            // Read-only: shows the ghost's vote + the POI score.
                            GhostVote(score = visit.score, myVote = visit.myVote)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A generated placeholder thumbnail for a ghost's simulated photo/video.
 *
 * How it works: ghost media have no real file, so instead of decoding an URI
 * we render a tinted box with the label and (for videos) a play badge — the
 * same visual language as the journal's media cards.
 */
@Composable
private fun GhostMediaPlaceholder(item: JournalMedia) {
    val base = when (item.mediaType) {
        "video" -> Color(0xFF312E81) // indigo
        else -> Color(0xFF0F766E) // teal
    }
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(base)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (item.mediaType == "video") {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.journal_video_description),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = item.caption ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Label of a transport mode, shared with the journal rows. */
private val TransportMode.ghostLabelRes: Int
    get() = when (this) {
        TransportMode.WALK -> R.string.transport_walk
        TransportMode.BUS -> R.string.transport_bus
        TransportMode.TRAM -> R.string.transport_tram
        TransportMode.METRO -> R.string.transport_metro
        TransportMode.FERRY -> R.string.transport_ferry
    }

/** "Bus · Line 4" label of a ghost's reported transport segment. */
@Composable
private fun ghostTransportLabel(mode: TransportMode, lineName: String?): String {
    val modeLabel = stringResource(mode.ghostLabelRes)
    return if (lineName.isNullOrBlank()) {
        modeLabel
    } else {
        stringResource(R.string.transport_with_line, modeLabel, lineName)
    }
}

/** Placeholder icon of a transport mode (same visual as the journal). */
@Composable
private fun GhostTransportIcon(mode: TransportMode, modifier: Modifier = Modifier) {
    val glyph = when (mode) {
        TransportMode.WALK -> "\uD83D\uDEB6"
        TransportMode.BUS -> "\uD83D\uDE8C"
        TransportMode.TRAM -> "\uD83D\uDE8B"
        TransportMode.METRO -> "\uD83D\uDE87"
        TransportMode.FERRY -> "\u26F4\uFE0F"
    }
    val background = when (mode) {
        TransportMode.WALK -> Color(0xFF16A34A)
        TransportMode.BUS -> Color(0xFF2563EB)
        TransportMode.TRAM -> Color(0xFFDC2626)
        TransportMode.METRO -> Color(0xFF7C3AED)
        TransportMode.FERRY -> Color(0xFF0891B2)
    }
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Up/down votes for a POI a ghost visited, shown read-only.
 *
 * How it works: mirrors the journal VoteButtons but is not clickable — it just
 * shows the cached community score and which way the ghost voted.
 */
@Composable
private fun GhostVote(score: Int, myVote: Int) {
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.ThumbUp,
            contentDescription = stringResource(R.string.journal_vote_up),
            tint = if (myVote == 1) activeTint else inactiveTint
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.ThumbUp,
            contentDescription = stringResource(R.string.journal_vote_down),
            tint = if (myVote == -1) activeTint else inactiveTint,
            modifier = Modifier.rotate(180f)
        )
    }
}

/** Formats a UTC timestamp as a short date, same style as the journal. */
private fun ghostDate(utcMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(utcMillis))

/**
 * Bottom sheet listing the trips to show on the map, one at a time.
 *
 * How it works: a leading "Live" row restores the active travel/live position;
 * every travel gets its own row (title or start date, with the detected
 * cities as subtitle); the selected row is checked. Choosing a row closes the
 * sheet and forwards the id to [MapViewModel], which switches the path.
 * A bottom sheet scales with years of trips: it scrolls instead of flooding
 * the map with chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelFilterSheet(
    travels: List<TravelEntity>,
    travelCities: Map<Long, List<String>>,
    displayedTravelId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.journal_my_travels),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            TravelSheetRow(
                label = stringResource(R.string.map_filter_live),
                selected = displayedTravelId == null,
                onClick = { onSelect(null) }
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(travels, key = { it.id }) { travel ->
                    TravelSheetRow(
                        label = travel.title ?: travelLabel(travel.startUtcMillis),
                        subtitle = travelCities[travel.id]
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", "),
                        selected = displayedTravelId == travel.id,
                        onClick = { onSelect(travel.id) }
                    )
                }
            }
        }
    }
}

/** One selectable row of the travel-filter sheet. */
@Composable
private fun TravelSheetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Short auto label for a travel without a title, e.g. "24 Aug 2026". */
private fun travelLabel(startUtcMillis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(startUtcMillis))

/**
 * Bottom sheet listing the nearby travelers so the user can hide/restore them
 * one by one by name.
 *
 * How it works: every row is a traveler with its map color, name and online
 * badge; tapping the row toggles its visibility on the map (an eye-off icon
 * means "tap to hide", an eye means "tap to restore"). Hidden travelers are
 * dimmed and stay in the list, so they can be brought back; a "Show all"
 * action clears every hide at once. The sheet scales with the number of
 * travelers (it scrolls instead of flooding the map).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyTravelersSheet(
    nearbyGhosts: List<Ghost>,
    hiddenGhostIds: Set<String>,
    onToggleGhost: (String) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.map_nearby_travelers_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (nearbyGhosts.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_nearby_travelers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(nearbyGhosts, key = { it.marker.userId ?: it.name }) { ghost ->
                        val userId = ghost.marker.userId
                        if (userId != null) {
                            NearbyTravelerRow(
                                ghost = ghost,
                                hidden = userId in hiddenGhostIds,
                                onClick = { onToggleGhost(userId) }
                            )
                        }
                    }
                }
            }
            if (hiddenGhostIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowAll)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.map_show_all_ghosts_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One traveler row of the nearby-travelers sheet: tap to hide/restore. */
@Composable
private fun NearbyTravelerRow(
    ghost: Ghost,
    hidden: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Same color the ghost uses on the map, so the name is recognizable.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ghostColor(ghost.marker.userId))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ghost.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (hidden) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = stringResource(
                    if (ghost.isOnlineNow) R.string.map_ghost_online_now else R.string.map_ghost_trip,
                    ghost.city
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(
                if (hidden) R.drawable.ic_show_ghosts else R.drawable.ic_hide_ghosts
            ),
            contentDescription = stringResource(
                if (hidden) R.string.map_show_traveler else R.string.map_hide_traveler,
                ghost.name
            ),
            tint = if (hidden) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Secondary floating action on the map (recenter, trips filter, travelers).
 *
 * How it works: all the map actions share the same neutral container with a
 * primary icon, so they read as one family and never compete with the raised
 * central Map button of the bottom bar (which keeps the primary fill).
 */
@Composable
private fun MapActionFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * One labeled action pill of the expanding map-actions menu: icon + text in a
 * rounded container, same visual family as the trigger FAB, so the three
 * actions (Add POI, My trips, Nearby travelers) read as one group.
 */
@Composable
private fun MapActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Owns the MapLibre style layers and applies state changes with a diff strategy.
 *
 * How it works: each kind of data (user, path, POIs, ghosts) is a GeoJSON
 * source + style layer. On every [update] the sources are refreshed only when
 * the corresponding state changed (cheap signature checks), and the map
 * redraws itself automatically: no marker objects are recreated.
 */
private class MapOverlays(
    private val context: Context,
    private val map: org.maplibre.android.maps.MapLibreMap,
    private val style: Style
) {

    private val userSource = GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyArray()))
    private val pathSource = GeoJsonSource(SOURCE_PATH, FeatureCollection.fromFeatures(emptyArray()))
    private val poiSource = GeoJsonSource(SOURCE_POIS, FeatureCollection.fromFeatures(emptyArray()))
    private val ghostSource = GeoJsonSource(SOURCE_GHOSTS, FeatureCollection.fromFeatures(emptyArray()))
    private val ghostPathSource = GeoJsonSource(SOURCE_GHOST_PATHS, FeatureCollection.fromFeatures(emptyArray()))

    private var initialized = false
    private var lastPathSignature = Int.MIN_VALUE
    private var lastPoiSignature = Int.MIN_VALUE
    private var lastGhostSignature = Int.MIN_VALUE
    private var lastGhostPathSignature = Int.MIN_VALUE
    private var lastUserLat = Double.NaN
    private var lastUserLon = Double.NaN

    init {
        // Icons are loaded once and reused by every SymbolLayer.
        style.addImage(IMAGE_USER, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_user_location, null)!!)
        style.addImage(IMAGE_POI, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_poi, null)!!)
        // One tinted ghost icon per palette color, so each traveler gets a
        // distinct, stable color on the map.
        GHOST_PALETTE_ARGB.forEachIndexed { index, color ->
            style.addImage(GHOST_ICON_PREFIX + index, tintedGhostIcon(context, color))
        }

        style.addSource(userSource)
        style.addSource(pathSource)
        style.addSource(poiSource)
        style.addSource(ghostSource)
        style.addSource(ghostPathSource)

        style.addLayer(
            LineLayer(LAYER_PATH, SOURCE_PATH).withProperties(
                PropertyFactory.lineColor(PATH_COLOR_INT),
                PropertyFactory.lineWidth(PATH_WIDTH_DP),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        // Ghost routes: one polyline per ghost, drawn below the markers so the
        // markers stay clickable on top. Each line takes its color from the
        // ghost feature (data-driven), so every traveler has its own color.
        style.addLayer(
            LineLayer(LAYER_GHOST_PATHS, SOURCE_GHOST_PATHS).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(GHOST_PATH_WIDTH_DP),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(1f, 2f))
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_USER, SOURCE_USER).withProperties(
                PropertyFactory.iconImage(IMAGE_USER),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_POIS, SOURCE_POIS).withProperties(
                PropertyFactory.iconImage(IMAGE_POI),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false)
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_GHOSTS, SOURCE_GHOSTS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
        initialized = true
    }

    /** Synchronizes the style layers with the given state. */
    fun update(state: MapUiState) {
        if (!initialized) return
        updateUser(state.userLocation)
        updatePath(state.pathPoints)
        updatePois(state.pois)
        updateGhosts(state.ghosts)
    }

    private fun updateUser(location: LocationSample?) {
        if (location == null) {
            // No fix yet (or fix lost): drop any stale marker instead of
            // leaving a ghost dot on the map.
            if (!lastUserLat.isNaN()) {
                lastUserLat = Double.NaN
                lastUserLon = Double.NaN
                userSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            }
            return
        }
        if (location.latitude == lastUserLat && location.longitude == lastUserLon) return
        lastUserLat = location.latitude
        lastUserLon = location.longitude
        userSource.setGeoJson(
            FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)))
            )
        )
    }

    /**
     * Draws the travel path as one polyline per continuous segment.
     *
     * How it works: [PathSegmenter.split] breaks the point list where the
     * tracking was interrupted (app killed, GPS off), so the map never draws
     * a false straight line across the distance covered while untracked.
     */
    private fun updatePath(points: List<TravelPathPointEntity>) {
        val signature = points.hashCode()
        if (signature == lastPathSignature) return
        lastPathSignature = signature
        val segments = PathSegmenter.split(points)
        if (segments.isNotEmpty()) {
            val features = segments.map { segment ->
                val line = LineString.fromLngLats(
                    segment.map { Point.fromLngLat(it.longitude, it.latitude) }
                )
                Feature.fromGeometry(line)
            }
            pathSource.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            pathSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    }

    private fun updatePois(pois: List<PoiEntity>) {
        val signature = pois.hashCode()
        if (signature == lastPoiSignature) return
        lastPoiSignature = signature
        poiById.clear()
        val features = pois.map { poi ->
            poiById[poi.id] = poi
            Feature.fromGeometry(
                Point.fromLngLat(poi.lon, poi.lat),
                JsonObject().apply {
                    addProperty("id", poi.id)
                    addProperty("title", poi.title)
                }
            )
        }.toTypedArray()
        poiSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun updateGhostPaths(ghosts: List<Ghost>) {
        val signature = ghosts.hashCode()
        if (signature == lastGhostPathSignature) return
        lastGhostPathSignature = signature
        val features = ghosts.mapNotNull { ghost ->
            if (ghost.path.size < 2) return@mapNotNull null
            val line = LineString.fromLngLats(
                ghost.path.map { Point.fromLngLat(it.second, it.first) }
            )
            Feature.fromGeometry(
                line,
                JsonObject().apply {
                    // The polyline is painted with the ghost's own color, and
                    // carries the user id so tapping the path opens the card.
                    addProperty("color", ghostColorHex(ghost.marker.userId))
                    addProperty("userId", ghost.marker.userId)
                }
            )
        }.toTypedArray()
        ghostPathSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun updateGhosts(ghosts: List<Ghost>) {
        val signature = ghosts.hashCode()
        if (signature == lastGhostSignature) return
        lastGhostSignature = signature
        ghostByUserId.clear()
        val features = ghosts.map { ghost ->
            ghost.marker.userId?.let { ghostByUserId[it] = ghost }
            Feature.fromGeometry(
                Point.fromLngLat(ghost.marker.lon, ghost.marker.lat),
                JsonObject().apply {
                    addProperty("userId", ghost.marker.userId)
                    // Which tinted icon to use, from the ghost's stable color.
                    addProperty("icon", GHOST_ICON_PREFIX + ghostColorIndex(ghost.marker.userId))
                }
            )
        }.toTypedArray()
        ghostSource.setGeoJson(FeatureCollection.fromFeatures(features))
        updateGhostPaths(ghosts)
    }

    /** A tinted copy of the ghost icon, one per palette color, for the markers. */
    private fun tintedGhostIcon(context: Context, color: Int): Bitmap {
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_ghost, null)!!
            .mutate()
        DrawableCompat.setTint(drawable, color)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    /** Returns the POI whose marker is at the given screen point, if any. */
    fun poiAt(latLng: LatLng): PoiEntity? {
        val point = map.projection.toScreenLocation(latLng)
        val features = map.queryRenderedFeatures(point, LAYER_POIS)
        val id = features.firstOrNull()?.getNumberProperty("id")?.toLong()
        return poiById[id]
    }

    /** Returns the ghost whose marker is at the given screen point, if any. */
    fun ghostAt(latLng: LatLng): Ghost? {
        val point = map.projection.toScreenLocation(latLng)
        val features = map.queryRenderedFeatures(point, LAYER_GHOSTS)
        val userId = features.firstOrNull()?.getStringProperty("userId")
        return userId?.let { ghostByUserId[it] }
    }

    /**
     * Returns the ghost whose route polyline is at (or near) the given screen
     * point, so tapping a ghost's path opens the same card as tapping its
     * marker.
     *
     * Why the tolerance rect: the polylines are thin and dashed, so a query
     * on the exact tap pixel is hard to hit on a touchscreen. Querying a small
     * rectangle AROUND the tap (a few tens of dp) makes the path comfortably
     * tappable while still requiring a deliberate tap near the route.
     */
    fun ghostAtPath(latLng: LatLng): Ghost? {
        val point = map.projection.toScreenLocation(latLng)
        val density = context.resources.displayMetrics.density
        val radius = GHOST_PATH_TAP_RADIUS_DP * density
        val rect = RectF(
            point.x - radius,
            point.y - radius,
            point.x + radius,
            point.y + radius
        )
        val features = map.queryRenderedFeatures(rect, LAYER_GHOST_PATHS)
        val userId = features.firstOrNull()?.getStringProperty("userId")
        return userId?.let { ghostByUserId[it] }
    }

    private val poiById = HashMap<Long, PoiEntity>()
    private val ghostByUserId = HashMap<String, Ghost>()

    companion object {
        const val SOURCE_USER = "source-user"
        const val SOURCE_PATH = "source-path"
        const val SOURCE_POIS = "source-pois"
        const val SOURCE_GHOSTS = "source-ghosts"
        const val SOURCE_GHOST_PATHS = "source-ghost-paths"
        const val LAYER_USER = "layer-user"
        const val LAYER_PATH = "layer-path"
        const val LAYER_POIS = "layer-pois"
        const val LAYER_GHOSTS = "layer-ghosts"
        const val LAYER_GHOST_PATHS = "layer-ghost-paths"
        const val IMAGE_USER = "image-user"
        const val IMAGE_POI = "image-poi"
        const val GHOST_ICON_PREFIX = "ghost-icon-"
        const val PATH_COLOR_INT = 0xFF0F766E.toInt()
        const val PATH_WIDTH_DP = 4f
        const val GHOST_PATH_WIDTH_DP = 4f

        /** Half-side (in dp) of the tap tolerance box around a ghost route. */
        const val GHOST_PATH_TAP_RADIUS_DP = 30f
    }
}

/**
 * Distinct, map-readable colors assigned to the simulated travelers. With many
 * ghosts (20 in Turin, 5 in Cerignola) a fixed hand-picked palette would
 * collide, so the colors are generated with a golden-angle hue rotation:
 * every ghost gets a visually distinct, stable color (the assignment is
 * derived from the stable user id, see [ghostColorIndex]). Pure presentation:
 * the domain model carries no colors.
 */
private const val GHOST_PALETTE_SIZE = 64

private val GHOST_PALETTE_ARGB: IntArray = IntArray(GHOST_PALETTE_SIZE) { index ->
    val hue = (index * 137.508f) % 360f
    android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.92f))
}

/** Stable palette index for a ghost, derived from its stable user id. */
private fun ghostColorIndex(userId: String?): Int =
    ((userId?.hashCode() ?: 0) and Int.MAX_VALUE) % GHOST_PALETTE_ARGB.size

/** ARGB color of a ghost (used for the tinted map icon and the path line). */
private fun ghostColorArgb(userId: String?): Int = GHOST_PALETTE_ARGB[ghostColorIndex(userId)]

/** Hex color of a ghost, as a "#RRGGBB" string for the GeoJSON features. */
private fun ghostColorHex(userId: String?): String =
    String.format(Locale.US, "#%06X", ghostColorArgb(userId) and 0xFFFFFF)

/** Compose color of a ghost (used in the detail card). */
private fun ghostColor(userId: String?): Color = Color(ghostColorArgb(userId))

private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0
private const val INITIAL_ZOOM = 15.0
private const val FOLLOW_ZOOM = 16.0
private const val CAMERA_MOVE_THRESHOLD_METERS = 25.0

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
 */
@Composable
private fun LocationBlockScreen(
    locationPermissionGranted: Boolean,
    gpsEnabled: Boolean
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Recomposition re-checks the state; nothing else needed. */ }

    val message: Int
    val actionLabel: Int
    val onAction: () -> Unit

    when {
        !locationPermissionGranted -> {
            message = R.string.map_warning_permission
            actionLabel = R.string.map_warning_grant
            onAction = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
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
