package com.example.progmob_progetto_melluso.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App-level ViewModel that exposes the user's theme choice to the activity.
 *
 * Why it exists: the theme is applied app-wide (in [MainActivity]), but
 * reading the preference there directly would couple the View layer to the
 * DataStore repository. This ViewModel is the single mediator between the
 * preference repository and the View, keeping the MVVM separation intact:
 * UI only observes [themeMode] and never touches the data layer.
 */
class ThemeViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** The user's theme mode; SYSTEM (follow device) until the value loads. */
    val themeMode: StateFlow<ThemeMode> = preferencesRepository.preferences
        .map { it.themeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )
}