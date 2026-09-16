package com.example.progmob_progetto_melluso.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level ViewModel behind the first-run pages.
 *
 * Why it exists: whether the pages were already accepted is a DataStore
 * preference, and the host activity must not read the repository directly (the
 * same reason [com.example.progmob_progetto_melluso.ui.theme.ThemeViewModel]
 * exists). The activity only observes [uiState] and asks to persist the
 * acceptance; the navigation decision stays a pure reaction to that state.
 *
 * @param preferencesRepository the DataStore-backed preferences, which own the
 *   device-scoped first-run flag.
 */
class OnboardingViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /**
     * The first-run state, kept alive while a subscriber is watching and for a
     * few seconds after, so a rotation does not restart the DataStore read.
     */
    val uiState: StateFlow<OnboardingUiState> = preferencesRepository.onboardingCompleted
        .map { completed -> OnboardingUiState(completed = completed) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingUiState()
        )

    /**
     * Persists that the user went through the pages and accepted what they
     * explain: the persisted flag drives the app away from the onboarding on
     * this and every following launch.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }
}
