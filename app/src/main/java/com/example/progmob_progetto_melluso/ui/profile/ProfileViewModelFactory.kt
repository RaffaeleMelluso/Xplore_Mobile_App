package com.example.progmob_progetto_melluso.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import com.example.progmob_progetto_melluso.stats.StatsRepository

/**
 * Factory that builds [ProfileViewModel] with its [UserPreferencesRepository]
 * (DataStore) and [StatsRepository] (aggregated Room statistics) dependencies.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds.
 */
class ProfileViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val statsRepository: StatsRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(preferencesRepository, statsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}