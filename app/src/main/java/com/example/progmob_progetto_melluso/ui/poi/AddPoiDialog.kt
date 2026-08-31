package com.example.progmob_progetto_melluso.ui.poi

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.ui.common.PoiImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog to create a point of interest (spec §3.3).
 *
 * How it works: pure UI — it holds the form fields and hands the built
 * [PoiEntity] to [onConfirm]; the ViewModel stores it and the reactive POI
 * flow makes the new marker/trend appear everywhere. The photo, the name and
 * the city are REQUIRED (a POI without them is not a usable entry): the
 * confirm button stays disabled until all three are present. The photo is
 * picked with the system gallery picker and immediately COPIED into the app's
 * private storage (the picker's content URI is only readable while the
 * activity lives), so it survives restarts and works offline. No validation
 * beyond the required fields (demo request): every other field is stored
 * exactly as typed. Latitude/longitude come prefilled with the point the user
 * picked ON THE MAP, so the location is always the chosen one.
 */
@Composable
fun AddPoiDialog(
    initialLat: Double,
    initialLon: Double,
    onConfirm: (PoiEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // All fields are rememberSaveable: a rotation (activity recreation) must
    // NOT cancel the form the user is filling in.
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lat by rememberSaveable { mutableStateOf(initialLat.toString()) }
    var lon by rememberSaveable { mutableStateOf(initialLon.toString()) }

    // System photo picker (same mechanism the journal uses): no storage
    // permission needed. The picked photo is copied to the app storage right
    // away, because the picker's grant expires when the activity dies.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localUri = withContext(Dispatchers.IO) {
                    copyPickedImageToAppStorage(context, uri)
                }
                if (localUri != null) {
                    imageUri = localUri.toString()
                }
            }
        }
    }

    // Required fields: a POI must have a name, a city and a photo.
    val formValid = title.isNotBlank() && city.isNotBlank() && imageUri != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_poi_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.add_poi_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(R.string.add_poi_field_city)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.add_poi_field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.add_poi_field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Secondary action, styled like the journal's \"Add photo\"
                    // button (OutlinedButton): the app uses the outlined
                    // variant for picker/media actions and the filled one only
                    // for primary actions.
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_poi_pick_photo),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    // Preview + removal of the chosen gallery photo.
                    imageUri?.let { uri ->
                        PoiImage(
                            imageResName = null,
                            imageUri = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        IconButton(onClick = { imageUri = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.add_poi_remove_photo)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text(stringResource(R.string.add_poi_field_lat)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text(stringResource(R.string.add_poi_field_lon)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(R.string.add_poi_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PoiEntity(
                            lat = lat.toDoubleOrNull() ?: initialLat,
                            lon = lon.toDoubleOrNull() ?: initialLon,
                            title = title.trim(),
                            description = description.ifBlank { null },
                            category = category.ifBlank { null },
                            createdByUserId = null,
                            city = city.trim(),
                            imageResName = null,
                            imageUri = imageUri
                        )
                    )
                },
                enabled = formValid
            ) {
                Text(stringResource(R.string.add_poi_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.add_poi_cancel))
            }
        }
    )
}

/**
 * Copies a picked gallery photo into the app's private storage and returns
 * its local file URI, or null on failure.
 *
 * Why: the system photo picker returns content URIs with a TEMPORARY read
 * grant tied to the calling activity — after the app restarts those URIs are
 * no longer readable and the POI photo would disappear. Owning a private copy
 * makes the photo persist across restarts and work offline (same pattern as
 * the journal media).
 */
private fun copyPickedImageToAppStorage(context: Context, uri: Uri): Uri? {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val ext = when {
            mime?.contains("png") == true -> ".png"
            mime?.contains("gif") == true -> ".gif"
            mime?.contains("webp") == true -> ".webp"
            else -> ".jpg"
        }
        val dir = File(context.filesDir, POI_MEDIA_DIR_NAME).apply { mkdirs() }
        val target = File(dir, "poi_${System.currentTimeMillis()}$ext")
        val input = resolver.openInputStream(uri) ?: return null
        input.use { stream ->
            target.outputStream().use { out -> stream.copyTo(out) }
        }
        Uri.fromFile(target)
    } catch (_: Exception) {
        null
    }
}

/** Sub-directory of filesDir where the POI photos are copied. */
private const val POI_MEDIA_DIR_NAME = "poi_media"
