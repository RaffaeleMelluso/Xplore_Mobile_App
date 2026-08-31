package com.example.progmob_progetto_melluso.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import com.example.progmob_progetto_melluso.data.dao.StatsDao

/**
 * Factory that builds [ProfileViewModel] with its [UserPreferencesRepository]
 * (DataStore) and [StatsDao] (Room) dependencies.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds.
 */
class ProfileViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val statsDao: StatsDao,
    private val userIdProvider: () -> String = { "local-user" }
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(preferencesRepository, statsDao, userIdProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}