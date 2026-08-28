package com.example.progmob_progetto_melluso.ui.poi

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.ui.common.PoiImage

/**
 * Demo dialog to create a point of interest (spec §3.3).
 *
 * How it works: pure UI — it holds the form fields, the gallery photo (picked
 * with the system photo picker, shown as a preview and removable) and hands
 * the built [PoiEntity] to [onConfirm]; the ViewModel stores it and the
 * reactive POI flow makes the new marker/trend appear everywhere. No
 * validation is applied on purpose (demo request): every field is stored
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
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf(initialLat.toString()) }
    var lon by remember { mutableStateOf(initialLon.toString()) }

    // System photo picker (same mechanism the journal uses): no storage
    // permission needed, the selected photo is granted to the app directly.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
        }
    }

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
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(R.string.add_poi_field_city)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary action, styled like the journal's "Add photo"
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
                            imageUrl = uri,
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
            TextButton(onClick = {
                onConfirm(
                    PoiEntity(
                        lat = lat.toDoubleOrNull() ?: initialLat,
                        lon = lon.toDoubleOrNull() ?: initialLon,
                        title = title,
                        description = description.ifBlank { null },
                        category = category.ifBlank { null },
                        createdByUserId = null,
                        city = city.ifBlank { null },
                        imageUrl = imageUri
                    )
                )
            }) {
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