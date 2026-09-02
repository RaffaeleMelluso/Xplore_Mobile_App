package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity

/**
 * Combines the two locally available distance estimates for one travel:
 * the pedometer estimate and the GPS path distance.
 */
object TravelDistanceCalculator {

    /**
     * Returns the best available estimate without adding overlapping walking
     * measurements twice.
     *
     * @param pedometerMeters distance estimated from the step counter.
     * @param pathPoints recorded GPS path of the travel.
     * @return the greater of the two estimates (never negative).
     */
    fun calculate(
        pedometerMeters: Double,
        pathPoints: List<TravelPathPointEntity>
    ): Double = maxOf(
        pedometerMeters.coerceAtLeast(0.0),
        PathSegmenter.distanceMeters(pathPoints)
    )
}
