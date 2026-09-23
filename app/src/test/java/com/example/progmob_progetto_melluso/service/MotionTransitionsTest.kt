package com.example.progmob_progetto_melluso.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests of the pure reducer that turns Activity Recognition transitions
 * into the motion state the app decides on (context based on actions).
 *
 * The rule is not decoration: it is what makes pausing the GPS safe (a wrong
 * "still" would stop the tracking while the user is walking) and what keeps the
 * automatic trip start honest (a wrong "moving" would open vacations nobody
 * asked for). Each case below is one of the events the platform really sends.
 */
class MotionTransitionsTest {

    @Test
    fun enteringAWalkingActivity_isMoving() {
        assertEquals(
            MotionState.MOVING,
            MotionTransitions.impliedState(MotionActivity.MOVING, entering = true)
        )
    }

    @Test
    fun enteringStill_isStill() {
        assertEquals(
            MotionState.STILL,
            MotionTransitions.impliedState(MotionActivity.STILL, entering = true)
        )
    }

    /**
     * Leaving stillness is how the app is WOKEN UP after a pause: without this
     * event the tracker would stay asleep while the user is already walking.
     */
    @Test
    fun leavingStill_isMoving() {
        assertEquals(
            MotionState.MOVING,
            MotionTransitions.impliedState(MotionActivity.STILL, entering = false)
        )
    }

    /**
     * Leaving a moving activity says nothing on its own: walking into a car is
     * a `WALKING EXIT` immediately followed by an `IN_VEHICLE ENTER`, so
     * treating the exit as "now still" would stop the tracking between two
     * moving activities. The next ENTER decides.
     */
    @Test
    fun leavingAMovingActivity_changesNothing() {
        assertNull(MotionTransitions.impliedState(MotionActivity.MOVING, entering = false))
    }

    /** Activities the app has no opinion about never move the state. */
    @Test
    fun irrelevantActivity_changesNothing() {
        assertNull(MotionTransitions.impliedState(MotionActivity.OTHER, entering = true))
        assertNull(MotionTransitions.impliedState(MotionActivity.OTHER, entering = false))
    }
}
