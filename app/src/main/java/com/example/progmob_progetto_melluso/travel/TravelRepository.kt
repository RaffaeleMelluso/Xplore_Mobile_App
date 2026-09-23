package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the travel lifecycle and the recorded path.
 *
 * Responsibilities:
 * - keep at most one active travel (the "libretto" entry currently being built);
 * - record path points applying spatial/temporal filters, cleaning the
 *   impossible and the untrustworthy ones ([PathCleaning]);
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
     * Starting a trip ALSO opens its traveled-duration clock, so no caller has
     * to remember to do it: the timer counts from the start for the map button,
     * the journal button and the automatic start alike.
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
     * Opens the tracked-duration clock for the active travel (the tracking
     * service is running the trip). A no-op when no travel is active, and
     * idempotent for a trip that is already being tracked: an existing segment
     * keeps its start instant.
     */
    suspend fun startSession()

    /**
     * Closes the open segment started by [startSession], storing it so the
     * traveled duration keeps every second the trip was tracked. A no-op when
     * no segment is open.
     */
    suspend fun endSession()

    /**
     * Emits the TRAVELED duration of a travel in milliseconds: for an ongoing
     * trip, the sum of the stored blocks plus the segment that is running right
     * now (re-emitted every second, so the timer actually advances); for a
     * closed trip, the sum of its stored blocks, emitted once.
     *
     * It is the time the trip was TRACKED, not the raw wall clock (end minus
     * start), which would also count the hours the phone was not tracked at all
     * while the trip stayed open.
     *
     * The duration is measured on a monotonic clock and only STAMPED on the wall
     * clock: a correction of the system time cannot invent tracked time.
     */
    fun observeTraveledDuration(travelId: Long): Flow<Long>

    /**
     * Feeds a location sample into the path pipeline.
     *
     * How it works: records the sample into the active travel when one is
     * running; with [autoStart] enabled it also opens a new travel when none
     * exists (the "I'm moving with the app open" behavior). The sample is
     * then filtered with the spatial/temporal thresholds, dropped when it
     * implies an impossible speed (an outlier, not a movement) and, if
     * meaningful, cleaned and appended as a path point. The trip is never
     * closed silently here: when the user is far from the anchor or days
     * passed, [TravelStaleness] flags the trip in the UI layer and the user
     * confirms the close.
     *
     * @param sample The provider-agnostic location reading to process.
     * @param autoStart When true, a location fix with no active travel opens
     *   one (started at the fix time). When false, samples arriving with no
     *   active travel are dropped: a trip only exists if it was started
     *   explicitly (or by a previous auto-start while the app was open).
     * @return [LocationRecordResult.Recorded] when stored, [LocationRecordResult.Skipped] otherwise.
     */
    suspend fun recordLocation(sample: LocationSample, autoStart: Boolean): LocationRecordResult
}
