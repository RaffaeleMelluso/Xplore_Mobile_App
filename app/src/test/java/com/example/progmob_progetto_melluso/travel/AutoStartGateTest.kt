package com.example.progmob_progetto_melluso.travel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure movement gate that decides when the foreground
 * tracking service may auto-start a trip (the moved-while-app-open rule).
 *
 * Coordinates are near Turin, where one degree of latitude is ~111 km, so
 * 0.0001 degrees is ~11 m: each sample states its real-world displacement.
 */
class AutoStartGateTest {

    /** The default production threshold under test (30 m). */
    private val gate = AutoStartGate()

    /** The very first fix can never show a displacement: the gate stays closed. */
    @Test
    fun firstFix_neverStarts() {
        assertFalse(gate.onFix(45.0700, 7.6900))
    }

    /** A jitter-sized hop (~11 m) is inside the noise buffer: no auto-start. */
    @Test
    fun smallJitter_doesNotStart() {
        gate.onFix(45.0700, 7.6900)
        assertFalse(gate.onFix(45.0701, 7.6900))
    }

    /** A hop beyond the buffer (~55 m) is a real movement: the gate opens. */
    @Test
    fun realMovement_starts() {
        gate.onFix(45.0700, 7.6900)
        assertTrue(gate.onFix(45.0705, 7.6900))
    }

    /**
     * The gate compares each fix with the LAST one it saw (not with the
     * first): a short hop keeps it closed, a following large hop opens it.
     */
    @Test
    fun gateComparesAgainstTheLastFix_notTheFirst() {
        gate.onFix(45.0700, 7.6900)
        // ~11 m from the previous fix: closed.
        assertFalse(gate.onFix(45.0701, 7.6900))
        // ~33 m from the LAST fix: the user is clearly moving.
        assertTrue(gate.onFix(45.0704, 7.6900))
    }

    /**
     * Uscire dall'app azzera il riferimento: il primo fix al rientro è una
     * nuova base, così un viaggio non si apre per uno spostamento avvenuto
     * mentre l'app era chiusa.
     */
    @Test
    fun reset_afterLeavingTheApp_makesTheFirstFixABaseline() {
        gate.onFix(45.0700, 7.6900)
        // The user left the app and drove far away: while the app is closed the
        // gate is not fed, and it is reset on leaving.
        gate.reset()
        // Reopening the app ~50 km from the last foreground fix: baseline only.
        assertFalse(gate.onFix(45.5000, 9.2000))
        // Movement observed ENTIRELY while the app is open still opens it.
        assertTrue(gate.onFix(45.5003, 9.2000)) // ~33 m from the baseline
    }

    /** After a start, a stationary fix closes the gate again. */
    @Test
    fun stoppingAfterMovement_closesTheGateAgain() {
        gate.onFix(45.0700, 7.6900)
        assertTrue(gate.onFix(45.0703, 7.6900)) // ~33 m: moving
        // ~11 m from the last fix: standing still again.
        assertFalse(gate.onFix(45.0704, 7.6900))
    }
}
