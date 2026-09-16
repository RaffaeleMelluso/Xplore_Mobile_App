package com.example.progmob_progetto_melluso.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository

/**
 * Factory that builds [OnboardingViewModel] with its
 * [UserPreferencesRepository] dependency, bridging the manual DI container
 * ([com.example.progmob_progetto_melluso.core.AppContainer]) and the
 * ViewModelProvider.
 */
class OnboardingViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
