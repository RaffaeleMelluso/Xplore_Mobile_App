package com.example.progmob_progetto_melluso.journal

import android.net.Uri
import com.example.progmob_progetto_melluso.core.GeoUtils
import java.io.File
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelCityDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.dao.TravelMediaDao
import com.example.progmob_progetto_melluso.data.dao.TravelTransportDao
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity
import com.example.progmob_progetto_melluso.data.entities.TravelMediaEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.travel.PoiProximity
import com.example.progmob_progetto_melluso.travel.TravelCityDetector
import com.example.progmob_progetto_melluso.travel.TravelConfig
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TravelJournalRepository].
 *
 * How it works: it composes the Room flows (travels, photos, cities, visits)
 * into the [JournalTravel] list and the per-travel [TravelJournal] aggregate,
 * always through the repository layer so the UI/ViewModel never see the
 * storage. Mutations (edit, media, rename, visits) are one-shot DAO calls.
 * City detection is delegated to the pure [TravelCityDetector] and only
 * inserts destinations that are not already present, so a renamed city is
 * never duplicated. POI visits are detected with the pure [PoiProximity]
 * helper (live during recording + idempotent backfill here). Votes delegate
 * to the shared [PoiVoteRepository], so journal, map and trends apply the
 * same one-vote-per-user rule and the POI cached score stays in sync.
 *
 * @param travelDao DAO for the travels table.
 * @param travelMediaDao DAO for photos/videos.
 * @param travelCityDao DAO for detected destinations.
 * @param travelPoiVisitDao DAO for POI visits.
 * @param travelTransportDao DAO for the reported transport segments.
 * @param pathPointDao DAO for the recorded path.
 * @param poiDao DAO for the POIs (scores and backfill detection).
 * @param poiVoteRepository the shared vote engine (my votes + votePoi), so
 *   the journal votes with the SAME rules as the map and the trends.
 * @param travelRepository the travel-lifecycle repo (start/stop + active travel).
 * @param cityDetector pure detector that groups path points into destinations.
 * @param config recording/geofence thresholds (POI visit radius).
 * @param mediaFilesDir the app's filesDir, used to delete the private media
 *   copies when a photo/video is removed from the journal.
 * @param clock provides "now" in UTC millis; injectable for deterministic tests.
 * @param userIdProvider Supplies the signed-in user id (Firebase uid): the
 *   journal list is scoped to it, so switching account shows an empty journal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelJournalRepositoryImpl(
    private val travelDao: TravelDao,
    private val travelMediaDao: TravelMediaDao,
    private val travelCityDao: TravelCityDao,
    private val travelPoiVisitDao: TravelPoiVisitDao,
    private val travelTransportDao: TravelTransportDao,
    private val pathPointDao: TravelPathPointDao,
    private val poiDao: PoiDao,
    private val poiVoteRepository: PoiVoteRepository,
    private val travelRepository: TravelRepository,
    private val cityDetector: TravelCityDetector = TravelCityDetector(),
    private val config: TravelConfig = TravelConfig(),
    private val mediaFilesDir: File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdProvider: () -> String = { "local-user" }
) : TravelJournalRepository {

    override fun observeJournal(): Flow<List<JournalTravel>> = flow {
        val userId = userIdProvider()
        travelDao.claimLegacyTravels(userId)
        emitAll(
            travelDao.allTravelsForUser(userId).map { travels ->
                travels.map { t ->
                    JournalTravel(
                        travelId = t.id,
                        title = t.title,
                        startUtcMillis = t.startUtcMillis,
                        endUtcMillis = t.endUtcMillis,
                        description = t.description
                    )
                }
            }
        )
    }

    override fun observeTravelDetails(travelId: Long): Flow<TravelJournal?> =
        travelDao.travelById(travelId).flatMapLatest { travel ->
            if (travel == null) {
                flowOf(null)
            } else {
                // Destinations of this travel.
                val citiesUi = travelCityDao.citiesForTravel(travelId).map { cities ->
                    cities.map { c ->
                        JournalCity(
                            cityId = c.id,
                            travelId = c.travelId,
                            name = c.name,
                            lat = c.lat,
                            lon = c.lon,
                            arrivedAtUtcMillis = c.arrivedAtUtcMillis
                        )
                    }
                }

                // Media (photos/videos) of this travel.
                val mediaUi = travelMediaDao.mediaForTravel(travelId).map { photos ->
                    photos.map { p ->
                        JournalMedia(
                            id = p.id,
                            mediaType = p.mediaType,
                            imageUri = p.imageUri,
                            caption = p.caption,
                            capturedAtUtcMillis = p.capturedAtUtcMillis
                        )
                    }
                }

                // Visited POIs of this travel.
                val visitsUi = travelPoiVisitDao.visitViewsForTravel(travelId).map { list ->
                    list.map { v ->
                        JournalVisit(
                            id = v.id,
                            poiId = v.poiId,
                            poiTitle = v.poiTitle,
                            visitedAtUtcMillis = v.visitedAtUtcMillis,
                            note = v.note
                        )
                    }
                }

                // All POIs (for their cached score) and the user's own votes,
                // so the up/down state of every visit stays reactive.
                val poisUi = poiDao.allPois()
                val myVotesUi = poiVoteRepository.observeMyVotes()

                // The recorded path, drawn as the small route preview.
                val pathUi = pathPointDao.pointsForTravel(travelId).map { list ->
                    list.map { JournalPathPoint(it.latitude, it.longitude) }
                }

                // The transport segments reported during the trip.
                val transportsUi = travelTransportDao.transportsForTravel(travelId).map { list ->
                    list.map { t ->
                        JournalTransport(
                            id = t.id,
                            travelId = t.travelId,
                            mode = TransportMode.fromStorage(t.transportType),
                            lineName = t.lineName,
                            startUtcMillis = t.startUtcMillis
                        )
                    }
                }

                // combine() has typed overloads up to five flows, so the five
                // existing streams build the journal and the path + transports
                // are merged in a second (nested) combine.
                combine(
                    combine(mediaUi, citiesUi, visitsUi, poisUi, myVotesUi) { media, cities, visits, pois, myVotes ->
                        val scoreByPoi = pois.associateBy { it.id }
                        val myVoteByPoi = myVotes.associateBy { it.poiId }
                        TravelJournal(
                            travel = JournalTravel(
                                travelId = travel.id,
                                title = travel.title,
                                startUtcMillis = travel.startUtcMillis,
                                endUtcMillis = travel.endUtcMillis,
                                description = travel.description
                            ),
                            media = media,
                            cities = cities,
                            visits = visits.map { visit ->
                                visit.copy(
                                    score = scoreByPoi[visit.poiId]?.votes ?: 0,
                                    myVote = myVoteByPoi[visit.poiId]?.voteValue ?: 0
                                )
                            }
                        )
                    },
                    pathUi,
                    transportsUi
                ) { journal, path, transports ->
                    journal.copy(pathPoints = path, transports = transports)
                }
            }
        }

    override suspend fun startTravel(): Long = travelRepository.startTravel()

    override suspend fun stopTravel(): Boolean {
        val closed = travelRepository.stopTravel()
        // Finalize the trip just closed: detect its destinations and backfill
        // any POI visit that the live recording may have missed (e.g. the app
        // was off while passing near a POI).
        if (closed) {
            val userId = userIdProvider()
            travelDao.claimLegacyTravels(userId)
            travelDao.mostRecentClosedTravelOnceForUser(userId)?.let { travel ->
                detectCities(travel.id)
                detectVisits(travel.id)
            }
        }
        return closed
    }

    override suspend fun updateTravelDetails(travelId: Long, title: String?, description: String?) {
        val travel = travelDao.travelByIdOnce(travelId) ?: return
        travelDao.update(travel.copy(title = title, description = description))
    }

    override suspend fun addMedia(travelId: Long, uri: String, mediaType: String) {
        travelMediaDao.insert(
            TravelMediaEntity(
                travelId = travelId,
                mediaType = mediaType,
                imageUri = uri,
                caption = null,
                capturedAtUtcMillis = clock(),
                createdByUserId = null
            )
        )
    }

    override suspend fun addTransport(travelId: Long, mode: TransportMode, lineName: String?) {
        travelTransportDao.insert(
            TravelTransportEntity(
                travelId = travelId,
                transportType = mode.storageName,
                lineName = lineName?.trim()?.ifBlank { null },
                departureName = null,
                arrivalName = null,
                startUtcMillis = clock(),
                endUtcMillis = null
            )
        )
    }

    override suspend fun removeTransport(id: Long) {
        travelTransportDao.deleteById(id)
    }

    override suspend fun removeMedia(id: Long) {
        travelMediaDao.mediaById(id)?.let { photo ->
            travelMediaDao.delete(photo)
            deleteMediaFile(photo.imageUri)
        }
    }

    /**
     * Deletes the private copy of a photo/video, but only when it lives in the
     * app's media directory (never anything the user picked elsewhere).
     */
    private fun deleteMediaFile(imageUri: String) {
        val dir = mediaFilesDir ?: return
        if (!imageUri.startsWith("file://")) return
        val path = Uri.parse(imageUri).path ?: return
        val file = File(path)
        if (file.parentFile?.absolutePath == dir.absolutePath) {
            file.delete()
        }
    }

    override suspend fun renameCity(cityId: Long, name: String) {
        val city = travelCityDao.cityByIdOnce(cityId) ?: return
        travelCityDao.update(city.copy(name = name))
        // If the trip was auto-named after this city ("Trip to <city>"),
        // refresh the title so it follows the corrected city name.
        val travel = travelDao.travelByIdOnce(city.travelId) ?: return
        if (travel.title == AUTO_TITLE_PREFIX + city.name) {
            travelDao.update(travel.copy(title = AUTO_TITLE_PREFIX + name))
        }
    }

    /**
     * Detects the destinations visited in a travel from its path points and
     * persists the missing ones.
     *
     * How it works: reads the stored path points and the already persisted
     * destinations, then inserts only the clusters that are far enough from
     * every existing destination. Already-present cities are left untouched so
     * a rename is never lost. Safe to call on every open of a travel.
     */
    override suspend fun detectCities(travelId: Long) {
        val points = pathPointDao.pointsForTravelOnce(travelId)
        if (points.isEmpty()) return
        val existing = travelCityDao.citiesForTravelOnce(travelId)

        var counter = existing.size
        cityDetector.detect(points).forEach { detected ->
            val alreadyPresent = existing.any { e ->
                GeoUtils.haversineMeters(e.lat, e.lon, detected.lat, detected.lon) < CITY_MERGE_METERS
            }
            if (!alreadyPresent) {
                counter++
                travelCityDao.insert(
                    TravelCityEntity(
                        travelId = travelId,
                        name = "Destination $counter",
                        lat = detected.lat,
                        lon = detected.lon,
                        arrivedAtUtcMillis = detected.arrivedAtUtcMillis
                    )
                )
            }
        }

        // Auto-name the trip after its first visited city, unless the user
        // already chose a title: the journal and the map filter then show a
        // meaningful name instead of "Untitled trip".
        val travel = travelDao.travelByIdOnce(travelId) ?: return
        if (travel.title == null) {
            val firstCity = travelCityDao.citiesForTravelOnce(travelId).firstOrNull()
            if (firstCity != null) {
                travelDao.update(travel.copy(title = AUTO_TITLE_PREFIX + firstCity.name))
            }
        }
    }

    override suspend fun removeVisit(visitId: Long) {
        travelPoiVisitDao.visitByIdOnce(visitId)?.let { travelPoiVisitDao.delete(it) }
    }

    override suspend fun detectVisits(travelId: Long) {
        val points = pathPointDao.pointsForTravelOnce(travelId)
        if (points.isEmpty()) return
        val pois = poiDao.allPoisOnce()
        if (pois.isEmpty()) return
        points.forEach { point ->
            PoiProximity.near(point.latitude, point.longitude, pois, config.poiVisitRadiusMeters)
                .forEach { poi ->
                    if (travelPoiVisitDao.visitForTravelAndPoi(travelId, poi.id) == null) {
                        travelPoiVisitDao.insert(
                            TravelPoiVisitEntity(
                                travelId = travelId,
                                poiId = poi.id,
                                visitedAtUtcMillis = point.recordedAtUtcMillis
                            )
                        )
                    }
                }
        }
    }

    override suspend fun deleteTravel(travelId: Long) {
        val travel = travelDao.travelByIdOnce(travelId) ?: return
        // Collect the visited POIs BEFORE deleting: the votes given on them go
        // away together with the trip.
        val visitedPoiIds = travelPoiVisitDao.visitsForTravelOnce(travelId)
            .map { it.poiId }
            .distinct()
        if (travel.endUtcMillis == null) {
            // Close the active travel first so the single-active-travel
            // invariant holds and tracking does not append to a deleted row.
            travelRepository.stopTravel()
        }
        travelDao.deleteById(travelId)
        // A deleted trip takes the votes given on its POIs with it (spec §5:
        // the journal is the user's own data, votes disappear with the trip).
        visitedPoiIds.forEach { poiVoteRepository.removeVote(it) }
    }

    override suspend fun votePoi(poiId: Long, voteValue: Int) =
        // Shared vote engine: same rules as the map card and the trends page.
        poiVoteRepository.votePoi(poiId, voteValue)

    companion object {
        /** Two detected destinations closer than this are treated as one. */
        private const val CITY_MERGE_METERS = 1_000.0

        /** Auto-title pattern for trips named after their first visited city. */
        private const val AUTO_TITLE_PREFIX = "Trip to "
    }
}