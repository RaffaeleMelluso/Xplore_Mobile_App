package com.example.progmob_progetto_melluso.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.ui.journal.TravelJournalScreen
import com.example.progmob_progetto_melluso.ui.map.MapScreen
import com.example.progmob_progetto_melluso.ui.poi.MyPoiScreen
import com.example.progmob_progetto_melluso.ui.profile.ProfileScreen
import com.example.progmob_progetto_melluso.ui.trend.TrendScreen

/** Height reserved for the bottom bar (excluding the system navigation inset). */
private val NAV_BAR_HEIGHT = 64.dp

/** Diameter of the raised central map button. */
private val MAP_BUTTON_SIZE = 60.dp

/** How much the central map button protrudes above the bar's top edge. */
private val MAP_BUTTON_RAISE = (-24).dp

/** Vertical gap between the central circle and its label. */
private val MAP_LABEL_SPACING = 4.dp

/**
 * Home scaffold shown after login: a bottom bar with a raised central Map
 * action and the other destinations around it.
 *
 * How it works: each tab hosts its own screen (Map, Journal, Trends, My POIs,
 * Profile); the bottom bar uses the same background color as the screens (not
 * Material's default container), so it never clashes with the rest of the UI.
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

    // Stack of previously visited tabs (indices, oldest first), stored as a
    // comma-separated string so it survives configuration changes.
    var history by rememberSaveable { mutableStateOf("") }

    // Switching tab records the current one in the history, so the system back
    // can return to the previously visited screen instead of exiting the app.
    val selectTab: (HomeTab) -> Unit = { tab ->
        val newIndex = tab.ordinal
        if (newIndex != selectedTabIndex) {
            history = if (history.isEmpty()) selectedTabIndex.toString() else "$history,$selectedTabIndex"
            selectedTabIndex = newIndex
        }
    }

    // System back with a history: go to the previous tab. Consumed only when
    // there is history; deeper BackHandlers (a travel/trend detail) win first
    // and close their detail, so the app also backs out of a detail smoothly.
    BackHandler(enabled = history.isNotEmpty()) {
        val parts = history.split(',')
        selectedTabIndex = parts.last().toInt()
        history = parts.dropLast(1).joinToString(",")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            HomeBottomBar(
                selectedTab = HomeTab.entries[selectedTabIndex],
                onTabSelected = selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (HomeTab.entries[selectedTabIndex]) {
                HomeTab.MAP -> MapScreen()
                HomeTab.TRAVEL -> TravelJournalScreen()
                HomeTab.TRENDS -> TrendScreen()
                HomeTab.MY_POIS -> MyPoiScreen()
                HomeTab.PROFILE -> ProfileScreen(
                    displayName = displayName,
                    email = email,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

/**
 * Custom bottom bar with a raised central Map button.
 *
 * How it works: the bar itself holds Travel, Trends and Profile (with a gap
 * in the middle for symmetry); the Map action is a SEPARATE floating element
 * drawn after the bar — a raised circular primary button with its "Map"
 * label — so nothing ever clips its elevated part and it is immediately
 * recognizable as the home/map destination. The bar uses the same background
 * color as the screens.
 */
@Composable
private fun HomeBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // The bar: background-colored, three destinations and a center gap.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NAV_BAR_HEIGHT),
                    verticalAlignment = Alignment.Bottom
                ) {
                    BarItem(
                        tab = HomeTab.TRAVEL,
                        selected = selectedTab == HomeTab.TRAVEL,
                        onClick = { onTabSelected(HomeTab.TRAVEL) },
                        modifier = Modifier.weight(1f)
                    )
                    BarItem(
                        tab = HomeTab.TRENDS,
                        selected = selectedTab == HomeTab.TRENDS,
                        onClick = { onTabSelected(HomeTab.TRENDS) },
                        modifier = Modifier.weight(1f)
                    )
                    // Center gap: the floating Map button sits over it.
                    Spacer(modifier = Modifier.weight(1f))
                    BarItem(
                        tab = HomeTab.MY_POIS,
                        selected = selectedTab == HomeTab.MY_POIS,
                        onClick = { onTabSelected(HomeTab.MY_POIS) },
                        modifier = Modifier.weight(1f)
                    )
                    BarItem(
                        tab = HomeTab.PROFILE,
                        selected = selectedTab == HomeTab.PROFILE,
                        onClick = { onTabSelected(HomeTab.PROFILE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Central raised Map action: circle + label, a sibling of the bar so
        // the bar never clips its elevated part.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = MAP_BUTTON_RAISE),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                onClick = { onTabSelected(HomeTab.MAP) },
                modifier = Modifier.size(MAP_BUTTON_SIZE),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HomeTab.MAP.icon,
                        contentDescription = stringResource(HomeTab.MAP.labelRes)
                    )
                }
            }
            Spacer(Modifier.height(MAP_LABEL_SPACING))
            // Label below the circle: makes it explicit this is the map/home.
            Text(
                text = stringResource(HomeTab.MAP.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedTab == HomeTab.MAP) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** A single destination (icon + label) of the bottom bar. */
@Composable
private fun BarItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.labelRes),
            tint = tint
        )
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

/** The five destinations of the home bottom navigation bar. */
private enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    MAP(R.string.tab_map, Icons.Filled.LocationOn),
    TRAVEL(R.string.tab_travel, Icons.AutoMirrored.Filled.List),
    TRENDS(R.string.tab_trends, Icons.Filled.Star),
    MY_POIS(R.string.tab_my_pois, Icons.Filled.Place),
    PROFILE(R.string.tab_profile, Icons.Filled.Person)
}
