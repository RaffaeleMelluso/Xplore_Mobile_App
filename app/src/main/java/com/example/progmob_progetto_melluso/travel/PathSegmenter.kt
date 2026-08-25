package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity

/**
 * Splits a travel path into continuous segments for rendering.
 *
 * Why it exists: when the tracking is interrupted (app killed from the recents
 * screen, device reboot, GPS off for a while) and then resumed, the points
 * recorded before and after the gap must NOT be connected by a straight line:
 * the user may have moved kilometres while not tracked. This helper breaks
 * the polyline at those discontinuities so the map draws two (or more)
 * separate segments instead of one false shortcut.
 *
 * How it works: two consecutive stored points are considered part of different
 * segments only when BOTH conditions hold:
 *  - more than [MAX_GAP_MILLIS] elapsed between them (tracking was off), and
 *  - more than [MAX_JUMP_METERS] between them (the user actually moved).
 * The distance condition avoids splitting on legitimate long stops (a museum
 * visit, a lunch break): while stationary no points are stored, but resuming
 * the walk produces a point near the last one, so the path stays connected.
 */
object PathSegmenter {

    /** Minimum time gap between consecutive points to consider a break. */
    const val MAX_GAP_MILLIS = 120_000L

    /** Minimum spatial jump to consider the gap a real interruption. */
    const val MAX_JUMP_METERS = 150.0

    /**
     * Splits the ordered path points into contiguous segments.
     *
     * @param points Path points ordered from oldest to newest.
     * @return A list of segments; each segment has at least 2 points and can
     *   be drawn as a straight polyline. Returns an empty list for paths with
     *   fewer than 2 points.
     */
    fun split(points: List<TravelPathPointEntity>): List<List<TravelPathPointEntity>> {
        if (points.size < 2) return emptyList()

        val segments = mutableListOf<List<TravelPathPointEntity>>()
        var current = mutableListOf(points.first())

        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val point = points[i]
            val timeGapMillis = point.recordedAtUtcMillis - previous.recordedAtUtcMillis
            val jumpMeters = GeoUtils.haversineMeters(
                previous.latitude, previous.longitude, point.latitude, point.longitude
            )
            if (timeGapMillis > MAX_GAP_MILLIS && jumpMeters > MAX_JUMP_METERS) {
                // Discontinuity: close the current segment and start a new one.
                segments.add(current)
                current = mutableListOf(point)
            } else {
                current.add(point)
            }
        }
        segments.add(current)

        // Single-point segments cannot be drawn as lines: drop them.
        return segments.filter { it.size >= 2 }
    }
}
