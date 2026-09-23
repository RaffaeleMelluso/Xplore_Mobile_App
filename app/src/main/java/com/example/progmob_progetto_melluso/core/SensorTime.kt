package com.example.progmob_progetto_melluso.core

import java.util.concurrent.TimeUnit

/**
 * Converts the hardware sensor timeline into the wall clock the app stores.
 *
 * Why it exists: `SensorEvent.timestamp` is NOT a date. It is the time elapsed
 * since the device booted, in NANOSECONDS, on a clock that keeps running
 * while the device sleeps but knows nothing about the real world. Stamping a
 * reading with "the moment the callback arrived" instead (`System.currentTime`
 * at event time) throws away the only exact information the sensor gives, and
 * it is exactly the shortcut the sensing lecture flags as not ideal: the
 * reading did not happen when the app got around to processing it, and a
 * delayed delivery (a busy main thread, a sleeping device) would date it
 * minutes late.
 *
 * How it works: the boot instant is reconstructed once per reading from the
 * two clocks that ARE available — the wall clock and the elapsed-realtime
 * clock differ by exactly the moment the device came up:
 *
 * `timePhoneWasLastRebooted = System.currentTimeMillis() - SystemClock.elapsedRealtime()`
 *
 * Adding the sensor's own elapsed time (converted from nanoseconds) to that
 * boot instant yields the UTC instant at which the reading was actually taken.
 *
 * The conversion is kept as a pure function, so it is unit-testable on the JVM
 * without a device and without a sensor: the service passes it the three
 * numbers the platform gives it.
 */
object SensorTime {

    /**
     * Reconstructs the UTC instant of a sensor reading.
     *
     * @param sensorNanos the raw `SensorEvent.timestamp` (nanoseconds since boot).
     * @param wallClockNowMillis `System.currentTimeMillis()` at delivery time.
     * @param elapsedRealtimeMillis `SystemClock.elapsedRealtime()` at delivery
     *   time; together with [wallClockNowMillis] it pins the boot instant.
     * @return the reading time in UTC millis (the same unit the app stores in Room).
     */
    fun wallClockUtcMillis(
        sensorNanos: Long,
        wallClockNowMillis: Long,
        elapsedRealtimeMillis: Long
    ): Long = wallClockNowMillis - elapsedRealtimeMillis + TimeUnit.NANOSECONDS.toMillis(sensorNanos)
}
