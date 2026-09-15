package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils

/**
 * Why an ongoing travel is considered stale (the vacation is probably over).
 *
 * The two triggers are independent and OR-ed: either the trip is simply too
 * old, or the user is physically far from where it started (they moved to
 * another city while the app was closed and the trip was never closed).
 */
enum class StaleReason {
    /** The trip started longer ago than [TravelConfig.tripEndDurationMillis]. */
    TOO_OLD,

    /** The current position is further than [TravelConfig.tripEndDistanceMeters]
     *  from the trip's anchor point (its first recorded location). */
    TOO_FAR
}

/**
 * Pure evaluation of \"is this ongoing travel still plausible?\".
 *
 * Why it exists (the travel lifecycle requirement): when the user reopens the app somewhere else
 * (or days later), the active trip must NOT be closed silently in the
 * background — the user is asked whether the vacation is still ongoing first.
 * This object only computes the signal; the UI turns the answer into the
 * stop/keep decision, and the whole check is deterministic and unit-testable.
 */
object TravelStaleness {

    /**
     * Returns the reason why [startUtcMillis] is stale, or null when the trip
     * still looks in progress.
     *
     * @param startUtcMillis start of the active travel (UTC millis).
     * @param anchorLat/anchorLon the trip's anchor point (first recorded fix);
     *   null when the trip has no points yet.
     * @param currentLat/currentLon the user's current position; null before
     *   the first fix (the distance trigger cannot fire without it).
     * @param maxDurationMillis the age threshold (OR trigger #1).
     * @param maxDistanceMeters the distance threshold from the anchor
     *   (OR trigger #2).
     * @param nowMillis \"now\" in UTC millis.
     */
    fun evaluate(
        startUtcMillis: Long,
        anchorLat: Double?,
        anchorLon: Double?,
        currentLat: Double?,
        currentLon: Double?,
        maxDurationMillis: Long,
        maxDistanceMeters: Double,
        nowMillis: Long
    ): StaleReason? {
        // Trigger #1: the trip is older than the maximum duration.
        if (nowMillis - startUtcMillis >= maxDurationMillis) return StaleReason.TOO_OLD

        // Trigger #2: the user is far from the anchor (needs both positions).
        if (anchorLat != null && anchorLon != null &&
            currentLat != null && currentLon != null
        ) {
            val distance = GeoUtils.haversineMeters(
                anchorLat, anchorLon, currentLat, currentLon
            )
            if (distance >= maxDistanceMeters) return StaleReason.TOO_FAR
        }
        return null
    }
}
