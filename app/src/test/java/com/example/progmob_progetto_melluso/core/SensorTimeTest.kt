package com.example.progmob_progetto_melluso.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of the conversion between the sensor timeline and the wall clock.
 *
 * The rule under test is the one the sensing lecture states explicitly: a
 * `SensorEvent.timestamp` is NOT a date, it is the time elapsed since the
 * device booted, in nanoseconds, and the app must reconstruct the instant the
 * reading was TAKEN instead of stamping it with the moment it was processed.
 *
 * The tests fix the arithmetic on plain numbers, so a change of formula (a
 * wrong unit, a missing boot offset) cannot silently date every stored reading
 * wrong on a device.
 */
class SensorTimeTest {

    /** The wall clock at delivery time: a fixed, readable instant. */
    private val wallNow = 1_700_000_000_000L

    /** The device booted 1000 s before that instant. */
    private val elapsedRealtime = 1_000_000L

    /**
     * The reconstructed boot instant is `wall now - elapsed realtime`, so a
     * reading taken at the very beginning of that uptime is dated exactly there.
     */
    @Test
    fun readingAtBoot_isDatedAtTheBootInstant() {
        assertEquals(
            wallNow - elapsedRealtime,
            SensorTime.wallClockUtcMillis(sensorNanos = 0L, wallNow, elapsedRealtime)
        )
    }

    /**
     * A reading taken 500 s after boot is dated 500 s after the boot instant:
     * this is the case the whole conversion exists for.
     */
    @Test
    fun readingSinceBoot_isOffsetFromTheBootInstant() {
        val sensorNanos = 500_000_000_000L // 500 s since boot, in nanoseconds

        assertEquals(
            wallNow - elapsedRealtime + 500_000L,
            SensorTime.wallClockUtcMillis(sensorNanos, wallNow, elapsedRealtime)
        )
    }

    /**
     * The sensor reports NANOSECONDS and the app stores MILLISECONDS: the
     * conversion must divide, not truncate to seconds nor keep nanoseconds.
     */
    @Test
    fun nanoseconds_areConvertedToMilliseconds() {
        assertEquals(
            wallNow - elapsedRealtime + 1_999L,
            SensorTime.wallClockUtcMillis(1_999_999_999L, wallNow, elapsedRealtime)
        )
    }

    /**
     * The point of using the sensing time: a reading delivered late must be
     * dated when it was taken, not when the callback ran. Two readings taken
     * 2 minutes apart are 2 minutes apart, regardless of when they were
     * processed.
     */
    @Test
    fun lateDelivery_doesNotShiftTheReadingTime() {
        val first = SensorTime.wallClockUtcMillis(10_000_000_000L, wallNow, elapsedRealtime)
        // Same elapsed timeline, but delivered 2 minutes later (the wall clock
        // and the elapsed clock moved together, as they do on a device).
        val second = SensorTime.wallClockUtcMillis(
            130_000_000_000L,
            wallNow + 120_000L,
            elapsedRealtime + 120_000L
        )

        assertEquals(120_000L, second - first)
    }
}
