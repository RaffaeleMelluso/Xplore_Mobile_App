package com.example.progmob_progetto_melluso.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.ui.map.MapScreen

/**
 * Home scaffold shown after login: a bottom navigation bar with four tabs.
 *
 * How it works: only the Map tab is implemented; the other three are
 * placeholders rendered by [PlaceholderScreen] (and a minimal profile with the
 * sign-out action), so the navigation structure is ready for the upcoming
 * features without implementing them yet.
 *
 * @param displayName display name of the signed-in user, if available.
 * @param email email of the signed-in user, used as fallback label.
 * @param onSignOut callback invoked when the user taps "Sign out".
 */
@Composable
fun HomeScreen(
    displayName: String?,
    email: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pure presentation state: which tab is selected. Survives configuration changes.
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Use the themed surface container (ocean palette) instead of the
            // default Material gray, so the navbar matches the rest of the app.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                HomeTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (HomeTab.entries[selectedTabIndex]) {
                HomeTab.MAP -> MapScreen()
                HomeTab.TRAVEL -> PlaceholderScreen(
                    icon = Icons.AutoMirrored.Filled.List,
                    titleRes = R.string.tab_travel,
                    descriptionRes = R.string.placeholder_travel
                )
                HomeTab.TRENDS -> PlaceholderScreen(
                    icon = Icons.Filled.Star,
                    titleRes = R.string.tab_trends,
                    descriptionRes = R.string.placeholder_trends
                )
                HomeTab.PROFILE -> ProfilePlaceholder(
                    displayName = displayName,
                    email = email,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

/** The four destinations of the home bottom navigation bar. */
private enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    MAP(R.string.tab_map, Icons.Filled.LocationOn),
    TRAVEL(R.string.tab_travel, Icons.AutoMirrored.Filled.List),
    TRENDS(R.string.tab_trends, Icons.Filled.Star),
    PROFILE(R.string.tab_profile, Icons.Filled.Person)
}
