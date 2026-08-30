package com.example.progmob_progetto_melluso.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the pure staleness signal: an ongoing travel looks finished when
 * it is too old OR the user is too far from where it started. The UI turns
 * this signal into the \"is your trip still in progress?\" question.
 */
class TravelStalenessTest {

    private val now = 2_000_000_000_000L
    private val threeDays = 3L * 24L * 60L * 60L * 1_000L
    private val maxDuration = threeDays
    private val maxDistance = 50_000.0

    @Test
    fun evaluate_nullWhenRecentAndNearAnchor() {
        // Started 1 day ago, ~10 km from the anchor: still in progress.
        assertNull(
            TravelStaleness.evaluate(
                startUtcMillis = now - 86_400_000L,
                anchorLat = 45.0,
                anchorLon = 7.0,
                currentLat = 45.09,
                currentLon = 7.0,
                maxDurationMillis = maxDuration,
                maxDistanceMeters = maxDistance,
                nowMillis = now
            )
        )
    }

    @Test
    fun evaluate_tooOldWhenStartedLongerAgoThanMaxDuration() {
        assertEquals(
            StaleReason.TOO_OLD,
            TravelStaleness.evaluate(
                startUtcMillis = now - threeDays - 1,
                anchorLat = null,
                anchorLon = null,
                currentLat = null,
                currentLon = null,
                maxDurationMillis = maxDuration,
                maxDistanceMeters = maxDistance,
                nowMillis = now
            )
        )
    }

    @Test
    fun evaluate_tooFarWhenBeyondDistanceFromAnchor() {
        // ~55 km from the anchor (moved to another city): vacation over.
        assertEquals(
            StaleReason.TOO_FAR,
            TravelStaleness.evaluate(
                startUtcMillis = now - 1_000L,
                anchorLat = 45.0,
                anchorLon = 7.0,
                currentLat = 45.5,
                currentLon = 7.0,
                maxDurationMillis = maxDuration,
                maxDistanceMeters = maxDistance,
                nowMillis = now
            )
        )
    }

    @Test
    fun evaluate_nullWithoutCurrentPosition() {
        // Before the first fix the distance trigger cannot fire; the trip is
        // recent, so nothing is stale.
        assertNull(
            TravelStaleness.evaluate(
                startUtcMillis = now - 1_000L,
                anchorLat = 45.0,
                anchorLon = 7.0,
                currentLat = null,
                currentLon = null,
                maxDurationMillis = maxDuration,
                maxDistanceMeters = maxDistance,
                nowMillis = now
            )
        )
    }

    @Test
    fun evaluate_oldWinsWhenBothTriggersFire() {
        // Both old and far: the age trigger is reported (checked first).
        assertEquals(
            StaleReason.TOO_OLD,
            TravelStaleness.evaluate(
                startUtcMillis = now - threeDays - 1,
                anchorLat = 45.0,
                anchorLon = 7.0,
                currentLat = 45.5,
                currentLon = 7.0,
                maxDurationMillis = maxDuration,
                maxDistanceMeters = maxDistance,
                nowMillis = now
            )
        )
    }
}
