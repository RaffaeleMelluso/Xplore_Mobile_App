package com.example.progmob_progetto_melluso.service

import com.example.progmob_progetto_melluso.service.TrackingMode.ACTIVE
import com.example.progmob_progetto_melluso.service.TrackingMode.IDLE
import com.example.progmob_progetto_melluso.service.TrackingMode.PAUSED
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
     * A standing user does NOT pause a running trip: the path is being
     * recorded, and a pause would leave a hole in it.
     */
    @Test
    fun runningTrip_isActiveEvenWhileStandingStill() {
        assertEquals(
            ACTIVE,
            TrackingMode.of(tripActive = true, appInForeground = false, motion = MotionState.STILL)
        )
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
     * A standing user with the app open keeps the expensive profile too: the
     * map in front is showing the position, and the app is where the user is
     * about to start a trip by hand.
     */
    @Test
    fun visibleAppWithoutTrip_isActiveEvenWhileStandingStill() {
        assertEquals(
            ACTIVE,
            TrackingMode.of(tripActive = false, appInForeground = true, motion = MotionState.STILL)
        )
    }

    /**
     * Only "no trip AND app off screen" degrades: there the sole remaining
     * reason to know the position is the POI geofence.
     */
    @Test
    fun noTripAndAppOffScreen_isIdle() {
        assertEquals(IDLE, TrackingMode.of(tripActive = false, appInForeground = false))
    }

    /**
     * The same situation, but the platform reports the user is MOVING: the
     * cheap geofence profile, which is the whole point of the degradation.
     */
    @Test
    fun noTripOffScreenAndMoving_isIdle() {
        assertEquals(
            IDLE,
            TrackingMode.of(tripActive = false, appInForeground = false, motion = MotionState.MOVING)
        )
    }

    /**
     * The situation in which the app can stop asking for positions entirely: no
     * trip, app off screen and the user standing still. Nothing can happen
     * until they move, and the Activity Recognition transitions are what will
     * say so.
     */
    @Test
    fun noTripOffScreenAndStandingStill_isPaused() {
        assertEquals(
            PAUSED,
            TrackingMode.of(tripActive = false, appInForeground = false, motion = MotionState.STILL)
        )
    }

    /**
     * An UNKNOWN motion state must never pause the tracking: it means the
     * platform has no opinion (no Activity Recognition, refused permission,
     * nothing classified yet), and stopping on the strength of a signal the app
     * cannot read would silently break the geofence on those devices.
     */
    @Test
    fun unknownMotion_fallsBackToIdle() {
        assertEquals(
            IDLE,
            TrackingMode.of(tripActive = false, appInForeground = false, motion = MotionState.UNKNOWN)
        )
    }
}
