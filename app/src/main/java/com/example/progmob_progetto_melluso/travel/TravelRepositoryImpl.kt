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
 * When a new sample is further than [TravelConfig.tripEndDistanceMeters] from
 * that anchor, the vacation is considered over, the active travel is closed
 * and a new one opens anchored at the new location.
 *
 * @param travelDao DAO for the travels table.
 * @param pathPointDao DAO for the recorded path points.
 * @param poiDao DAO for the POIs, used by the automatic visit detection.
 * @param travelPoiVisitDao DAO for the visits recorded in the journal.
 * @param config Recording, trip-end and POI-geofence thresholds.
 * @param timeZoneIdProvider Supplies the reference timezone stored on each
 *   travel, used for correct day bucketing when the user crosses time zones.
 * @param clock Supplies "now" in UTC millis; injectable for deterministic tests.
 */
class TravelRepositoryImpl(
    private val travelDao: TravelDao,
    private val pathPointDao: TravelPathPointDao,
    private val poiDao: PoiDao,
    private val travelPoiVisitDao: TravelPoiVisitDao,
    private val config: TravelConfig = TravelConfig(),
    private val timeZoneIdProvider: () -> String = { "UTC" },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : TravelRepository {

    // Location updates can arrive concurrently (e.g. from the foreground
    // service), and the segmenter state below is mutable: the mutex makes the
    // read-check-write steps in recordLocation() atomic.
    private val mutex = Mutex()

    // State valid only for the currently tracked travel.
    private var trackedTravelId: Long? = null
    private var anchorPoint: TravelPathPointEntity? = null
    private var lastPoint: TravelPathPointEntity? = null

    override fun observeActiveTravel(): Flow<TravelEntity?> = travelDao.activeTravel()

    override fun observeTravels(): Flow<List<TravelEntity>> = travelDao.allTravels()

    override fun observePathPoints(travelId: Long): Flow<List<TravelPathPointEntity>> =
        pathPointDao.pointsForTravel(travelId)

    override suspend fun startTravel(): Long = mutex.withLock {
        // Single-active-travel invariant: reuse the running travel if present.
        travelDao.activeTravelOnce()?.let { active ->
            loadState(active.id)
            return@withLock active.id
        }
        val id = createActiveTravel(clock()).id
        loadState(id)
        id
    }

    override suspend fun stopTravel(): Boolean = mutex.withLock {
        val active = travelDao.activeTravelOnce() ?: return@withLock false
        travelDao.update(active.copy(endUtcMillis = clock()))
        resetState()
        true
    }

    override suspend fun recordLocation(sample: LocationSample): LocationRecordResult =
        mutex.withLock {
            // 1. Ensure an active travel exists (auto-start, timeline-style).
            val activeTravel = activeTravelOrCreate(sample.recordedAtUtcMillis)
            if (trackedTravelId != activeTravel.id) loadState(activeTravel.id)

            // 2. Vacation-end detection: straight-line distance from the anchor.
            val distanceFromAnchor = anchorPoint?.let {
                GeoUtils.haversineMeters(it.latitude, it.longitude, sample.latitude, sample.longitude)
            } ?: 0.0

            if (anchorPoint != null && distanceFromAnchor >= config.tripEndDistanceMeters) {
                // The user left the vacation area: close the current trip and
                // open the next one anchored at this location.
                travelDao.update(activeTravel.copy(endUtcMillis = sample.recordedAtUtcMillis))
                val newTravel = createActiveTravel(sample.recordedAtUtcMillis)
                val point = newPathPoint(newTravel.id, sample)
                pathPointDao.insert(point)
                detectVisitsNear(newTravel.id, sample)

                trackedTravelId = newTravel.id
                anchorPoint = point
                lastPoint = point
                return@withLock LocationRecordResult.Recorded(newTravel.id, true)
            }

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
            //    point (spec §7.2): a point is stored only after BOTH a
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
            //    gets a visit in the travel journal (spec §7.3, automatic).
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

    /** Returns the active travel, creating one when none exists yet. */
    private suspend fun activeTravelOrCreate(startUtcMillis: Long): TravelEntity {
        travelDao.activeTravelOnce()?.let { return it }
        return createActiveTravel(startUtcMillis)
    }

    /** Creates a new active travel and returns the persisted entity. */
    private suspend fun createActiveTravel(startUtcMillis: Long): TravelEntity {
        val travel = TravelEntity(
            startUtcMillis = startUtcMillis,
            endUtcMillis = null,
            travelTimeZoneId = timeZoneIdProvider(),
            title = null
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
