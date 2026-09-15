package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
 * @param travelDao DAO for the travels table.
 * @param pathPointDao DAO for the recorded path points.
 * @param poiDao DAO for the POIs, used by the automatic visit detection.
 * @param travelPoiVisitDao DAO for the visits recorded in the journal.
 * @param config Recording, trip-end and POI-geofence thresholds.
 * @param timeZoneIdProvider Supplies the reference timezone stored on each
 *   travel, used for correct day bucketing when the user crosses time zones.
 * @param clock Supplies "now" in UTC millis; injectable for deterministic tests.
 * @param userIdProvider Supplies the signed-in user id (Firebase uid) that
 *   owns the travels: every travel is stamped and every query is scoped to it,
 *   so switching account shows an empty journal instead of the previous
 *   user's data.
 */
class TravelRepositoryImpl(
    private val travelDao: TravelDao,
    private val pathPointDao: TravelPathPointDao,
    private val poiDao: PoiDao,
    private val travelPoiVisitDao: TravelPoiVisitDao,
    private val config: TravelConfig = TravelConfig(),
    private val timeZoneIdProvider: () -> String = { "UTC" },
    private val clock: () -> Long = { System.currentTimeMillis() },
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
            return@withLock active.id
        }
        val id = createActiveTravel(clock(), userId).id
        loadState(id)
        id
    }

    override suspend fun stopTravel(): Boolean = mutex.withLock {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        val active = travelDao.activeTravelForUserOnce(userId) ?: return@withLock false
        travelDao.update(active.copy(endUtcMillis = clock()))
        resetState()
        true
    }

    override suspend fun recordLocation(sample: LocationSample): LocationRecordResult =
        mutex.withLock {
            // 1. Ensure an active travel exists (auto-start, timeline-style).
            val activeTravel = activeTravelOrCreate(sample.recordedAtUtcMillis)
            if (trackedTravelId != activeTravel.id) loadState(activeTravel.id)

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

            // 5. Store the point; the first one becomes the trip anchor. Then
            //    check the POI geofence: every POI within the configured radius
            //    gets a visit in the travel journal (the POI proximity requirement, automatic).
            val point = newPathPoint(activeTravel.id, sample)
            pathPointDao.insert(point)
            detectVisitsNear(activeTravel.id, sample)
            if (anchorPoint == null) anchorPoint = point
            lastPoint = point

            LocationRecordResult.Recorded(activeTravel.id, false)
        }

    /**
     * Registers a visit for every POI within the geofencing radius of this
     * sample, keeping one visit per (travel, POI): re-passing near the same
     * POI never duplicates it. Runs only when a point is actually stored, so
     * it never fires on filtered GPS noise.
     */
    private suspend fun detectVisitsNear(travelId: Long, sample: LocationSample) {
        val pois = poiDao.allPoisOnce()
        if (pois.isEmpty()) return
        PoiProximity.near(sample.latitude, sample.longitude, pois, config.poiVisitRadiusMeters)
            .forEach { poi ->
                if (travelPoiVisitDao.visitForTravelAndPoi(travelId, poi.id) == null) {
                    travelPoiVisitDao.insert(
                        TravelPoiVisitEntity(
                            travelId = travelId,
                            poiId = poi.id,
                            visitedAtUtcMillis = sample.recordedAtUtcMillis
                        )
                    )
                }
            }
    }

    /** Builds a path point for [travelId] from a location sample. */
    private fun newPathPoint(travelId: Long, sample: LocationSample) = TravelPathPointEntity(
        travelId = travelId,
        latitude = sample.latitude,
        longitude = sample.longitude,
        recordedAtUtcMillis = sample.recordedAtUtcMillis,
        accuracyMeters = sample.accuracyMeters
    )

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
    }
}
