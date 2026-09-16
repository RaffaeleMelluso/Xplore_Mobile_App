package com.example.progmob_progetto_melluso.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
// Aliased: the Material icon used below is also called Settings.
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppPermissions
import com.example.progmob_progetto_melluso.core.PermissionGroup
import com.example.progmob_progetto_melluso.core.PermissionStatus

/**
 * Permissions section of the Profile screen.
 *
 * Why it lives in the settings: the first-run pages ask for each permission
 * once, and a refusal there is not the end of it. This section is where the user
 * comes back to, with the current state of every group and a button to ask for
 * what is still missing.
 *
 * How it works: the status is read from the platform through [AppPermissions] —
 * the same vocabulary the onboarding pages and the map use — and re-read
 * whenever the platform state may have changed. Two events matter: resuming the
 * app (a grant made from the system settings, or a revoke made there) and the
 * answer to the permission dialog. The dialog does not pause the activity, so no
 * resume follows it and the launcher callback refreshes the state itself.
 *
 * When a request comes back with nothing granted, Android has usually stopped
 * showing the dialog: the section then offers the only remaining route, the
 * system settings page of the app.
 */
@Composable
fun PermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bumped whenever the platform state may have changed; the status read below
    // runs again only then, instead of on every recomposition.
    var statusRevision by remember { mutableIntStateOf(0) }

    // Set when a request produced no grant at all, which is the signal that the
    // dialog will no longer appear.
    var requestUnavailable by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) statusRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statuses = remember(statusRevision) { AppPermissions.statuses(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        statusRevision++
        if (grants.isNotEmpty() && grants.values.none { granted -> granted }) {
            requestUnavailable = true
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.profile_permissions_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.profile_permissions_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        // Only the groups this Android version actually has: asking for
        // POST_NOTIFICATIONS on Android 12 would be asking for nothing.
        statuses.filter { status -> status.group.isSupported }.forEach { status ->
            PermissionRow(
                status = status,
                onGrantClick = {
                    // A new attempt cancels the hint: the permission may have
                    // been enabled from the system settings in the meantime.
                    requestUnavailable = false
                    permissionLauncher.launch(status.missing.toTypedArray())
                }
            )
        }

        if (requestUnavailable) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { openAppSettings(context) }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.profile_permissions_open_settings))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.profile_permissions_blocked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One permission group: what it is, what it is for, and its current state.
 *
 * A granted group shows the confirmation instead of a button, so the section
 * reads as a checklist rather than as a form.
 */
@Composable
private fun PermissionRow(
    status: PermissionStatus,
    onGrantClick: () -> Unit,
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
            Icon(
                imageVector = status.group.icon(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(status.group.titleRes()),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(status.group.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (status.isSatisfied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.profile_permissions_granted),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                TextButton(onClick = onGrantClick) {
                    Text(stringResource(R.string.profile_permissions_grant))
                }
            }
        }
    }
}

/**
 * Opens the system settings page of this app.
 *
 * Used when Android no longer shows the permission dialog: the settings page is
 * the only place where the user can still change the answer.
 */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    context.startActivity(intent)
}

/** The icon shown next to the group name, from the core Material icon set. */
private fun PermissionGroup.icon(): ImageVector = when (this) {
    PermissionGroup.LOCATION -> Icons.Filled.LocationOn
    PermissionGroup.ACTIVITY_RECOGNITION -> Icons.Filled.Person
    PermissionGroup.NOTIFICATIONS -> Icons.Filled.Notifications
}

/** Short name of the group, worded like the first-run page that explains it. */
@StringRes
private fun PermissionGroup.titleRes(): Int = when (this) {
    PermissionGroup.LOCATION -> R.string.profile_permission_location_title
    PermissionGroup.ACTIVITY_RECOGNITION -> R.string.profile_permission_steps_title
    PermissionGroup.NOTIFICATIONS -> R.string.profile_permission_notifications_title
}

/** One-line explanation of what the permission is used for. */
@StringRes
private fun PermissionGroup.descriptionRes(): Int = when (this) {
    PermissionGroup.LOCATION -> R.string.profile_permission_location_desc
    PermissionGroup.ACTIVITY_RECOGNITION -> R.string.profile_permission_steps_desc
    PermissionGroup.NOTIFICATIONS -> R.string.profile_permission_notifications_desc
}
