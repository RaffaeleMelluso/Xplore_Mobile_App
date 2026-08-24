package com.example.progmob_progetto_melluso.ui.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.LocationSample
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

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
        factory = remember(context) { MapViewModelFactory(AppContainer.mapRepository(context)) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val overlaysRef = remember { mutableStateOf<MapOverlays?>(null) }
    val lastCamera = remember { mutableStateOf<LatLng?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }

    // Selected marker info window: the tapped POI or ghost, shown as a card.
    var selectedPoi by remember { mutableStateOf<PoiEntity?>(null) }
    var selectedGhost by remember { mutableStateOf<GhostEntity?>(null) }

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
                        // only shown when a POI/ghost is actually hit.
                        map.addOnMapClickListener { latLng ->
                            val poi = overlaysRef.value?.poiAt(latLng)
                            val ghost = overlaysRef.value?.ghostAt(latLng)
                            if (poi != null) {
                                selectedPoi = poi
                                selectedGhost = null
                            } else if (ghost != null) {
                                selectedGhost = ghost
                                selectedPoi = null
                            } else {
                                selectedPoi = null
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

        if (uiState.isLoading && mapReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Info card for the tapped POI or ghost.
        val info = selectedPoi?.let { poi ->
            InfoContent(
                title = poi.title,
                subtitle = listOfNotNull(poi.category, poi.description).joinToString(" · ")
            )
        } ?: selectedGhost?.let { ghost ->
            InfoContent(
                title = stringResource(R.string.map_ghost_title),
                subtitle = stringResource(R.string.map_ghost_snippet)
            )
        }
        if (info != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (info.subtitle.isNotBlank()) {
                            Text(
                                text = info.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { selectedPoi = null; selectedGhost = null }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.map_close_info)
                        )
                    }
                }
            }
        }

        // Recenter button: only useful once the map is actually usable.
        if (mapReady) {
            FloatingActionButton(
                onClick = { recenterTick++ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = stringResource(R.string.map_recenter)
                )
            }
        }
    }
}

/** Simple holder for the info card fields. */
private data class InfoContent(val title: String, val subtitle: String)

/**
 * Owns the MapLibre style layers and applies state changes with a diff strategy.
 *
 * How it works: each kind of data (user, path, POIs, ghosts) is a GeoJSON
 * source + style layer. On every [update] the sources are refreshed only when
 * the corresponding state changed (cheap signature checks), and the map
 * redraws itself automatically: no marker objects are recreated.
 */
private class MapOverlays(
    context: Context,
    private val map: org.maplibre.android.maps.MapLibreMap,
    private val style: Style
) {

    private val userSource = GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyArray()))
    private val pathSource = GeoJsonSource(SOURCE_PATH, FeatureCollection.fromFeatures(emptyArray()))
    private val poiSource = GeoJsonSource(SOURCE_POIS, FeatureCollection.fromFeatures(emptyArray()))
    private val ghostSource = GeoJsonSource(SOURCE_GHOSTS, FeatureCollection.fromFeatures(emptyArray()))

    private var initialized = false
    private var lastPathSignature = Int.MIN_VALUE
    private var lastPoiSignature = Int.MIN_VALUE
    private var lastGhostSignature = Int.MIN_VALUE
    private var lastUserLat = Double.NaN
    private var lastUserLon = Double.NaN

    init {
        // Icons are loaded once and reused by every SymbolLayer.
        style.addImage(IMAGE_USER, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_user_location, null)!!)
        style.addImage(IMAGE_POI, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_poi, null)!!)
        style.addImage(IMAGE_GHOST, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_ghost, null)!!)

        style.addSource(userSource)
        style.addSource(pathSource)
        style.addSource(poiSource)
        style.addSource(ghostSource)

        style.addLayer(
            LineLayer(LAYER_PATH, SOURCE_PATH).withProperties(
                PropertyFactory.lineColor(PATH_COLOR_INT),
                PropertyFactory.lineWidth(PATH_WIDTH_DP),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
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
                PropertyFactory.iconImage(IMAGE_GHOST),
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

    private fun updatePath(points: List<TravelPathPointEntity>) {
        val signature = points.hashCode()
        if (signature == lastPathSignature) return
        lastPathSignature = signature
        if (points.size >= 2) {
            val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
            pathSource.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(line)))
            )
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

    private fun updateGhosts(ghosts: List<GhostEntity>) {
        val signature = ghosts.hashCode()
        if (signature == lastGhostSignature) return
        lastGhostSignature = signature
        ghostByUserId.clear()
        val features = ghosts.map { ghost ->
            ghost.userId?.let { ghostByUserId[it] = ghost }
            Feature.fromGeometry(
                Point.fromLngLat(ghost.lon, ghost.lat),
                JsonObject().apply {
                    addProperty("userId", ghost.userId)
                }
            )
        }.toTypedArray()
        ghostSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Returns the POI whose marker is at the given screen point, if any. */
    fun poiAt(latLng: LatLng): PoiEntity? {
        val point = map.projection.toScreenLocation(latLng)
        val features = map.queryRenderedFeatures(point, LAYER_POIS)
        val id = features.firstOrNull()?.getNumberProperty("id")?.toLong()
        return poiById[id]
    }

    /** Returns the ghost whose marker is at the given screen point, if any. */
    fun ghostAt(latLng: LatLng): GhostEntity? {
        val point = map.projection.toScreenLocation(latLng)
        val features = map.queryRenderedFeatures(point, LAYER_GHOSTS)
        val userId = features.firstOrNull()?.getStringProperty("userId")
        return userId?.let { ghostByUserId[it] }
    }

    private val poiById = HashMap<Long, PoiEntity>()
    private val ghostByUserId = HashMap<String, GhostEntity>()

    companion object {
        const val SOURCE_USER = "source-user"
        const val SOURCE_PATH = "source-path"
        const val SOURCE_POIS = "source-pois"
        const val SOURCE_GHOSTS = "source-ghosts"
        const val LAYER_USER = "layer-user"
        const val LAYER_PATH = "layer-path"
        const val LAYER_POIS = "layer-pois"
        const val LAYER_GHOSTS = "layer-ghosts"
        const val IMAGE_USER = "image-user"
        const val IMAGE_POI = "image-poi"
        const val IMAGE_GHOST = "image-ghost"
        const val PATH_COLOR_INT = 0xFF0F766E.toInt()
        const val PATH_WIDTH_DP = 4f
    }
}

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
