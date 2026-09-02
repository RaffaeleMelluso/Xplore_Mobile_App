package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelDistanceCalculatorTest {

    @Test
    fun calculate_usesGpsWhenPedometerIsUnavailable() {
        val points = listOf(point(0.0, 0.0, 0L), point(0.0, 0.001, 20_000L))

        val distance = TravelDistanceCalculator.calculate(0.0, points)

        assertEquals(111.0, distance, 2.0)
    }

    @Test
    fun calculate_usesPedometerWhenGpsHasNoContinuousPath() {
        val points = listOf(point(0.0, 0.0, 0L))

        assertEquals(500.0, TravelDistanceCalculator.calculate(500.0, points), 0.0)
    }

    @Test
    fun calculate_doesNotAddOverlappingEstimates() {
        val points = listOf(point(0.0, 0.0, 0L), point(0.0, 0.001, 20_000L))

        assertEquals(200.0, TravelDistanceCalculator.calculate(200.0, points), 0.0)
    }

    private fun point(lat: Double, lon: Double, timestamp: Long) =
        TravelPathPointEntity(
            id = 0L,
            travelId = 1L,
            latitude = lat,
            longitude = lon,
            recordedAtUtcMillis = timestamp,
            accuracyMeters = 5f
        )
}
