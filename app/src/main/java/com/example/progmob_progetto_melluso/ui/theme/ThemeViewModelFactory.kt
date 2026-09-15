package com.example.progmob_progetto_melluso.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository

/**
 * Factory that builds [ThemeViewModel] with its [UserPreferencesRepository]
 * dependency, bridging the manual DI container ([AppContainer]) and the
 * ViewModelProvider.
 */
class ThemeViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeViewModel::class.java)) {
            return ThemeViewModel(preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}