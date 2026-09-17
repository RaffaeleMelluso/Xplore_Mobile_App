package com.example.progmob_progetto_melluso.step

import com.example.progmob_progetto_melluso.data.dao.StepCheckpointDao
import com.example.progmob_progetto_melluso.data.dao.StepDailySummaryDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Room-backed [StepRepository].
 *
 * How it works: the hardware step sensor reports a CUMULATIVE count since the
 * device booted. [onStepCount] computes the delta with the last value seen
 * (kept in memory, restored from the travel checkpoint after a restart) and
 * accumulates it into the `step_daily_summaries` row of the current local day
 * of the active travel. The distance is estimated from the steps with a fixed
 * average stride length. Readings are only stored while a trip is
 * active, so the totals always belong to a travel and follow the user scope
 * (the DAO totals are per travelId, the travel itself is per user).
 *
 * @param stepCheckpointDao DAO of `step_checkpoints` (resume point per travel).
 * @param stepDailySummaryDao DAO of `step_daily_summaries` (per-day totals).
 * @param travelDao DAO of `travels`, used to find the active travel.
 * @param timeZoneIdProvider supplies the reference timezone used to bucket the
 *   steps into local days (the same one stored on each travel).
 * @param clock supplies "now" in UTC millis; injectable for deterministic tests.
 * @param userIdProvider supplies the signed-in user id owning the active travel.
 * @param strideLengthMeters average step length used to estimate the distance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepRepositoryImpl(
    private val stepCheckpointDao: StepCheckpointDao,
    private val stepDailySummaryDao: StepDailySummaryDao,
    private val travelDao: TravelDao,
    private val timeZoneIdProvider: () -> String = { TimeZone.getDefault().id },
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdProvider: () -> String = { "local-user" },
    private val strideLengthMeters: Double = DEFAULT_STRIDE_LENGTH_METERS
) : StepRepository {

    // Last cumulative sensor value seen in this process. Kept in memory so the
    // delta is cheap; restored from the checkpoint row after a process restart.
    private var lastSensorValue: Long? = null

    // The travel to which [lastSensorValue] belongs. A sensor counter is global
    // to the device, but its delta must never cross a stopped/new travel.
    private var lastSensorTravelId: Long? = null

    // Sensor events are dispatched on the main thread and each is launched on
    // the IO pool: the read-modify-write on [lastSensorValue] must be atomic,
    // otherwise concurrent events could be double-counted or lost.
    private val mutex = Mutex()

    override suspend fun onStepCount(cumulativeSteps: Long) = mutex.withLock {
        if (cumulativeSteps <= 0) return@withLock

        val activeBeforeReading = activeTravelForCurrentUser()
        val previous = if (activeBeforeReading == null && lastSensorTravelId != null) {
            // The previous travel was stopped. Drop its baseline before reading
            // the global sensor again; otherwise the first reading of the new
            // travel would import the old trip's steps.
            lastSensorTravelId = null
            lastSensorValue = null
            null
        } else if (activeBeforeReading == null) {
            // No trip is active: the pedometer never opens one by itself, so
            // the reading is remembered only as a baseline (a trip started
            // later must not import steps walked before it began).
            lastSensorValue
        } else if (lastSensorTravelId == null) {
            // After a process restart, resume only from THIS travel's
            // checkpoint. A missing checkpoint means this is the first sensor
            // reading of that trip, so it becomes the baseline.
            lastSensorTravelId = activeBeforeReading.id
            stepCheckpointDao.checkpointForTravelOnce(activeBeforeReading.id)?.lastSensorValue
        } else if (lastSensorTravelId != activeBeforeReading.id) {
            // A different trip became active in this process. Its first reading
            // is a fresh baseline and must not import the previous trip's
            // cumulative sensor delta.
            lastSensorTravelId = activeBeforeReading.id
            null
        } else {
            lastSensorValue
        }

        // The counter restarts from zero when the device reboots: a lower value
        // means "reset", not "walked backwards". Re-baseline from the new count.
        if (previous != null && cumulativeSteps < previous) {
            lastSensorValue = cumulativeSteps
            return@withLock
        }

        val delta = previous?.let { cumulativeSteps - it } ?: 0L
        lastSensorValue = cumulativeSteps
        if (delta <= 0) return@withLock

        // Steps are recorded only into an ALREADY active travel: the trip
        // lifecycle is manual (journal button) or auto-started by the map
        // while the app is open — the pedometer never opens one by itself,
        // otherwise walking at home would silently start a vacation.
        val active = activeBeforeReading ?: return@withLock
        lastSensorTravelId = active.id
        saveCheckpoint(active.id, cumulativeSteps)

        // Accumulate into the local-day bucket of the active travel. On a day
        // change the delta is attributed to the new day: the summary of the
        // previous day stays untouched, and the trip total (SUM of the rows)
        // remains exact.
        val timeZoneId = timeZoneIdProvider()
        val now = clock()
        val localDate = localDateOf(now, timeZoneId)
        val existing = stepDailySummaryDao.summaryForTravelAndDateOnce(active.id, localDate, timeZoneId)
        stepDailySummaryDao.upsert(
            existing?.copy(
                steps = existing.steps + delta,
                distanceMeters = existing.distanceMeters + delta * strideLengthMeters,
                lastSensorValue = cumulativeSteps,
                updatedAtUtcMillis = now
            ) ?: StepDailySummaryEntity(
                travelId = active.id,
                localDate = localDate,
                timeZoneId = timeZoneId,
                steps = delta,
                distanceMeters = delta * strideLengthMeters,
                lastSensorValue = cumulativeSteps,
                updatedAtUtcMillis = now
            )
        )
    }

    override fun observeActiveTravelSteps(): Flow<Long> =
        activeTravelIdFlow().flatMapLatest { id ->
            if (id == null) flowOf(0L) else stepDailySummaryDao.totalStepsForTravel(id)
        }

    override fun observeActiveTravelDistance(): Flow<Double> =
        activeTravelIdFlow().flatMapLatest { id ->
            if (id == null) flowOf(0.0) else stepDailySummaryDao.totalDistanceForTravel(id)
        }

    /** Returns the active travel of the current user for one sensor reading. */
    private suspend fun activeTravelForCurrentUser(): TravelEntity? {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        return travelDao.activeTravelForUserOnce(userId)
    }

    /**
     * Emits the id of the active travel of the current user (null when none),
     * so the live totals re-anchor automatically when a trip starts/ends.
     */
    private fun activeTravelIdFlow(): Flow<Long?> = flow {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        emitAll(travelDao.activeTravelForUser(userId).map { it?.id })
    }

    /** Persists the resume point of the active travel (last raw sensor value). */
    private suspend fun saveCheckpoint(travelId: Long, value: Long) {
        val timeZoneId = timeZoneIdProvider()
        stepCheckpointDao.upsert(
            StepCheckpointEntity(
                travelId = travelId,
                lastSensorValue = value,
                lastCheckpointUtcMillis = clock(),
                lastKnownLocalDate = localDateOf(clock(), timeZoneId),
                lastKnownTimeZoneId = timeZoneId
            )
        )
    }

    /** Formats a UTC instant as the trip-local date (ISO yyyy-MM-dd). */
    private fun localDateOf(utcMillis: Long, timeZoneId: String): String {
        val zone = runCatching { TimeZone.getTimeZone(timeZoneId) }.getOrElse { TimeZone.getTimeZone("UTC") }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = zone }
            .format(Date(utcMillis))
    }

    companion object {
        /** Average step length used to estimate the walked distance. */
        private const val DEFAULT_STRIDE_LENGTH_METERS = 0.75
    }
}
