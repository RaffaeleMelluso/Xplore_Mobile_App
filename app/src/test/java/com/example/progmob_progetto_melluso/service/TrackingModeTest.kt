package com.example.progmob_progetto_melluso.service

import com.example.progmob_progetto_melluso.service.TrackingMode.ACTIVE
import com.example.progmob_progetto_melluso.service.TrackingMode.IDLE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of the pure rule that picks the location sampling profile.
 *
 * The rule is what stands between the app and a battery drain: the expensive
 * profile may only be used when the position is actually needed. The tests
 * state each of the three situations explicitly, so a future change of the
 * condition cannot silently degrade to the cheap profile while a trip is being
 * recorded (which would lose path detail) nor keep the GPS at full rate with
 * nothing to track (the bug these profiles exist for).
 */
class TrackingModeTest {

    /** A running trip always needs precision, visible app or not. */
    @Test
    fun runningTrip_isAlwaysActive() {
        assertEquals(ACTIVE, TrackingMode.of(tripActive = true, appInForeground = true))
        assertEquals(ACTIVE, TrackingMode.of(tripActive = true, appInForeground = false))
    }

    /**
     * No trip, but the app is on screen: still active, because that is the only
     * situation where moving can open a trip by itself.
     */
    @Test
    fun visibleAppWithoutTrip_isStillActive() {
        assertEquals(ACTIVE, TrackingMode.of(tripActive = false, appInForeground = true))
    }

    /**
     * Only "no trip AND app off screen" degrades: there the sole remaining
     * reason to know the position is the POI geofence.
     */
    @Test
    fun noTripAndAppOffScreen_isIdle() {
        assertEquals(IDLE, TrackingMode.of(tripActive = false, appInForeground = false))
    }
}
