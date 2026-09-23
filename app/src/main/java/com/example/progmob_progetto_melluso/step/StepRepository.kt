package com.example.progmob_progetto_melluso.step

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the pedometer data.
 *
 * Responsibilities:
 * - consume the raw cumulative readings of the hardware step sensor and turn
 *   them into per-travel, per-day step/distance records (Room);
 * - expose the live totals of the ACTIVE travel, so the map info screen and
 *   the journal show the steps/distance while the trip is still running.
 *
 * The sensor layer ([com.example.progmob_progetto_melluso.service.TrackingService])
 * calls [onStepCount] from the foreground service, so the pedometer keeps
 * counting while the app is in the background or the screen is off (same
 * lifecycle as the location tracking).
 */
interface StepRepository {

    /**
     * Processes one raw cumulative reading of the step counter sensor.
     *
     * How it works: the sensor counts steps from device boot; the delta with
     * the previous reading is attributed to the local day the reading was TAKEN
     * and stored in `step_daily_summaries`, while `step_checkpoints` keeps the
     * last value so a process restart can resume without double counting.
     * Readings without an active travel are ignored (no trip, no steps): the
     * pedometer never creates a travel by itself.
     *
     * The reading time is passed in instead of being read here on purpose: the
     * hardware reports when the step happened (`SensorEvent.timestamp`, an
     * elapsed-time value converted to the wall clock by
     * [com.example.progmob_progetto_melluso.core.SensorTime]), which is not the
     * moment this method happens to run. Stamping with "now" would date the
     * steps late by however long the delivery took, and a reading that lands
     * after midnight would even be bucketed into the wrong day.
     *
     * @param cumulativeSteps the latest cumulative count from the sensor.
     * @param readingUtcMillis when the reading was taken, in UTC millis.
     */
    suspend fun onStepCount(cumulativeSteps: Long, readingUtcMillis: Long)

    /** Live total steps of the active travel (0 when none is ongoing). */
    fun observeActiveTravelSteps(): Flow<Long>

    /** Live pedometer distance in meters of the active travel (0 when none is ongoing). */
    fun observeActiveTravelDistance(): Flow<Double>
}
