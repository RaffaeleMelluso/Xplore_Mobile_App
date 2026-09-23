package com.example.progmob_progetto_melluso.core

import android.os.SystemClock

/**
 * Monotonic "time since boot" source, used to MEASURE durations.
 *
 * Why it exists: durations must not be measured on the wall clock. The wall
 * clock is the right unit to STAMP an instant (a path point, the start of a
 * trip, a stored block: all of them must stay meaningful across reboots and be
 * comparable with the times other devices report), but it is the wrong unit to
 * MEASURE how long something took: it can jump forward (an NTP correction, a
 * user setting the time, a timezone/network update) and the app would then
 * credit a trip with hours it never tracked. The sensing lecture makes the same
 * distinction from the other side, where it converts a sensor's elapsed time
 * into a wall-clock instant: elapsed time is what measures, the wall clock is
 * what records.
 *
 * How it works: [elapsedMillis] forwards to the platform's monotonic clock,
 * which only ever moves forward and keeps counting while the device sleeps.
 * It is kept behind this object so the repositories can depend on a plain
 * `() -> Long` (injectable in tests) without importing Android themselves.
 *
 * The fallback covers JVM unit tests, where the Android framework is a stub
 * that throws on call: there the wall clock is the only clock available, and a
 * test that cares about durations injects its own provider anyway.
 */
object MonotonicTime {

    /**
     * Milliseconds elapsed since the device booted (monotonic).
     *
     * @return a value that never decreases while the process lives.
     */
    fun elapsedMillis(): Long = runCatching { SystemClock.elapsedRealtime() }
        .getOrDefault(System.currentTimeMillis())
}
