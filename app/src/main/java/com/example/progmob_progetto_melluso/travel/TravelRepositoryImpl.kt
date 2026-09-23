package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.core.MonotonicTime
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.dao.TravelSessionBlockDao
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.data.entities.TravelSessionBlockEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed [TravelRepository] implementation.
 *
 * It owns the per-travel state (anchor point + last recorded point) in memory
 * and rebuilds it lazily from the stored path points whenever the tracked
 * travel changes, so the running state stays correct across repository
 * recreation. A [Mutex] serializes concurrent location updates.
 *
 * Vacation-end detection: the trip's anchor is its first recorded location.
 * The trip is NOT closed silently here anymore: when the user reopens the app
 * somewhere else (or days later), [TravelStaleness] detects the stale trip in
 * the UI layer and the user is ASKED whether the vacation is still ongoing
 * before the trip is closed (the travel lifecycle requirement, confirmed close instead of silent).
 *
 * Trip start: manual by default (the map / journal buttons), or automatic
 * while the app is open AND the user actually moves — [recordLocation] with
 * [autoStart]=true opens a trip, but the tracking service sets that flag only
 * after [AutoStartGate] saw a real displacement between two fixes (a noise
 * buffer of a few tens of meters keeps sensor jitter from opening trips while
 * sitting still). Background samples are dropped when no trip is active, so a
 * trip is never opened from the background without the user asking for it.
 *
 * Traveled duration: the clock of a trip follows the TRACKING, not the app
 * visibility. It opens when the trip starts ([startTravel] and the auto-start
 * path) and when the tracking service starts, and closes when the trip is
 * stopped or tracking is torn down. The duration therefore grows while the
 * phone keeps tracking the user with the app in the background (pocket mode)
 * and stops when tracking stops: it is the time the trip was actually
 * tracked, which is what the journal, the map and the trip timer show.
 *
 * The duration is MEASURED on a monotonic clock ([monotonicMillis], time since
 * boot) while it is STAMPED on the wall clock ([clock]): a stored block needs
 * wall-clock coordinates to stay meaningful across reboots, but measuring with
 * the wall clock would let an NTP correction or a manual time change credit the
 * trip with time it never tracked.
 *
 * Path cleaning: the recorded path is cleaned before each write. A fix
 * implying an impossible speed is dropped (a GPS teleport) and a fix whose
 * reported error makes it barely informative is blended toward the last
 * recorded point, so the path keeps the shape of the movement instead of the
 * shape of the noise ([PathCleaning]).
 *
 * @param travelDao DAO for the travels table.
 * @param pathPointDao DAO for the recorded path points.
 * @param poiDao DAO for the POIs, used by the automatic visit detection.
 * @param travelPoiVisitDao DAO for the visits recorded in the journal.
 * @param config Recording, trip-end and POI-geofence thresholds.
 * @param timeZoneIdProvider Supplies the reference timezone stored on each
 *   travel, used for correct day bucketing when the user crosses time zones.
 * @param clock Supplies "now" in UTC millis; injectable for deterministic tests.
 * @param monotonicMillis Supplies a monotonic millisecond counter (time since
 *   boot) used to measure durations; injectable for deterministic tests.
 * @param userIdProvider Supplies the signed-in user id (Firebase uid) that
 *   owns the travels: every travel is stamped and every query is scoped to it,
 *   so switching account shows an empty journal instead of the previous
 *   user's data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelRepositoryImpl(
    private val travelDao: TravelDao,
    private val pathPointDao: TravelPathPointDao,
    private val poiDao: PoiDao,
    private val travelPoiVisitDao: TravelPoiVisitDao,
    private val sessionBlockDao: TravelSessionBlockDao,
    private val config: TravelConfig = TravelConfig(),
    private val timeZoneIdProvider: () -> String = { "UTC" },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val monotonicMillis: () -> Long = { MonotonicTime.elapsedMillis() },
    private val userIdProvider: () -> String = { "local-user" }
) : TravelRepository {

    // Location updates can arrive concurrently (e.g. from the foreground
    // service), and the segmenter state below is mutable: the mutex makes the
    // read-check-write steps in recordLocation() atomic.
    private val mutex = Mutex()

    // State valid only for the currently tracked travel.
    private var trackedTravelId: Long? = null
    private var anchorPoint: TravelPathPointEntity? = null
    private var lastPoint: TravelPathPointEntity? = null

    /**
     * Milliseconds the tracked clock has been running; 0 when it is closed.
     *
     * Backed by the companion [sessionStartUtcMillis] / [sessionTravelId]:
     * AppContainer builds a NEW TravelRepositoryImpl per call (the sensor
     * layer and the UI each get their own instance), so the clock must be
     * per-PROCESS. Otherwise the service's [endSession] could never close the
     * segment the map's [startSession] opened and the timer would freeze.
     *
     * The value is MEASURED on the monotonic clock: it is the real time the
     * tracking has been running, immune to wall-clock jumps. The wall-clock
     * start is kept only to stamp the stored block, and it is also the
     * fallback when the monotonic start is missing (a state written by a
     * different code path), so the timer can never be reported as negative or
     * as zero simply because one of the two starts is absent.
     */
    private fun openSessionMillis(): Long {
        val startedAtWall = sessionStartUtcMillis ?: return 0L
        val startedAtMonotonic = sessionStartElapsedMillis
            ?: return maxOf(0L, clock() - startedAtWall)
        return maxOf(0L, monotonicMillis() - startedAtMonotonic)
    }

    /**
     * Opens the tracked clock on [travelId].
     *
     * Idempotent: when the clock is already running for THIS trip its start
     * instant is kept, so a second call (the app reopening, the service
     * restarting, a manual start on a trip that is already running) can never
     * throw away the time already counted.
     *
     * Both starts are recorded: the wall-clock instant (to stamp the stored
     * block) and the monotonic instant (to measure its length).
     */
    private fun openSession(travelId: Long) {
        if (sessionTravelId == travelId && sessionStartUtcMillis != null) return
        sessionTravelId = travelId
        sessionStartUtcMillis = clock()
        sessionStartElapsedMillis = monotonicMillis()
    }

    /** Closes the tracked clock; the open segment is stored by the caller. */
    private fun closeSession() {
        sessionTravelId = null
        sessionStartUtcMillis = null
        sessionStartElapsedMillis = null
    }

    override fun observeActiveTravel(): Flow<TravelEntity?> = flow {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        emitAll(travelDao.activeTravelForUser(userId))
    }

    override fun observeTravels(): Flow<List<TravelEntity>> = flow {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        emitAll(travelDao.allTravelsForUser(userId))
    }

    override fun observePathPoints(travelId: Long): Flow<List<TravelPathPointEntity>> =
        pathPointDao.pointsForTravel(travelId)

    override suspend fun startTravel(): Long = mutex.withLock {
        // Single-active-travel invariant: reuse the running travel if present.
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        travelDao.activeTravelForUserOnce(userId)?.let { active ->
            loadState(active.id)
            // Already running: make sure the clock is open, but do NOT restart
            // an existing segment (openSession is idempotent).
            openSession(active.id)
            return@withLock active.id
        }
        val id = createActiveTravel(clock(), userId).id
        loadState(id)
        // The tracked clock starts together with the trip, whichever caller
        // started it (map button, journal button): the timer counts from here
        // and no caller has to remember to open the session itself.
        openSession(id)
        id
    }

    /**
     * Stores one tracked window, but only while [travelId] is STILL the
     * active travel: a block arriving after the stop (or belonging to an old
     * trip) must never extend a closed trip.
     */
    private suspend fun recordSessionBlock(travelId: Long, fromUtcMillis: Long, toUtcMillis: Long) {
        if (toUtcMillis <= fromUtcMillis) return
        mutex.withLock {
            // Only the travel that is currently running can accumulate open-
            // app time: a block arriving after the stop is stale and must not
            // extend a closed trip.
            val active = travelDao.activeTravelForUserOnce(userIdProvider()) ?: return@withLock
            if (active.id != travelId) return@withLock
            sessionBlockDao.insert(
                TravelSessionBlockEntity(
                    travelId = travelId,
                    fromUtcMillis = fromUtcMillis,
                    toUtcMillis = toUtcMillis
                )
            )
        }
    }

    /**
     * Emits the traveled duration of [travelId]: the stored tracking blocks
     * plus the clock segment that is open right now.
     *
     * Why the ticker: an ONGOING trip's duration grows with the clock, but
     * nothing writes the travels or the session-block table while it runs, so
     * a plain `combine` of the two Room flows emitted once and then stayed
     * frozen (the "timer never moves" bug). The ongoing value is therefore
     * recomputed every [DURATION_TICK_MILLIS] for as long as it is observed.
     * A CLOSED trip emits a single value and completes, so a finished trip
     * never keeps a timer alive.
     */
    override fun observeTraveledDuration(travelId: Long): Flow<Long> =
        combine(
            travelDao.travelById(travelId),
            sessionBlockDao.blocksForTravel(travelId)
        ) { travel, blocks -> travel to blocks }
            .flatMapLatest { (travel, blocks) ->
                val storedMillis = blocks.sumOf { it.toUtcMillis - it.fromUtcMillis }
                when {
                    // No travel (yet): nothing to time.
                    travel == null -> flowOf(0L)
                    // Closed trip: every segment is stored, so the sum IS the
                    // time the trip was tracked.
                    travel.endUtcMillis != null -> flowOf(storedMillis)
                    else -> flow {
                        while (true) {
                            emit(storedMillis + openSessionMillis())
                            delay(DURATION_TICK_MILLIS)
                        }
                    }
                }
            }

    override suspend fun endSession() {
        // The clock knows the trip it belongs to, so any instance can close a
        // segment another instance opened (the service, the map and the
        // journal each hold their own repository).
        val startedAt = sessionStartUtcMillis ?: return
        val travelId = sessionTravelId ?: return
        // The end instant is the start PLUS the time really measured on the
        // monotonic clock: the block keeps wall-clock coordinates (so it stays
        // meaningful after a reboot) while its LENGTH cannot be distorted by a
        // wall-clock jump.
        recordSessionBlock(travelId, startedAt, startedAt + openSessionMillis())
        // Closing STORES the segment: the duration stops growing while nothing
        // tracks the user. [startSession] opens a new one when tracking resumes.
        closeSession()
    }

    override suspend fun stopTravel(): Boolean = mutex.withLock {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        val active = travelDao.activeTravelForUserOnce(userId) ?: return@withLock false
        // The tracked clock ends together with the trip: its tail is stored
        // BEFORE the end stamp, so the closed trip keeps every second it was
        // actually tracked.
        val startedAt = sessionStartUtcMillis
        val endedAt = clock()
        val clockBelongsToThisTrip = sessionTravelId == null || sessionTravelId == active.id
        // Same monotonic measurement as endSession: the tail of the clock is
        // stored with its real length, stamped in wall-clock coordinates.
        val trackedEnd = startedAt?.let { it + openSessionMillis() }
        if (startedAt != null && trackedEnd != null && trackedEnd > startedAt && clockBelongsToThisTrip) {
            sessionBlockDao.insert(
                TravelSessionBlockEntity(
                    travelId = active.id,
                    fromUtcMillis = startedAt,
                    toUtcMillis = trackedEnd
                )
            )
        }
        closeSession()
        travelDao.update(active.copy(endUtcMillis = endedAt))
        resetState()
        true
    }

    override suspend fun recordLocation(
        sample: LocationSample,
        autoStart: Boolean
    ): LocationRecordResult =
        mutex.withLock {
            // 1. Find the active travel. Without an explicit request the trip
            //    only exists when the user (or a previous auto-start while the
            //    app was open) started it: a GPS fix arriving with no active
            //    travel is dropped instead of silently opening a trip.
            val activeTravel = if (autoStart) {
                activeTravelOrCreate(sample.recordedAtUtcMillis)
            } else {
                travelDao.activeTravelForUserOnce(userIdProvider())
                    ?: return@withLock LocationRecordResult.Skipped
            }
            if (trackedTravelId != activeTravel.id) loadState(activeTravel.id)
            // A trip that opens here (auto-start) begins its tracked clock
            // right away: autoStart is true only while the app is open AND the
            // user really moved, so this is exactly the start of a fresh trip.
            if (autoStart) openSession(activeTravel.id)

            // NOTE: no silent vacation-end close here anymore. The staleness
            // signal (distance from the anchor OR trip age) is evaluated in the
            // UI layer ([TravelStaleness]) and the user is ASKED before the
            // trip is closed: the app must not end a vacation behind the user's
            // back while it runs in the background. Points are still recorded
            // normally; the path segmenter keeps the map drawing sane.

            // 3. Accuracy gate relative to the previous point: a fix whose
            //    reported error exceeds maxAccuracyMeters is mostly GPS noise
            //    (typical while standing still) and would draw zigzag
            //    artifacts on the path. The first fix is always accepted,
            //    otherwise a cold GPS could never create the anchor point.
            val accuracy = sample.accuracyMeters?.toDouble() ?: 0.0
            if (lastPoint != null && accuracy > config.maxAccuracyMeters) {
                return@withLock LocationRecordResult.Skipped
            }

            // 4. Spatial AND temporal filters relative to the last recorded
            //    point (the path-recording requirement): a point is stored only after BOTH a
            //    meaningful movement and a minimum time interval. The spatial
            //    threshold grows with the average accuracy error of the two
            //    fixes, so jitter smaller than the GPS uncertainty never
            //    pollutes the path. The two thresholds are combined with AND:
            //    an OR would re-record stationary GPS drift after the temporal
            //    threshold alone (the old "imprecise path while standing
            //    still" bug).
            val distanceFromLast = lastPoint?.let {
                GeoUtils.haversineMeters(it.latitude, it.longitude, sample.latitude, sample.longitude)
            } ?: 0.0
            val elapsedFromLast = lastPoint?.let {
                sample.recordedAtUtcMillis - it.recordedAtUtcMillis
            } ?: Long.MAX_VALUE
            val accuracyBuffer =
                (accuracy + (lastPoint?.accuracyMeters ?: 0f).toDouble()) / 2.0
            val spatialThreshold = maxOf(config.minDistanceMeters, accuracyBuffer)

            val shouldRecord = lastPoint == null ||
                (distanceFromLast >= spatialThreshold && elapsedFromLast >= config.minElapsedMillis)
            if (!shouldRecord) return@withLock LocationRecordResult.Skipped

            // 5. Outlier gate, the last defence before writing: a fix implying a
            //    movement no traveler could have made is a GPS "teleport" (or a
            //    stale fix delivered late with a fresh timestamp), not a
            //    movement. One such point would draw a straight line across the
            //    map and spoil the shape of the whole trip, and no later
            //    cleaning could undo it.
            if (lastPoint != null &&
                !PathCleaning.isPlausibleMovement(
                    distanceMeters = distanceFromLast,
                    elapsedMillis = elapsedFromLast,
                    maxSpeedMetersPerSecond = config.maxPlausibleSpeedKmh / KMH_TO_METERS_PER_SECOND
                )
            ) {
                return@withLock LocationRecordResult.Skipped
            }

            // 6. Clean the point, then store it; the first one becomes the trip
            //    anchor. A fix whose reported error makes it barely informative
            //    is blended toward the last recorded point, so the path follows
            //    the movement instead of the noise. The STORED coordinates are
            //    what the state keeps, so consecutive points are smoothed
            //    against each other instead of against the raw stream.
            val cleaned = lastPoint?.let { previous ->
                PathCleaning.smooth(
                    previousLatitude = previous.latitude,
                    previousLongitude = previous.longitude,
                    nextLatitude = sample.latitude,
                    nextLongitude = sample.longitude,
                    nextAccuracyMeters = accuracy,
                    referenceMeters = config.pathSmoothingReferenceMeters
                )
            } ?: (sample.latitude to sample.longitude)
            val point = newPathPoint(activeTravel.id, sample, cleaned.first, cleaned.second)
            pathPointDao.insert(point)
            // The POI geofence is evaluated on the CLEANED position too: the
            // journal visit and the stored path must never disagree about where
            // the user was.
            detectVisitsNear(activeTravel.id, sample.recordedAtUtcMillis, cleaned.first, cleaned.second)
            if (anchorPoint == null) anchorPoint = point
            lastPoint = point

            LocationRecordResult.Recorded(activeTravel.id, false)
        }

    /**
     * Registers a visit for every POI within the geofencing radius of a
     * recorded position, keeping one visit per (travel, POI): re-passing near
     * the same POI never duplicates it. Runs only when a point is actually
     * stored, so it never fires on filtered GPS noise.
     *
     * @param latitude latitude to test, i.e. the CLEANED one that was stored.
     * @param longitude longitude to test, i.e. the CLEANED one that was stored.
     */
    private suspend fun detectVisitsNear(
        travelId: Long,
        visitedAtUtcMillis: Long,
        latitude: Double,
        longitude: Double
    ) {
        val pois = poiDao.allPoisOnce()
        if (pois.isEmpty()) return
        PoiProximity.near(latitude, longitude, pois, config.poiVisitRadiusMeters)
            .forEach { poi ->
                if (travelPoiVisitDao.visitForTravelAndPoi(travelId, poi.id) == null) {
                    travelPoiVisitDao.insert(
                        TravelPoiVisitEntity(
                            travelId = travelId,
                            poiId = poi.id,
                            visitedAtUtcMillis = visitedAtUtcMillis
                        )
                    )
                }
            }
    }

    /**
     * Builds a path point for [travelId] from a location sample.
     *
     * The coordinates are passed in rather than taken from the sample because
     * the point that is stored is the CLEANED one ([PathCleaning.smooth]);
     * everything else (when it was taken, how precise it was) is the sample's.
     */
    private fun newPathPoint(
        travelId: Long,
        sample: LocationSample,
        latitude: Double,
        longitude: Double
    ) = TravelPathPointEntity(
        travelId = travelId,
        latitude = latitude,
        longitude = longitude,
        recordedAtUtcMillis = sample.recordedAtUtcMillis,
        accuracyMeters = sample.accuracyMeters
    )

    override suspend fun startSession() {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        val active = travelDao.activeTravelForUserOnce(userId) ?: return
        loadState(active.id)
        // Idempotent: a trip that is already being tracked keeps its start, so
        // re-opening the app (or restarting the service) never truncates the
        // timer.
        openSession(active.id)
    }

    /** Returns the active travel of the current user, creating one when none exists. */
    private suspend fun activeTravelOrCreate(startUtcMillis: Long): TravelEntity {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        travelDao.activeTravelForUserOnce(userId)?.let { return it }
        return createActiveTravel(startUtcMillis, userId)
    }

    /** Creates a new active travel owned by [userId] and returns the entity. */
    private suspend fun createActiveTravel(startUtcMillis: Long, userId: String): TravelEntity {
        val travel = TravelEntity(
            startUtcMillis = startUtcMillis,
            endUtcMillis = null,
            travelTimeZoneId = timeZoneIdProvider(),
            title = null,
            userId = userId
        )
        return travel.copy(id = travelDao.insert(travel))
    }

    /**
     * Rebuilds [anchorPoint] and [lastPoint] from the stored path points of
     * [travelId] (anchor = first point, last = most recent point). Called
     * lazily when the tracked travel changes so the in-memory state survives
     * process restarts and repository recreation.
     */
    private suspend fun loadState(travelId: Long) {
        val points = pathPointDao.pointsForTravelOnce(travelId)
        anchorPoint = points.firstOrNull()
        lastPoint = points.lastOrNull()
        trackedTravelId = travelId
    }

    private fun resetState() {
        trackedTravelId = null
        anchorPoint = null
        lastPoint = null
        closeSession()
    }

    companion object {
        /**
         * Per-process tracked clock (see the class docs): the instant the open
         * segment started and the trip it belongs to. Kept here because
         * AppContainer hands out a new repository instance per caller, so an
         * instance-level clock could never be closed from another instance.
         */
        @Volatile
        private var sessionStartUtcMillis: Long? = null

        @Volatile
        private var sessionTravelId: Long? = null

        /**
         * Monotonic instant the open segment started (time since boot), used to
         * MEASURE its length. Null when no segment is open. Kept per-process
         * next to the wall-clock start, for the same reason.
         */
        @Volatile
        private var sessionStartElapsedMillis: Long? = null

        /**
         * How often an ongoing trip re-emits its duration: the resolution the
         * timer displays. The tick runs only while an ongoing trip is being
         * observed (see [observeTraveledDuration]).
         */
        private const val DURATION_TICK_MILLIS = 1_000L

        /** Kilometres per hour to meters per second, used by the outlier gate. */
        private const val KMH_TO_METERS_PER_SECOND = 3.6
    }
}
