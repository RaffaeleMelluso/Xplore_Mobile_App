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

internal class MapOverlays(
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
internal fun ghostColor(userId: String?): Color = Color(ghostColorArgb(userId))

