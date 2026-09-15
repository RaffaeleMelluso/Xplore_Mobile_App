package com.example.progmob_progetto_melluso.ui.profile

import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import com.example.progmob_progetto_melluso.stats.ProfileStatistics

/**
 * Immutable state of the Profile screen.
 *
 * How it works: the preferences (anonymous mode, path visibility) and the
 * avatar come from DataStore; the statistics come from the reactive flows of
 * [StatsRepository], so the screen re-renders as new data is recorded.
 */
data class ProfileUiState(
    val anonymous: Boolean = false,
    val showTravelPaths: Boolean = true,
    val avatarUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val stats: ProfileStatistics = ProfileStatistics()
)