package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelCityDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.ghost.GhostFactory
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * Room-backed implementation of [MapRepository].
 *
 * How it works: it composes the sensor stream (injected as [locationFlow] so
 * tests can substitute fake values), the travel path flow, the POI flow from
 * Room and the ghost simulator. The ghosts are re-anchored to the current user
 * position on every tick, so they move "around you" even when you are still.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapRepositoryImpl(
    private val locationFlow: Flow<LocationSample>,
    private val poiDao: PoiDao,
    private val travelCityDao: TravelCityDao,
    private val travelRepository: TravelRepository,
    private val ghostSimulator: GhostFactory,
    private val poiSeed: List<PoiEntity> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdProvider: () -> String = { "local-user" }
) : MapRepository {

    override fun observeCurrentLocation(): Flow<LocationSample?> =
        // Emit null before the first fix so the combined map state renders
        // (POIs, ghosts, base map) even when location is not available yet,
        // instead of waiting forever on the location stream.
        flow {
            emit(null)
            emitAll(locationFlow)
        }

    override fun observePois(): Flow<List<PoiEntity>> = poiDao.allPois()

    override fun observeActivePath(): Flow<List<TravelPathPointEntity>> =
        travelRepository.observeActiveTravel()
            .flatMapLatest { travel ->
                if (travel == null) {
                    flowOf(emptyList())
                } else {
                    travelRepository.observePathPoints(travel.id)
                }
            }
            .distinctUntilChanged()

    override fun observePathFor(travelId: Long): Flow<List<TravelPathPointEntity>> =
        travelRepository.observePathPoints(travelId)

    override fun observeTravels(): Flow<List<TravelEntity>> = travelRepository.observeTravels()

    override fun observeTravelCities(): Flow<List<TravelCityEntity>> =
        travelCityDao.allCitiesForUser(userIdProvider())

    override suspend fun stopTravel(): Boolean = travelRepository.stopTravel()

    override fun observeGhosts(): Flow<List<Ghost>> =
        combine(
            locationFlow.onStart { emit(DEFAULT_ANCHOR) },
            tickerFlow(),
            // The POIs feed the ghost cards: each visit shows the REAL POI id
            // and its live community score, so the ghosts display exactly the
            // votes the trends page aggregates (the local ghost simulation requirement, synced demo).
            poiDao.allPois()
        ) { location, _, pois ->
            ghostSimulator.tick(
                location.latitude,
                location.longitude,
                clock(),
                pois.associateBy { it.title }
            )
        }.distinctUntilChanged()

    override suspend fun ensurePoiSeed() {
        // inserts only the entries that are not present
        // yet, so new demo sets (for example a new city) are added on existing
        // installs without duplicating the POIs already stored.
        poiSeed.forEach { poi ->
            if (poiDao.countByTitle(poi.title) == 0) {
                poiDao.insert(poi)
            } else {
                // Heals demo POIs stored BEFORE the static-photo columns
                // existed: existing installs get the bundled demo photo too,
                // while the photo the user attached to their own POI (stored
                // in `imageUri`, never a drawable name) is not touched.
                poi.imageResName?.let { poiDao.updateSeedImageResName(poi.title, it) }
            }
        }
    }

    /**
     * Emits periodically so the ghosts keep moving even when the user is still.
     */
    private fun tickerFlow(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(GHOST_TICK_MILLIS)
        }
    }

    companion object {
        /** Fallback anchor (Piazza Castello, Turin) used before the first fix. */
        private val DEFAULT_ANCHOR = LocationSample(45.0703, 7.6869, 0L)

        /**
         * How often the simulated travelers are re-anchored around the user.
         * The ghosts are decoration around a position that the location fixes
         * already refresh, so a slower tick costs nothing in realism while the
         * map is open, and the tick does not run at all when the screen is not
         * collecting the state (`WhileSubscribed`).
         */
        private const val GHOST_TICK_MILLIS = 10_000L
    }
}
