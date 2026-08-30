package com.example.progmob_progetto_melluso.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The photo of a point of interest.
 *
 * How it works — two sources, no network at runtime:
 * - **Bundled photos** ([imageResName]): the demo POIs ship their real photo
 *   inside the APK (`res/drawable/poi_*.jpg`), so it always loads, offline
 *   included. The name is resolved to a drawable id with [getIdentifier].
 * - **User photos** ([imageUri]): the gallery photo the user attached when
 *   adding a POI, copied into the app's private storage; the bitmap is decoded
 *   off the main thread with [BitmapFactory], same pattern as the journal.
 *
 * A themed placeholder (a place icon over the surface color) is drawn BEHIND
 * the image, so while decoding — or when a POI has no photo at all (demo
 * entries without an image, a broken reference) — the UI still shows a clean,
 * recognizable tile instead of an empty hole.
 *
 * @param imageResName name of the bundled drawable, or null.
 * @param imageUri local file/content URI of the user's photo, or null.
 * @param contentDescription accessibility description of the photo.
 */
@Composable
fun PoiImage(
    imageResName: String?,
    imageUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    // The user's photo is decoded off the main thread (keyed on the URI, so
    // the decode runs once per image and not on every recomposition).
    val bitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        val uri = imageUri
        if (uri != null) {
            value = withContext(Dispatchers.IO) { decodeUri(context, uri) }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val decoded = bitmap
        when {
            decoded != null -> {
                // User photo: the decoded bitmap fills the tile.
                Image(
                    bitmap = decoded.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !imageResName.isNullOrBlank() -> {
                // Bundled demo photo: resolved once per name and drawn over
                // the placeholder.
                val resId = context.resources.getIdentifier(
                    imageResName, "drawable", context.packageName
                )
                if (resId != 0) {
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Decodes a photo from a file or content URI, returning null on failure. */
private fun decodeUri(context: android.content.Context, uri: String): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val parsed = android.net.Uri.parse(uri)
        if (parsed.scheme == "file") {
            // File URI (the journal-style copy in the app storage): decode the
            // path directly, downsampled to a thumbnail size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(parsed.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4
                inJustDecodeBounds = false
            }
            BitmapFactory.decodeFile(parsed.path, options)
        } else {
            // Content URI (legacy rows or directly granted photos).
            resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, null)
            }
        }
    } catch (_: Exception) {
        null
    }
}
