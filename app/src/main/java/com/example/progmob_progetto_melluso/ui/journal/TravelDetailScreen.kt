package com.example.progmob_progetto_melluso.ui.journal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import com.example.progmob_progetto_melluso.ui.common.PoiVoteButtons
import com.example.progmob_progetto_melluso.ui.map.STYLE_URL
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.journal.JournalCity
import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalPathPoint
import com.example.progmob_progetto_melluso.journal.JournalTransport
import com.example.progmob_progetto_melluso.journal.TravelJournal
import com.example.progmob_progetto_melluso.journal.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail screen of one travel in the journal.
 *
 * How it works: the screen only renders [TravelJournal] and emits user intents
 * through the callbacks; it never talks to the storage. It offers: editing the
 * title / "what I saw", a photo/video picker (system picker, URIs stored;
 * photos are decoded locally for display), the list of detected destinations
 * (each renameable) and the visited POIs (removable).
 *
 * Votes are NOT shown here: per the the requirements they live only on POIs.
 *
 * @param uiState the journal state containing the opened [TravelJournal].
 * @param onBack closes the detail and returns to the list.
 * @param onSave saves the edited title/description.
 * @param onAddMedia adds a picked photo/video (mediaType "photo"/"video").
 * @param onRemoveMedia removes a photo/video.
 * @param onAddTransport reports a transport segment (mode + line number).
 * @param onRemoveTransport removes a reported transport segment.
 * @param onRenameCity renames a detected destination.
 * @param onRemoveVisit removes a visited POI.
 * @param onVotePoi votes a POI up (+1) or down (-1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDetailScreen(
    uiState: TravelJournalUiState,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onAddMedia: (String, String) -> Unit,
    onRemoveMedia: (Long) -> Unit,
    onAddTransport: (com.example.progmob_progetto_melluso.journal.TransportMode, String?) -> Unit,
    onRemoveTransport: (Long) -> Unit,
    onRenameCity: (Long, String) -> Unit,
    onRemoveVisit: (Long) -> Unit,
    onVotePoi: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val journal = uiState.detail ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(journal.travel.title ?: stringResource(R.string.journal_untitled))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.journal_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Route preview: a small map of the recorded path.
            if (journal.pathPoints.isNotEmpty()) {
                PathPreviewCard(
                    travelId = journal.travel.travelId,
                    points = journal.pathPoints
                )
                Spacer(Modifier.height(24.dp))
            }

            // Trip stats: pedometer steps, walked distance and TRAVELED
            // duration (live while the trip is still ongoing): the time this
            // trip was tracked, not the raw wall clock from the start.
            TripStatsSection(
                startUtcMillis = journal.travel.startUtcMillis,
                endUtcMillis = journal.travel.endUtcMillis,
                steps = journal.steps,
                distanceMeters = journal.distanceMeters,
                traveledMillis = uiState.detailTraveledMillis
            )

            Spacer(Modifier.height(24.dp))

            // Media section.
            MediaSection(
                media = journal.media,
                onAddMedia = onAddMedia,
                onRemoveMedia = onRemoveMedia
            )

            Spacer(Modifier.height(24.dp))

            // Editable details. Saving shows a snackbar so the user gets
            // immediate feedback that the change was persisted.
            EditableDetails(
                travelId = journal.travel.travelId,
                initialTitle = journal.travel.title,
                initialDescription = journal.travel.description,
                onSave = { title, description ->
                    onSave(title, description)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.journal_saved))
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // Detected destinations (cities), renameable.
            CitiesSection(
                cities = journal.cities,
                onRename = onRenameCity
            )

            Spacer(Modifier.height(24.dp))

            // Transport segments reported during the trip (simple flow).
            TransportsSection(
                transports = journal.transports,
                onAdd = onAddTransport,
                onRemove = onRemoveTransport
            )

            Spacer(Modifier.height(24.dp))

            // Visited POIs, with up/down voting.
            VisitsSection(
                visits = journal.visits,
                onRemoveVisit = onRemoveVisit,
                onVotePoi = onVotePoi
            )
        }
    }
}

/**
 * Pedometer + duration card of a trip (the transport-reporting requirement: "registrare il numero di
 * passi e le distanze percorse durante il viaggio" + the trip length).
 *
 * How it works: the traveled duration arrives from the ViewModel state (live
 * for an ongoing trip, stored blocks for a closed one). Steps and distance come
 * from the shared pedometer tables (0 when the device has no step sensor or
 * the trip predates the feature).
 */
@Composable
private fun TripStatsSection(
    startUtcMillis: Long,
    endUtcMillis: Long?,
    steps: Long,
    distanceMeters: Double,
    traveledMillis: Long,
    modifier: Modifier = Modifier
) {
    // The traveled duration (the traveled-duration requirement) comes from
    // the ViewModel state (the session-block flow upstream), so it covers
    // both open and closed trips and grows live while the trip is ongoing.
    // The screen holds no repository (MVVM separation).
    val durationMillis = traveledMillis

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_trip_stats_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                TripStat(
                    label = stringResource(R.string.journal_stat_duration),
                    value = formatDuration(durationMillis),
                    modifier = Modifier.weight(1f)
                )
                TripStat(
                    label = stringResource(R.string.stat_steps),
                    value = formatCount(steps),
                    modifier = Modifier.weight(1f)
                )
                TripStat(
                    label = stringResource(R.string.stat_distance),
                    value = formatKilometers(distanceMeters),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** One value + label column of the trip stats card. */
@Composable
private fun TripStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Formats a duration as "Xh Ym" or "Ym" / "Xs" when shorter. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** Formats a step count with thousands separators. */
private fun formatCount(value: Long): String =
    try {
        java.text.NumberFormat.getIntegerInstance().format(value)
    } catch (_: Exception) {
        value.toString()
    }

/** Formats meters as km (one decimal) or meters below 1 km. */
private fun formatKilometers(meters: Double): String =
    if (meters >= 1_000.0) "%.1f km".format(meters / 1_000.0).replace('.', ',')
    else "${meters.toInt()} m"

/** Photos/videos added to the travel, with add and remove actions. */
@Composable
private fun MediaSection(
    media: List<JournalMedia>,
    onAddMedia: (String, String) -> Unit,
    onRemoveMedia: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // Copy to private storage: the picker's content URI is only
                // readable while this activity is alive, so the journal keeps
                // its own copy (survives restarts, works offline).
                val localUri = withContext(Dispatchers.IO) {
                    copyPickedMediaToAppStorage(context, uri, "photo")
                }
                if (localUri != null) onAddMedia(localUri.toString(), "photo")
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localUri = withContext(Dispatchers.IO) {
                    copyPickedMediaToAppStorage(context, uri, "video")
                }
                if (localUri != null) onAddMedia(localUri.toString(), "video")
            }
        }
    }

    // In-app camera capture: the destination already lives inside the app's
    // private media directory (exposed by the FileProvider), so on success the
    // file is immediately the final copy and needs no extra copy step.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) onAddMedia(uri.toString(), "photo")
    }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    val recordVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = pendingVideoUri
        pendingVideoUri = null
        if (success && uri != null) onAddMedia(uri.toString(), "video")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_media_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        if (media.isEmpty()) {
            Text(
                text = stringResource(R.string.journal_no_media),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(media, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        context = context,
                        onRemove = { onRemoveMedia(item.id) }
                    )
                }
            }
        }
        // Media actions, centered, on two rows: add-from-gallery + take/capture
        // on the first, add-picked-video + record on the second, so the buttons
        // never overflow the screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text(stringResource(R.string.journal_add_photo))
                }
                OutlinedButton(
                    onClick = {
                        cameraOutputUri(context, ".jpg")?.let { uri ->
                            pendingPhotoUri = uri
                            takePhotoLauncher.launch(uri)
                        }
                    }
                ) {
                    Text(stringResource(R.string.journal_take_photo))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                ) {
                    Text(stringResource(R.string.journal_add_video))
                }
                OutlinedButton(
                    onClick = {
                        cameraOutputUri(context, ".mp4")?.let { uri ->
                            pendingVideoUri = uri
                            recordVideoLauncher.launch(uri)
                        }
                    }
                ) {
                    Text(stringResource(R.string.journal_record_video))
                }
            }
        }
    }
}

/** A single photo/video card with remove. */
@Composable
private fun MediaCard(
    item: JournalMedia,
    context: Context,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.width(140.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (item.mediaType == "photo") {
                    val thumbnail by rememberThumbnail(context, Uri.parse(item.imageUri))
                    val bitmap = thumbnail
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.journal_photo_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // Video: first frame with a play badge; tapping opens the
                    // system player (no in-app video player).
                    val thumbnail by rememberVideoThumbnail(context, Uri.parse(item.imageUri))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable { openVideoExternally(context, item.imageUri) },
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = thumbnail
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.journal_video_description),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Play badge on top of the frame, so it reads as a video.
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.journal_video_description),
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.journal_remove_media))
                }
            }
        }
    }
}

/**
 * Decodes a downsampled bitmap for a photo content Uri, off the main thread.
 *
 * How it works: [produceState] launches the decode on [Dispatchers.IO]; the
 * result is a [Bitmap] or null while loading.
 */
@Composable
private fun rememberThumbnail(context: Context, uri: Uri) =
    produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            decodeThumbnail(context, uri)
        }
    }

/** Downsample + decode a photo from a content Uri, returning null on failure. */
@SuppressLint("DiscouragedApi")
private fun decodeThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        // First pass: get just the bounds to compute the sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        // Second pass: decode a reduced bitmap.
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 8
            inJustDecodeBounds = false
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Exception) {
        null
    }
}

/**
 * Builds a content URI inside the app's private media directory where the
 * system camera app can write the captured photo/video.
 *
 * How it works: the file is created (empty) under `filesDir/media` and handed
 * to the camera via the FileProvider, which exposes exactly that directory
 * (see `res/xml/file_paths.xml`). Because the destination already lives in the
 * final location, the captured file is the app's own copy and no extra copy
 * step is needed.
 *
 * @return a content:// URI exposed to the camera app, or null on failure.
 */
private fun cameraOutputUri(context: Context, extension: String): Uri? {
    return try {
        val dir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "media_${System.currentTimeMillis()}_camera$extension")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Copies a picked photo/video into the app's private storage and returns its
 * local file URI, or null on failure.
 *
 * Why: the system photo picker returns content URIs with a TEMPORARY read
 * grant tied to the calling activity — after the app restarts those URIs are
 * no longer readable and the journal would show endless spinners. Owning a
 * private copy makes the media persist across restarts and work offline.
 */
private fun copyPickedMediaToAppStorage(context: Context, uri: Uri, mediaType: String): Uri? {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val ext = when {
            mime?.contains("video") == true -> ".mp4"
            mime?.contains("png") == true -> ".png"
            mime?.contains("gif") == true -> ".gif"
            mime?.contains("webp") == true -> ".webp"
            else -> ".jpg"
        }
        val dir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val target = File(dir, "media_${System.currentTimeMillis()}$ext")
        val input = resolver.openInputStream(uri) ?: return null
        input.use { stream ->
            target.outputStream().use { out -> stream.copyTo(out) }
        }
        Uri.fromFile(target)
    } catch (_: Exception) {
        null
    }
}

/**
 * Extracts the first frame of a video as a thumbnail, off the main thread.
 *
 * How it works: [MediaMetadataRetriever] reads the file copied into the app
 * storage; the result is a [Bitmap] or null while loading / on failure.
 */
@Composable
private fun rememberVideoThumbnail(context: Context, uri: Uri) =
    produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            extractVideoFrame(context, uri)
        }
    }

/** First frame of a video as a [Bitmap], or null when it cannot be decoded. */
private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) retriever.setDataSource(path) else retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(0)
        retriever.release()
        frame
    } catch (_: Exception) {
        null
    }
}

/**
 * Opens a video with the system player (ACTION_VIEW).
 *
 * How it works: the copied file lives in the app's private storage, so it is
 * exposed through the [FileProvider] as a content:// URI with a temporary
 * read grant (file:// URIs are blocked across apps since Android 7). The
 * chooser lets the user pick which installed player to use; a toast appears
 * only when literally no video app is installed.
 */
private fun openVideoExternally(context: Context, imageUri: String) {
    try {
        val uri = if (imageUri.startsWith("file://")) {
            val file = File(Uri.parse(imageUri).path ?: return)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            // Legacy content URI (e.g. picked before the copy-to-storage fix).
            Uri.parse(imageUri)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.journal_open_video_with))
        )
    } catch (_: Exception) {
        Toast.makeText(context, R.string.journal_no_video_player, Toast.LENGTH_SHORT).show()
    }
}

/** Inline editable title / description, saved with a button. */
@Composable
private fun EditableDetails(
    travelId: Long,
    initialTitle: String?,
    initialDescription: String?,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // rememberSaveable so a rotation does not wipe the fields being edited.
    var title by rememberSaveable(travelId) { mutableStateOf(initialTitle ?: "") }
    var description by rememberSaveable(travelId) { mutableStateOf(initialDescription ?: "") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_title_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.journal_description_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSave(title, description) }) {
            Text(stringResource(R.string.journal_save))
        }
    }
}

/** Detected destinations (cities) of the travel, each renameable. */
@Composable
private fun CitiesSection(
    cities: List<JournalCity>,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_cities_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        if (cities.isEmpty()) {
            Text(
                text = stringResource(R.string.journal_no_cities),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            cities.forEach { city ->
                CityRow(city = city, onRename = onRename)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** One detected destination row with an inline rename. */
@Composable
private fun CityRow(
    city: JournalCity,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable(city.cityId) { mutableStateOf(false) }
    var name by rememberSaveable(city.cityId) { mutableStateOf(city.name) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = {
                    onRename(city.cityId, name)
                    editing = false
                }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.journal_save))
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = city.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatDate(city.arrivedAtUtcMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.journal_title_label))
                }
            }
        }
    }
}

/** Visited POIs of the travel, removable and votable (one vote per user). */
@Composable
private fun VisitsSection(
    visits: List<com.example.progmob_progetto_melluso.journal.JournalVisit>,
    onRemoveVisit: (Long) -> Unit,
    onVotePoi: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_visits_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        if (visits.isEmpty()) {
            Text(
                text = stringResource(R.string.journal_no_visits),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            visits.forEach { visit ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
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
                            Text(formatDate(visit.visitedAtUtcMillis), style = MaterialTheme.typography.bodySmall)
                        }
                        PoiVoteButtons(
                            poiId = visit.poiId,
                            score = visit.score,
                            myVote = visit.myVote,
                            onVotePoi = onVotePoi
                        )
                        TextButton(onClick = { onRemoveVisit(visit.id) }) {
                            Text(stringResource(R.string.journal_remove_visit))
                        }
                    }
                }
            }
        }
    }
}

/** Label of a transport mode, shown in the journal rows and the picker. */
private val TransportMode.labelRes: Int
    get() = when (this) {
        TransportMode.WALK -> R.string.transport_walk
        TransportMode.BUS -> R.string.transport_bus
        TransportMode.TRAM -> R.string.transport_tram
        TransportMode.METRO -> R.string.transport_metro
        TransportMode.TRAIN -> R.string.transport_train
        TransportMode.FERRY -> R.string.transport_ferry
    }

/**
 * Transport segments reported during the trip (the transport-reporting requirement, simple flow).
 *
 * How it works: each row shows a placeholder icon + the mode (with the line
 * number when the user reported one) and can be removed; the "Add" button
 * opens the small picker dialog.
 */
@Composable
private fun TransportsSection(
    transports: List<JournalTransport>,
    onAdd: (TransportMode, String?) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_transports_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        if (transports.isEmpty()) {
            Text(
                text = stringResource(R.string.journal_no_transports),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            transports.forEach { transport ->
                TransportRow(transport = transport, onRemove = { onRemove(transport.id) })
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.journal_add_transport))
            }
        }
    }

    if (showDialog) {
        AddTransportDialog(
            onConfirm = { mode, line ->
                onAdd(mode, line)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** One reported transport segment row, with remove. */
@Composable
private fun TransportRow(
    transport: JournalTransport,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportModeIcon(mode = transport.mode)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transportLabel(transport.mode, transport.lineName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatDate(transport.startUtcMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.journal_remove_transport)
                )
            }
        }
    }
}

/** Placeholder icon of a transport mode: a tinted circle with a glyph. */
@Composable
private fun TransportModeIcon(mode: TransportMode, modifier: Modifier = Modifier) {
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
            .size(36.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, style = MaterialTheme.typography.titleMedium)
    }
}

/** "Bus · Line 4" (or just the mode label when no line was reported). */
@Composable
private fun transportLabel(mode: TransportMode, lineName: String?): String {
    val modeLabel = stringResource(mode.labelRes)
    return if (lineName.isNullOrBlank()) {
        modeLabel
    } else {
        stringResource(R.string.transport_with_line, modeLabel, lineName)
    }
}

/** Simple dialog to report a transport: pick the mode, optionally the line. */
@Composable
private fun AddTransportDialog(
    onConfirm: (TransportMode, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(TransportMode.BUS) }
    var line by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_add_transport)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.transport_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TransportMode.entries.forEach { mode ->
                    Surface(
                        onClick = { selected = mode },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected == mode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TransportModeIcon(mode)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(mode.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected == mode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                // Only the vehicles with a line ask for the number; walking
                // has none (keeps the flow "simple").
                if (selected != TransportMode.WALK) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = line,
                        onValueChange = { line = it },
                        label = { Text(stringResource(R.string.transport_line_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, line.ifBlank { null }) }) {
                Text(stringResource(R.string.journal_add_transport_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.journal_delete_cancel))
            }
        }
    )
}

/**
 * Small map of the travel's recorded path.
 *
 * How it works: the map image is rendered OFF-SCREEN with MapLibre's
 * [MapSnapshotter] (no MapView, no on-screen GL context) using the same
 * OpenFreeMap style as the map tab; the polyline is projected into the
 * snapshot pixels with [MapSnapshot.pixelForLatLng], so the route aligns
 * exactly with the rendered map. While the snapshot loads (or when offline,
 * if the style/tiles fail) a plain polyline fallback is drawn instead.
 */
@Composable
private fun PathPreviewCard(
    travelId: Long,
    points: List<JournalPathPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.background

    // Null until the off-screen snapshot is ready; the fallback polyline is
    // shown meanwhile. Keyed on the travel id: a finished trip never changes.
    var snapshot by remember(travelId) { mutableStateOf<MapSnapshot?>(null) }
    var overlayPixels by remember(travelId) { mutableStateOf<List<Offset>?>(null) }

    DisposableEffect(travelId) {
        // Ensure the native MapLibre runtime is initialized for this process
        // (idempotent; the map tab normally does it at startup).
        MapLibre.getInstance(context)
        val snapshotter = MapSnapshotter(
            context,
            MapSnapshotter.Options(SNAP_WIDTH, SNAP_HEIGHT)
                .withStyle(STYLE_URL)
                .withRegion(paddedBounds(points))
                .withLogo(false)
        )
        snapshotter.start(
            object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(result: MapSnapshot) {
                    snapshot = result
                    overlayPixels = points.map { point ->
                        val px = result.pixelForLatLng(LatLng(point.lat, point.lon))
                        Offset(px.x, px.y)
                    }
                    snapshotter.cancel()
                }
            },
            object : MapSnapshotter.ErrorHandler {
                override fun onError(message: String) {
                    // Style/tile failure (e.g. offline): keep the fallback.
                    snapshotter.cancel()
                }
            }
        )
        onDispose { snapshotter.cancel() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_route_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(10.dp)
            ) {
                val mapImage = snapshot?.bitmap
                val overlay = overlayPixels
                if (mapImage != null && overlay != null) {
                    // Fit the snapshot bitmap into the canvas preserving the
                    // aspect ratio, and draw the path in the same fitted frame.
                    val scale = minOf(size.width / mapImage.width, size.height / mapImage.height)
                    val dstW = mapImage.width * scale
                    val dstH = mapImage.height * scale
                    val left = (size.width - dstW) / 2f
                    val top = (size.height - dstH) / 2f
                    drawImage(
                        image = mapImage.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(mapImage.width, mapImage.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
                    )

                    val path = Path()
                    overlay.forEachIndexed { index, point ->
                        val x = left + point.x * scale
                        val y = top + point.y * scale
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Start (sand) and end (teal) markers, outlined with the
                    // surface color so they read on top of the map.
                    val first = Offset(left + overlay.first().x * scale, top + overlay.first().y * scale)
                    val last = Offset(left + overlay.last().x * scale, top + overlay.last().y * scale)
                    drawCircle(color = outline, radius = 7.dp.toPx(), center = first)
                    drawCircle(color = startColor, radius = 5.dp.toPx(), center = first)
                    drawCircle(color = outline, radius = 7.dp.toPx(), center = last)
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = last)
                } else {
                    // Fallback while loading or offline: plain polyline from
                    // the raw coordinates, normalized preserving the aspect.
                    val minLat = points.minOf { it.lat }
                    val maxLat = points.maxOf { it.lat }
                    val minLon = points.minOf { it.lon }
                    val maxLon = points.maxOf { it.lon }
                    val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
                    val spanLon = (maxLon - minLon).coerceAtLeast(1e-6)

                    val pad = 8.dp.toPx()
                    val availW = (size.width - 2 * pad).coerceAtLeast(1f)
                    val availH = (size.height - 2 * pad).coerceAtLeast(1f)
                    val fallbackScale = minOf(availW / spanLon, availH / spanLat)
                    val offsetX = (size.width - spanLon * fallbackScale) / 2f
                    val offsetY = (size.height - spanLat * fallbackScale) / 2f

                    fun x(lon: Double): Float = (offsetX + (lon - minLon) * fallbackScale).toFloat()
                    fun y(lat: Double): Float = (offsetY + (maxLat - lat) * fallbackScale).toFloat()

                    val path = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(x(point.lon), y(point.lat))
                        else path.lineTo(x(point.lon), y(point.lat))
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    val first = Offset(x(points.first().lon), y(points.first().lat))
                    val last = Offset(x(points.last().lon), y(points.last().lat))
                    drawCircle(color = outline, radius = 7.dp.toPx(), center = first)
                    drawCircle(color = startColor, radius = 5.dp.toPx(), center = first)
                    drawCircle(color = outline, radius = 7.dp.toPx(), center = last)
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = last)
                }
            }
        }
    }
}

/**
 * Bounding box of the path with a margin, so the route is never clipped at
 * the snapshot edges. A minimum span keeps even a single point framed.
 */
private fun paddedBounds(points: List<JournalPathPoint>): LatLngBounds {
    val latMin = points.minOf { it.lat }
    val latMax = points.maxOf { it.lat }
    val lonMin = points.minOf { it.lon }
    val lonMax = points.maxOf { it.lon }
    val minSpan = 0.01
    val spanLat = maxOf(latMax - latMin, minSpan)
    val spanLon = maxOf(lonMax - lonMin, minSpan)
    val padLat = spanLat * 0.25
    val padLon = spanLon * 0.25
    return LatLngBounds.Builder()
        .include(LatLng(latMin - padLat, lonMin - padLon))
        .include(LatLng(latMax + padLat, lonMax + padLon))
        .build()
}

/** Snapshot resolution: enough detail for the route thumbnails, light on memory. */
private const val SNAP_WIDTH = 900
private const val SNAP_HEIGHT = 400

/** Sub-directory of filesDir where picked photos/videos are copied. */
private const val MEDIA_DIR_NAME = "media"

private fun formatDate(utcMillis: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(utcMillis))
}
