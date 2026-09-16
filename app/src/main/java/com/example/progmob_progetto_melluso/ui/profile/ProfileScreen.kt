package com.example.progmob_progetto_melluso.ui.profile

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.stats.ProfileStatistics
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile screen: identity, privacy preferences and overall statistics.
 *
 * How it works: builds its own [ProfileViewModel] through the manual DI
 * container, renders the DataStore preferences (anonymous mode, path
 * visibility) with switches, the user's avatar (picked from the gallery and
 * copied to the app storage so it persists), and a statistics grid aggregated
 * reactively from the travel data. The identity (name/email) and the sign-out
 * come from the auth state, so they are passed in as parameters.
 */
@Composable
fun ProfileScreen(
    displayName: String?,
    email: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(
            preferencesRepository = AppContainer.userPreferencesRepository(context),
            statsRepository = AppContainer.statsRepository(context)
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            // Extra bottom room so the version line can scroll clear of the
            // raised central Map button of the bottom bar.
            .padding(bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileHeader(
            displayName = displayName,
            email = email,
            avatarUri = uiState.avatarUri,
            onAvatarPicked = viewModel::updateAvatar,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        PrivacySection(
            anonymous = uiState.anonymous,
            showTravelPaths = uiState.showTravelPaths,
            onAnonymousChange = viewModel::setAnonymous,
            onShowTravelPathsChange = viewModel::setShowTravelPaths,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        AppearanceSection(
            themeMode = uiState.themeMode,
            onThemeModeChange = viewModel::setThemeMode,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        // Platform grants, not stored preferences: a refusal during the first
        // run can be reconsidered here, so the first-run pages never become the
        // only chance to enable a feature.
        PermissionsSection(modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        StatisticsSection(stats = uiState.stats, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(R.string.logout))
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_version_label, installedVersionName()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Avatar (clickable to change it) + display name + email. */
@Composable
private fun ProfileHeader(
    displayName: String?,
    email: String?,
    avatarUri: String?,
    onAvatarPicked: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // System photo picker: the picked photo is copied to the app storage right
    // away, because the picker's grant expires when the activity dies.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localUri = withContext(Dispatchers.IO) {
                    copyPickedAvatarToAppStorage(context, uri)
                }
                if (localUri != null) onAvatarPicked(localUri.toString())
            }
        }
    }

    // Opens the gallery chooser; shared by the avatar and the name so both are
    // tappable to change the profile picture.
    val launchPhotoPicker = {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable(onClick = launchPhotoPicker)) {
            ProfileAvatar(avatarUri = avatarUri, size = 92.dp)
            // Small camera badge on the avatar to signal it is editable.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.profile_change_avatar),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName ?: email ?: stringResource(R.string.tab_profile),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.clickable(onClick = launchPhotoPicker)
        )
        if (email != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The round profile picture, or a placeholder when none is set. */
@Composable
private fun ProfileAvatar(avatarUri: String?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, avatarUri) {
        value = avatarUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeImage(context, uri) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.tab_profile),
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Lets the user pick the app theme (System / Light / Dark) with an icon per
 * option. The chosen mode is persisted in DataStore per user and applied app
 * wide from MainActivity. Defaults to the system night/day setting.
 */
@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_appearance_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOption(
                label = stringResource(R.string.theme_system),
                drawable = R.drawable.ic_theme_system,
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = stringResource(R.string.theme_light),
                drawable = R.drawable.ic_theme_light,
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = stringResource(R.string.theme_dark),
                drawable = R.drawable.ic_theme_dark,
                selected = themeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A single tappable theme choice: icon on top, label below, highlighted when selected. */
@Composable
private fun ThemeOption(
    label: String,
    drawable: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(drawable),
                contentDescription = label,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Privacy toggles (the preferences requirement + NFR): anonymous mode and path visibility. */
@Composable
private fun PrivacySection(
    anonymous: Boolean,
    showTravelPaths: Boolean,
    onAnonymousChange: (Boolean) -> Unit,
    onShowTravelPathsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.profile_privacy_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        PreferenceRow(
            title = stringResource(R.string.profile_anonymous_label),
            description = stringResource(R.string.profile_anonymous_desc),
            checked = anonymous,
            onCheckedChange = onAnonymousChange
        )
        PreferenceRow(
            title = stringResource(R.string.profile_show_paths_label),
            description = stringResource(R.string.profile_show_paths_desc),
            checked = showTravelPaths,
            onCheckedChange = onShowTravelPathsChange
        )
    }
}

/** A labeled switch row with a one-line description. */
@Composable
private fun PreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** Grid of the user's overall statistics. */
@Composable
private fun StatisticsSection(stats: ProfileStatistics, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_stats_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.stat_steps), formatLong(stats.totalSteps), Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_days_active), stats.activityDays.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.stat_pois_visited), stats.poisVisited.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_cities_visited), stats.citiesVisited.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.stat_distance), formatDistance(stats.totalDistanceMeters), Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_travels), stats.travelsCount.toString(), Modifier.weight(1f))
        }
    }
}

/** A single small number tile with a label. */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Copies a picked gallery photo into the app's private storage and returns its
 * local file URI, or null on failure (persists across restarts, works offline).
 */
private fun copyPickedAvatarToAppStorage(context: Context, uri: Uri): Uri? {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val ext = when {
            mime?.contains("png") == true -> ".png"
            mime?.contains("webp") == true -> ".webp"
            else -> ".jpg"
        }
        val dir = File(context.filesDir, PROFILE_MEDIA_DIR_NAME).apply { mkdirs() }
        val target = File(dir, "avatar_${System.currentTimeMillis()}$ext")
        val input = resolver.openInputStream(uri) ?: return null
        input.use { stream -> target.outputStream().use { out -> stream.copyTo(out) } }
        Uri.fromFile(target)
    } catch (_: Exception) {
        null
    }
}

/** Decodes a downsampled avatar bitmap from a local file URI, or null. */
@Suppress("DEPRECATION")
private fun decodeImage(context: Context, uri: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(uri.removePrefix("file://"), bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inJustDecodeBounds = false
        }
        BitmapFactory.decodeFile(uri.removePrefix("file://"), opts)
    } catch (_: Exception) {
        null
    }
}

/** Formats a raw step count with the platform grouping (e.g. 12.456). */
private fun formatLong(value: Long): String {
    return try {
        java.text.NumberFormat.getIntegerInstance().format(value)
    } catch (_: Exception) {
        value.toString()
    }
}

/** Formats a meter distance into km (e.g. "1,4 km") or meters under 1 km. */
private fun formatDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        "%.1f km".format(meters / 1000.0).replace('.', ',')
    } else {
        "${meters.toInt()} m"
    }
}

/** Reads the installed version name without relying on BuildConfig. */
@Composable
private fun installedVersionName(): String {
    val context = LocalContext.current
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (exception: PackageManager.NameNotFoundException) {
        ""
    }
}

/** Sub-directory of filesDir where the profile avatar is copied. */
private const val PROFILE_MEDIA_DIR_NAME = "profile_media"