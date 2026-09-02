package com.example.progmob_progetto_melluso.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import com.example.progmob_progetto_melluso.core.preferences.UserPreferencesRepository
import com.example.progmob_progetto_melluso.data.dao.StatsDao
import com.example.progmob_progetto_melluso.travel.TravelDistanceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val statsDao: StatsDao,
    private val userIdProvider: () -> String = { "local-user" }
) : ViewModel() {

    // The account the statistics belong to. Captured at construction time: the
    // ViewModel is recreated after a sign-in, so it always reflects the
    // CURRENT user and switching account empties the tiles.
    private val userId = userIdProvider()

    /** Distinct local dates (in the device timezone) present in the GPS trace. */
    private val activityDays: kotlinx.coroutines.flow.Flow<Int> = statsDao.pointTimestamps(userId).map { timestamps ->
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        if (timestamps.isEmpty()) {
            0
        } else {
            timestamps.map { formatter.format(Date(it)) }.distinct().size
        }
    }

    /**
     * Aggregated statistics flow. Distance is merged per travel so GPS and
     * pedometer values do not get double-counted across the whole profile.
     */
    private val statsFlow = combine(
        combine(statsDao.totalSteps(userId), activityDays, statsDao.poisVisited(userId)) { steps, days, pois ->
            Triple(steps, days, pois)
        },
        combine(
            statsDao.citiesVisited(userId),
            statsDao.travelsCount(userId),
            statsDao.travelStepDistances(userId),
            statsDao.travelPathPoints(userId)
        ) { cities, travels, stepDistances, pathPoints ->
            val stepDistanceByTravel = stepDistances.associate { it.travelId to it.distanceMeters }
            val gpsDistanceByTravel = pathPoints
                .groupBy { it.travelId }
                .mapValues { (_, points) -> TravelDistanceCalculator.calculate(0.0, points) }
            val travelIds = stepDistanceByTravel.keys + gpsDistanceByTravel.keys
            val distance = travelIds.sumOf { travelId ->
                TravelDistanceCalculator.calculate(
                    stepDistanceByTravel[travelId] ?: 0.0,
                    pathPoints.filter { it.travelId == travelId }
                )
            }
            Triple(cities, travels, distance)
        }
    ) { first, second ->
        ProfileStatistics(
            totalSteps = first.first,
            activityDays = first.second,
            poisVisited = first.third,
            citiesVisited = second.first,
            totalDistanceMeters = second.third,
            travelsCount = second.second
        )
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesRepository.preferences,
        statsFlow
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