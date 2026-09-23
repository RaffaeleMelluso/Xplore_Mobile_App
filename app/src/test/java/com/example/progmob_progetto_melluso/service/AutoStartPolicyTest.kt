package com.example.progmob_progetto_melluso.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the rule that decides whether a fix may open a trip by itself.
 *
 * The automatic start is the most intrusive thing the app does on its own, and
 * the tests pin the two independent signals that must agree — a real
 * displacement and a platform that reports the user as moving — together with
 * the fallback that keeps the feature working where Activity Recognition has no
 * opinion.
 */
class AutoStartPolicyTest {

    @Test
    fun movementWithTheAppOpenAndNoOpinion_starts() {
        assertTrue(
            AutoStartPolicy.shouldStart(
                appInForeground = true,
                movedBeyondBuffer = true,
                motion = MotionState.UNKNOWN
            )
        )
    }

    @Test
    fun movementWithTheAppOpenAndTheUserMoving_starts() {
        assertTrue(
            AutoStartPolicy.shouldStart(
                appInForeground = true,
                movedBeyondBuffer = true,
                motion = MotionState.MOVING
            )
        )
    }

    /**
     * A displacement while the platform says the user is STANDING STILL is
     * exactly the GPS noise the gate exists for: no trip.
     */
    @Test
    fun displacementWhileStandingStill_doesNotStart() {
        assertFalse(
            AutoStartPolicy.shouldStart(
                appInForeground = true,
                movedBeyondBuffer = true,
                motion = MotionState.STILL
            )
        )
    }

    /** A trip must never open from the background, whatever the sensors say. */
    @Test
    fun backgroundMovement_neverStarts() {
        assertFalse(
            AutoStartPolicy.shouldStart(
                appInForeground = false,
                movedBeyondBuffer = true,
                motion = MotionState.MOVING
            )
        )
    }

    /** No displacement means no movement was observed, however the platform calls it. */
    @Test
    fun noDisplacement_neverStarts() {
        assertFalse(
            AutoStartPolicy.shouldStart(
                appInForeground = true,
                movedBeyondBuffer = false,
                motion = MotionState.MOVING
            )
        )
    }
}
