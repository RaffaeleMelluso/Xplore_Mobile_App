package com.example.progmob_progetto_melluso.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry helpers shared by the travel path and the POI proximity logic.
 *
 * No Android dependency is used here, so the math is unit-testable on the JVM.
 */
object GeoUtils {

    /** Mean Earth radius in meters, used by the haversine formula. */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Computes the great-circle distance in meters between two coordinates
     * using the haversine formula.
     *
     * How it works: the latitude/longitude deltas are converted to radians and
     * fed into the haversine equation, then the angular result is multiplied by
     * the Earth radius to obtain a distance in meters.
     *
     * @param lat1 Latitude of the first point, in degrees.
     * @param lon1 Longitude of the first point, in degrees.
     * @param lat2 Latitude of the second point, in degrees.
     * @param lon2 Longitude of the second point, in degrees.
     * @return Distance in meters (always >= 0).
     */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
