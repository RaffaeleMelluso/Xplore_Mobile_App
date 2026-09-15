package com.example.progmob_progetto_melluso.stats

/**
 * Overall statistics of the user, aggregated across every recorded travel.
 *
 * @param totalSteps total steps recorded by the pedometer.
 * @param activityDays number of distinct local days with recorded GPS activity.
 * @param poisVisited number of distinct POIs visited across all trips.
 * @param citiesVisited number of destinations (cities) visited across all trips.
 * @param totalDistanceMeters total distance covered in meters.
 * @param travelsCount number of travels in the journal.
 */
data class ProfileStatistics(
    val totalSteps: Long = 0L,
    val activityDays: Int = 0,
    val poisVisited: Int = 0,
    val citiesVisited: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val travelsCount: Int = 0
)