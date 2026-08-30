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
 * - expose the staleness signals ([TravelStaleness], evaluated in the UI)
 *   that let the user CONFIRM the end of a vacation instead of having the
 *   app close the trip silently in the background.
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
     * needed, timeline-style), then filters the sample with the
     * spatial/temporal thresholds and, if meaningful, appends it as a path
     * point. The trip is never closed silently here: when the user is far
     * from the anchor or days passed, [TravelStaleness] flags the trip in the
     * UI layer and the user confirms the close.
     *
     * @param sample The provider-agnostic location reading to process.
     * @return [LocationRecordResult.Recorded] when stored, [LocationRecordResult.Skipped] otherwise.
     */
    suspend fun recordLocation(sample: LocationSample): LocationRecordResult
}
