package com.example.progmob_progetto_melluso.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import com.example.progmob_progetto_melluso.stats.StatsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel of the Profile screen (the preferences requirement + NFR privacy).
 *
 * How it works: it combines the DataStore preferences (anonymous mode, path
 * visibility, avatar) with the reactive Room statistics into a single
 * [ProfileUiState], and forwards the user intents (toggle a preference, change
 * the avatar) to the DataStore repository in [viewModelScope].
 */
class ProfileViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesRepository.preferences,
        statsRepository.observeStatistics()
    ) { preferences, stats ->
        ProfileUiState(
            anonymous = preferences.anonymous,
            showTravelPaths = preferences.showTravelPaths,
            avatarUri = preferences.avatarUri,
            themeMode = preferences.themeMode,
            stats = stats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    /** Toggles the anonymous-mode preference and persists it via DataStore. */
    fun setAnonymous(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAnonymous(enabled) }
    }

    /** Toggles the travel-path visibility preference and persists it. */
    fun setShowTravelPaths(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowTravelPaths(enabled) }
    }

    /** Sets the app theme mode (system/light/dark) and persists it. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    /** Updates the profile avatar to the given local file URI (null removes it). */
    fun updateAvatar(uri: String?) {
        viewModelScope.launch { preferencesRepository.setAvatarUri(uri) }
    }
}