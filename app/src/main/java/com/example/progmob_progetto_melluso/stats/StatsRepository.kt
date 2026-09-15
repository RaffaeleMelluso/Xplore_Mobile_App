package com.example.progmob_progetto_melluso.stats

import com.example.progmob_progetto_melluso.data.dao.StatsDao
import com.example.progmob_progetto_melluso.travel.TravelDistanceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Aggregate-read repository over the travel statistics shown on the Profile
 * screen.
 *
 * Why it exists: the statistics combine several Room tables (steps, GPS
 * points, visits, travels) with the shared distance calculator. Keeping that
 * aggregation here instead of inside the ViewModel follows the repository
 * pattern: the ViewModel only forwards the reactive [observeStatistics] flow
 * and never touches the DAO or the distance math directly.
 *
 * How it works: each DAO query exposes a [Flow], and the repository merges
 * them reactively, so the Profile screen re-renders as new data is recorded.
 * Distance is merged per travel (GPS vs pedometer) so values are not
 * double-counted across the whole profile. Every query is scoped to ONE user
 * (joined through `travels.userId`), taken once at construction time, so
 * switching account empties the tiles after the ViewModel is recreated.
 */
class StatsRepository(
    private val statsDao: StatsDao,
    private val userIdProvider: () -> String = { "local-user" }
) {

    // The account the statistics belong to. Captured at construction time: the
    // ViewModel is recreated after a sign-in, so it always reflects the
    // CURRENT user and switching account empties the tiles.
    private val userId = userIdProvider()

    /** Distinct local dates (in the device timezone) present in the GPS trace. */
    private val activityDays: Flow<Int> = statsDao.pointTimestamps(userId).map { timestamps ->
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
     * Aggregated statistics flow, combining every Room query into a single
     * [ProfileStatistics] emission.
     */
    fun observeStatistics(): Flow<ProfileStatistics> = combine(
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
}