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
     * the previous reading is attributed to the current local day of the ACTIVE
     * travel and stored in `step_daily_summaries`, while `step_checkpoints`
     * keeps the last value so a process restart can resume without double
     * counting. Readings without an active travel are ignored (no trip, no
     * steps).
     *
     * @param cumulativeSteps the latest cumulative count from the sensor.
     */
    suspend fun onStepCount(cumulativeSteps: Long)

    /** Live total steps of the active travel (0 when none is ongoing). */
    fun observeActiveTravelSteps(): Flow<Long>

    /** Live total distance in meters of the active travel (0 when none is ongoing). */
    fun observeActiveTravelDistance(): Flow<Double>
}
