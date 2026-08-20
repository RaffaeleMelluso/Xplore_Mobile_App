package com.example.progmob_progetto_melluso.travel

/**
 * Tunable thresholds that control how the travel path is recorded and when a
 * trip is considered finished.
 *
 * Exposed as a data class so that tests can inject small deterministic values
 * instead of the production defaults.
 *
 * @property minDistanceMeters Minimum distance from the last recorded point
 *   before a new path point is stored (spatial filter, battery saving).
 * @property minElapsedMillis Minimum time since the last recorded point before
 *   a new path point is stored (temporal filter, battery saving).
 * @property tripEndDistanceMeters Straight-line distance from the trip's anchor
 *   point (its first recorded location) at which the trip is closed
 *   automatically: leaving the vacation area by this distance means the
 *   vacation is over. Default 50 km.
 */
data class TravelConfig(
    val minDistanceMeters: Double = 10.0,
    val minElapsedMillis: Long = 10_000L,
    val tripEndDistanceMeters: Double = 50_000.0
)
