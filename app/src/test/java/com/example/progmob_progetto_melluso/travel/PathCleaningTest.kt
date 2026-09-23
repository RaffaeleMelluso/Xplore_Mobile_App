package com.example.progmob_progetto_melluso.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the path-cleaning rules ("paths need cleaning").
 *
 * Both rules protect the SHAPE of the trip from two different enemies: the
 * impossible fix (a GPS jump that would draw a line across the map) and the
 * barely informative one (a fix whose own error is larger than the movement it
 * reports). The tests state the arithmetic, so the thresholds can be retuned
 * without the behaviour drifting unnoticed.
 */
class PathCleaningTest {

    /** 300 km/h in meters per second: the production outlier ceiling. */
    private val maxSpeed = TravelConfig().maxPlausibleSpeedKmh / 3.6

    @Test
    fun impossibleJump_isNotPlausible() {
        // 1 km in 10 s: 360 km/h, i.e. a GPS teleport, not a movement.
        assertFalse(PathCleaning.isPlausibleMovement(1_000.0, 10_000L, maxSpeed))
    }

    @Test
    fun normalMovement_isPlausible() {
        // 100 m in 10 s: 36 km/h, a brisk but very real car/bike movement.
        assertTrue(PathCleaning.isPlausibleMovement(100.0, 10_000L, maxSpeed))
        // 12 m in 10 s: walking.
        assertTrue(PathCleaning.isPlausibleMovement(12.0, 10_000L, maxSpeed))
    }

    /** Standing still says nothing about speed and must never be rejected. */
    @Test
    fun noDisplacement_isAlwaysPlausible() {
        assertTrue(PathCleaning.isPlausibleMovement(0.0, 0L, maxSpeed))
        assertTrue(PathCleaning.isPlausibleMovement(0.0, 60_000L, maxSpeed))
    }

    /**
     * A displacement with no time between the fixes is not a measurement: it is
     * a stale fix delivered with a fresh timestamp, and accepting it would put
     * a jump in the path that no filter could explain.
     */
    @Test
    fun displacementWithoutElapsedTime_isNotPlausible() {
        assertFalse(PathCleaning.isPlausibleMovement(50.0, 0L, maxSpeed))
    }

    /** A confident fix is stored as it is: no smoothing, no lag. */
    @Test
    fun preciseFix_isTakenVerbatim() {
        val cleaned = PathCleaning.smooth(0.0, 0.0, 0.001, 0.002, nextAccuracyMeters = 10.0, referenceMeters = 15.0)

        assertEquals(0.001, cleaned.first, 0.0)
        assertEquals(0.002, cleaned.second, 0.0)
    }

    /** A fix carrying no accuracy cannot be judged, so it is trusted. */
    @Test
    fun fixWithoutAccuracy_isTakenVerbatim() {
        val cleaned = PathCleaning.smooth(0.0, 0.0, 0.001, 0.002, nextAccuracyMeters = null, referenceMeters = 15.0)

        assertEquals(0.001, cleaned.first, 0.0)
    }

    /**
     * A fix with twice the reference error keeps half of its displacement: it
     * is pulled toward the last known point by exactly how much worse it is.
     */
    @Test
    fun noisyFix_isBlendedTowardThePreviousPoint() {
        val cleaned = PathCleaning.smooth(0.0, 0.0, 0.010, 0.0, nextAccuracyMeters = 30.0, referenceMeters = 15.0)

        assertEquals(0.005, cleaned.first, 0.0)
    }

    /**
     * The blend is bounded: the result stays strictly between the previous
     * point and the new fix, so smoothing can never overshoot the movement nor
     * leave the path behind the point it came from.
     */
    @Test
    fun blendNeverOvershootsNorGoesBackwards() {
        val previous = 0.0
        val next = 0.010
        val cleaned = PathCleaning.smooth(previous, 0.0, next, 0.0, nextAccuracyMeters = 200.0, referenceMeters = 15.0)

        assertTrue(cleaned.first > previous)
        assertTrue(cleaned.first < next)
    }

    /** A non-positive reference disables the smoothing instead of dividing by zero. */
    @Test
    fun nonPositiveReference_isTakenVerbatim() {
        val cleaned = PathCleaning.smooth(0.0, 0.0, 0.010, 0.0, nextAccuracyMeters = 30.0, referenceMeters = 0.0)

        assertEquals(0.010, cleaned.first, 0.0)
    }
}
