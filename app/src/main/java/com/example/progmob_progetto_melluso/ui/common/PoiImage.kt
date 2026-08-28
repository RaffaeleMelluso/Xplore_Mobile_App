package com.example.progmob_progetto_melluso.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * The photo of a point of interest, loaded with Coil from any source the app
 * supports: remote URLs (the demo seeds use Wikimedia Commons) and local
 * content URIs (the gallery photos the user attaches when reporting a POI).
 *
 * How it works: a themed placeholder (a place icon over the surface color)
 * is drawn BEHIND the image, so while the photo loads — or if it fails
 * (offline, broken link, expired media grant) — the UI still shows a clean,
 * recognizable tile instead of an empty hole.
 *
 * @param imageUrl the image reference (URL or content URI), or null to show
 *   only the placeholder.
 * @param contentDescription accessibility description of the photo.
 */
@Composable
fun PoiImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
