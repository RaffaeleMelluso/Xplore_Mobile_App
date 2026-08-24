package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MapRepositoryImplTest {

    private fun poi(id: Long) = PoiEntity(
        id = id,
        lat = 45.0,
        lon = 7.0,
        title = "POI $id",
        description = null,
        category = "Museum",
        createdByUserId = null
    )

    /**
     * Verifica che il seed dei POI venga inserito solo quando la tabella è vuota.
     */
    @Test
    fun ensurePoiSeed_insertsWhenEmpty() = runTest {
        val poiDao = mock<PoiDao>()
        whenever(poiDao.count()).thenReturn(0)
        val seed = listOf(poi(0L), poi(0L))
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
            travelRepository = mock(),
            ghostSimulator = GhostSimulator(ghostCount = 2),
            poiSeed = seed
        )

        repository.ensurePoiSeed()

        verify(poiDao, times(seed.size)).insert(any())
    }

    /**
     * Verifica che il seed sia idempotente: se i POI ci sono già, non inserisce nulla.
     */
    @Test
    fun ensurePoiSeed_skipsWhenNotEmpty() = runTest {
        val poiDao = mock<PoiDao>()
        whenever(poiDao.count()).thenReturn(11)
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
            travelRepository = mock(),
            ghostSimulator = GhostSimulator(ghostCount = 2),
            poiSeed = listOf(poi(0L))
        )

        repository.ensurePoiSeed()

        verify(poiDao, never()).insert(any())
    }

    /**
     * Verifica che senza un viaggio attivo il flusso del percorso emetta una lista vuota.
     */
    @Test
    fun observeActivePath_emitsEmptyWhenNoActiveTravel() = runTest {
        val travelRepository = mock<TravelRepository>()
        whenever(travelRepository.observeActiveTravel()).thenReturn(flowOf(null))
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = mock(),
            travelRepository = travelRepository,
            ghostSimulator = GhostSimulator(ghostCount = 1)
        )

        val emitted = repository.observeActivePath().first()

        assertTrue(emitted.isEmpty())
    }

    /**
     * Verifica che con un viaggio attivo vengano emessi i suoi punti, ordinati come dal DAO.
     */
    @Test
    fun observeActivePath_emitsPathOfActiveTravel() = runTest {
        val travelRepository = mock<TravelRepository>()
        val travel = TravelEntity(
            id = 5L,
            startUtcMillis = 0L,
            endUtcMillis = null,
            travelTimeZoneId = "UTC",
            title = null
        )
        val points = listOf(
            TravelPathPointEntity(id = 1L, travelId = 5L, latitude = 45.0, longitude = 7.0, recordedAtUtcMillis = 0L),
            TravelPathPointEntity(id = 2L, travelId = 5L, latitude = 45.1, longitude = 7.1, recordedAtUtcMillis = 1_000L)
        )
        whenever(travelRepository.observeActiveTravel()).thenReturn(flowOf(travel))
        whenever(travelRepository.observePathPoints(5L)).thenReturn(flowOf(points))
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = mock(),
            travelRepository = travelRepository,
            ghostSimulator = GhostSimulator(ghostCount = 1)
        )

        val emitted = repository.observeActivePath().first()

        assertEquals(points, emitted)
    }

    /**
     * Verifica che i fantasmi vengano emessi anche prima del primo fix GPS,
     * ancorati alla posizione di default (Piazza Castello).
     */
    @Test
    fun observeGhosts_anchorsOnDefaultBeforeFirstFix() = runTest {
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = mock(),
            travelRepository = mock(),
            ghostSimulator = GhostSimulator(ghostCount = 2)
        )

        val ghosts = repository.observeGhosts().first()

        assertEquals(2, ghosts.size)
        ghosts.forEach { ghost ->
            val distance = GeoUtils.haversineMeters(45.0703, 7.6869, ghost.lat, ghost.lon)
            assertTrue("ghost too far from default anchor: $distance m", distance < 5_000)
        }
    }
}
