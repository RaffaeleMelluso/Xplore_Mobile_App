package com.example.progmob_progetto_melluso.stats

import com.example.progmob_progetto_melluso.data.dao.StatsDao
import com.example.progmob_progetto_melluso.data.dao.TravelStepDistance
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StatsRepository]: verifies that the Profile statistics
 * aggregate every Room counter and that the per-travel distance merge (GPS vs
 * pedometer, largest wins) never double-counts across the whole profile.
 */
class StatsRepositoryTest {

    private class FakeStatsDao(
        private val steps: Long = 0L,
        private val timestamps: List<Long> = emptyList(),
        private val pois: Int = 0,
        private val cities: Int = 0,
        private val travels: Int = 0,
        private val stepDistances: List<TravelStepDistance> = emptyList(),
        private val pathPoints: List<TravelPathPointEntity> = emptyList()
    ) : StatsDao {
        override fun totalSteps(userId: String): Flow<Long> = flowOf(steps)
        override fun totalDistanceMeters(userId: String): Flow<Double> = flowOf(0.0)
        override fun travelStepDistances(userId: String): Flow<List<TravelStepDistance>> = flowOf(stepDistances)
        override fun travelPathPoints(userId: String): Flow<List<TravelPathPointEntity>> = flowOf(pathPoints)
        override fun pointTimestamps(userId: String): Flow<List<Long>> = flowOf(timestamps)
        override fun travelsCount(userId: String): Flow<Int> = flowOf(travels)
        override fun poisVisited(userId: String): Flow<Int> = flowOf(pois)
        override fun citiesVisited(userId: String): Flow<Int> = flowOf(cities)
    }

    private fun pathPoint(travelId: Long, lat: Double, lon: Double) = TravelPathPointEntity(
        travelId = travelId,
        latitude = lat,
        longitude = lon,
        recordedAtUtcMillis = 0L
    )

    @Test
    fun observeStatistics_aggregatesEveryCounter() = runTest {
        val repository = StatsRepository(
            FakeStatsDao(
                steps = 5_000L,
                // One point per day: still distinct local dates in any timezone.
                timestamps = listOf(0L, 86_400_000L, 2 * 86_400_000L),
                pois = 3,
                cities = 2,
                travels = 4
            )
        )

        val stats = repository.observeStatistics().first()

        assertEquals(5_000L, stats.totalSteps)
        assertEquals(3, stats.activityDays)
        assertEquals(3, stats.poisVisited)
        assertEquals(2, stats.citiesVisited)
        assertEquals(4, stats.travelsCount)
        assertEquals(0.0, stats.totalDistanceMeters, 0.001)
    }

    @Test
    fun observeStatistics_gpsDistanceUsedWhenLargerThanPedometer() = runTest {
        // Travel 1: pedometer says 500 m but the GPS trace measures ~777 m
        // (0.007 degrees of longitude at the equator): GPS wins for that trip.
        // Travel 2: only GPS, ~300 m.
        val repository = StatsRepository(
            FakeStatsDao(
                stepDistances = listOf(TravelStepDistance(travelId = 1L, distanceMeters = 500.0)),
                pathPoints = listOf(
                    pathPoint(1L, 0.0, 0.0),
                    pathPoint(1L, 0.0, 0.007),
                    pathPoint(2L, 0.0, 0.0),
                    pathPoint(2L, 0.0, 0.0027)
                )
            )
        )

        val stats = repository.observeStatistics().first()

        // 777 (GPS wins over 500 pedometer meters) + 300 (GPS only) = ~1077 m.
        assertEquals(1_077.0, stats.totalDistanceMeters, 4.0)
    }

    @Test
    fun observeStatistics_pedometerDistanceUsedWhenGpsIsNegligible() = runTest {
        // Travel 1: pedometer reports 800 m, GPS trace is ~11 m (0.0001 deg):
        // the pedometer wins and no phantom GPS distance is added.
        val repository = StatsRepository(
            FakeStatsDao(
                stepDistances = listOf(TravelStepDistance(travelId = 1L, distanceMeters = 800.0)),
                pathPoints = listOf(
                    pathPoint(1L, 0.0, 0.0),
                    pathPoint(1L, 0.0, 0.0001)
                )
            )
        )

        val stats = repository.observeStatistics().first()

        assertEquals(800.0, stats.totalDistanceMeters, 1.0)
    }
}