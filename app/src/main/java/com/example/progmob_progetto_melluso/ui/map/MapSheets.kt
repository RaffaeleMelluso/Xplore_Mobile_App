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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiDetailSheet(
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
internal fun TripInfoOverlay(
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
internal fun GhostDetailCard(
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
        TransportMode.TRAIN -> R.string.transport_train
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
        TransportMode.TRAIN -> "\uD83D\uDE86"
        TransportMode.FERRY -> "\u26F4\uFE0F"
    }
    val background = when (mode) {
        TransportMode.WALK -> Color(0xFF16A34A)
        TransportMode.BUS -> Color(0xFF2563EB)
        TransportMode.TRAM -> Color(0xFFDC2626)
        TransportMode.METRO -> Color(0xFF7C3AED)
        TransportMode.TRAIN -> Color(0xFFEA580C)
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
internal fun TravelFilterSheet(
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
internal fun NearbyTravelersSheet(
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
internal fun MapActionFab(
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
internal fun MapActionPill(
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
