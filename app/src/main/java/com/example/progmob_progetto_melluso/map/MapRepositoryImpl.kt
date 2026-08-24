package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
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
    private val travelRepository: TravelRepository,
    private val ghostSimulator: GhostSimulator,
    private val poiSeed: List<PoiEntity> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis
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

    override fun observeGhosts(): Flow<List<GhostEntity>> =
        combine(
            locationFlow.onStart { emit(DEFAULT_ANCHOR) },
            tickerFlow()
        ) { location, _ ->
            ghostSimulator.tick(location.latitude, location.longitude, clock())
        }.distinctUntilChanged()

    override suspend fun ensurePoiSeed() {
        // Idempotent per title: inserts only the entries that are not present
        // yet, so new demo sets (for example a new city) are added on existing
        // installs without duplicating the POIs already stored.
        poiSeed.forEach { poi ->
            if (poiDao.countByTitle(poi.title) == 0) {
                poiDao.insert(poi)
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
        private const val GHOST_TICK_MILLIS = 5_000L
    }
}
