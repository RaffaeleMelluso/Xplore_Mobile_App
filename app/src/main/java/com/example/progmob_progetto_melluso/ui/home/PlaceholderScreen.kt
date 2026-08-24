package com.example.progmob_progetto_melluso.ui.home

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R

/**
 * Generic "coming soon" screen for the tabs that are not implemented yet.
 *
 * How it works: a centered icon, the destination name and a short description
 * with a "coming soon" hint. No business logic: it is pure presentation.
 *
 * @param icon the icon of the future feature.
 * @param titleRes string resource with the destination name.
 * @param descriptionRes string resource with a one-line description.
 */
@Composable
fun PlaceholderScreen(
    icon: ImageVector,
    titleRes: Int,
    descriptionRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Minimal profile placeholder: identity of the signed-in user and sign out.
 *
 * How it works: shows the display name (or the email as fallback) and the
 * sign-out action. The rest of the profile features are not implemented yet.
 *
 * @param displayName display name of the signed-in user, if available.
 * @param email email of the signed-in user.
 * @param onSignOut callback invoked when the user taps "Sign out".
 */
@Composable
fun ProfilePlaceholder(
    displayName: String?,
    email: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = displayName ?: email ?: stringResource(R.string.tab_profile),
            style = MaterialTheme.typography.headlineSmall
        )
        if (email != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(R.string.logout))
        }
        // Build identifier: lets you confirm at a glance which version is installed.
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_version_label, installedVersionName()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Reads the installed version name without relying on BuildConfig.
 *
 * How it works: asks the package manager for the package info of this app and
 * returns its [PackageInfo.versionName]; falls back to an empty string if the
 * lookup fails (should never happen in practice).
 *
 * @return the version name (for example "1.1") or "" on failure.
 */
@Composable
private fun installedVersionName(): String {
    val context = LocalContext.current
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (exception: PackageManager.NameNotFoundException) {
        ""
    }
}
