package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the travel lifecycle and the recorded path.
 *
 * Responsibilities:
 * - keep at most one active travel (the "libretto" entry currently being built);
 * - record path points applying spatial/temporal filters;
 * - detect the end of a vacation automatically: when the user moves further
 *   than [TravelConfig.tripEndDistanceMeters] in a straight line from the
 *   trip's anchor point (its first recorded location), the active travel is
 *   closed and a new one opens.
 */
interface TravelRepository {

    /** Emits the currently active travel, or null when no travel is running. */
    fun observeActiveTravel(): Flow<TravelEntity?>

    /** Emits all travels (the travel journal), newest first. */
    fun observeTravels(): Flow<List<TravelEntity>>

    /** Emits the ordered path points of a travel, oldest first. */
    fun observePathPoints(travelId: Long): Flow<List<TravelPathPointEntity>>

    /**
     * Starts a new travel. If one is already active it returns its id instead,
     * preserving the single-active-travel invariant.
     *
     * @return The id of the active travel.
     */
    suspend fun startTravel(): Long

    /**
     * Closes the active travel, if any, stamping the current time as its end.
     *
     * @return true when travel is closed, false when none is active.
     */
    suspend fun stopTravel(): Boolean

    /**
     * Feeds a location sample into the path pipeline.
     *
     * How it works: ensures active travel exists (auto-starting one if
     * needed, timeline-style), then checks the straight-line distance from the
     * trip's anchor point. Leaving the vacation area (distance >=
     * [TravelConfig.tripEndDistanceMeters]) closes the current travel and opens
     * a new one anchored at the new location. Otherwise, the sample is filtered
     * with the spatial/temporal thresholds and, if meaningful, appended as a
     * path point.
     *
     * @param sample The provider-agnostic location reading to process.
     * @return [LocationRecordResult.Recorded] when stored, [LocationRecordResult.Skipped] otherwise.
     */
    suspend fun recordLocation(sample: LocationSample): LocationRecordResult
}
