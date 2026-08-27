package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.ghost.GhostFactory
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
     * Verifica che il seed inserisca i POI quando la tabella è vuota (nessun titolo presente).
     */
    @Test
    fun ensurePoiSeed_insertsWhenEmpty() = runTest {
        val poiDao = mock<PoiDao>()
        whenever(poiDao.countByTitle(any())).thenReturn(0)
        val seed = listOf(poi(1L), poi(2L))
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
                        travelCityDao = mock(),
            travelRepository = mock(),
            ghostSimulator = GhostFactory(),
            poiSeed = seed
        )

        repository.ensurePoiSeed()

        verify(poiDao, times(seed.size)).insert(any())
    }

    /**
     * Verifica che il seed sia idempotente: se i titoli sono già presenti, non inserisce nulla.
     */
    @Test
    fun ensurePoiSeed_skipsWhenAllTitlesPresent() = runTest {
        val poiDao = mock<PoiDao>()
        whenever(poiDao.countByTitle(any())).thenReturn(1)
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
                        travelCityDao = mock(),
            travelRepository = mock(),
            ghostSimulator = GhostFactory(),
            poiSeed = listOf(poi(1L))
        )

        repository.ensurePoiSeed()

        verify(poiDao, never()).insert(any())
    }

    /**
     * Verifica che un seed misto (es. nuovo set di una nuova città) inserisca
     * solo i POI mancanti, senza duplicare quelli già presenti.
     */
    @Test
    fun ensurePoiSeed_insertsOnlyMissingTitles() = runTest {
        val poiDao = mock<PoiDao>()
        // "POI 1" esiste già, "POI 2" manca: viene inserito solo quest'ultimo.
        whenever(poiDao.countByTitle(any())).thenReturn(1)
        whenever(poiDao.countByTitle("POI 2")).thenReturn(0)
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
                        travelCityDao = mock(),
            travelRepository = mock(),
            ghostSimulator = GhostFactory(),
            poiSeed = listOf(poi(1L), poi(2L))
        )

        repository.ensurePoiSeed()

        val captor = argumentCaptor<PoiEntity>()
        verify(poiDao, times(1)).insert(captor.capture())
        assertEquals("POI 2", captor.allValues.single().title)
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
                        travelCityDao = mock(),
            travelRepository = travelRepository,
            ghostSimulator = GhostFactory()
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
                        travelCityDao = mock(),
            travelRepository = travelRepository,
            ghostSimulator = GhostFactory()
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
        val poiDao = mock<PoiDao>()
        // The ghosts now consume the POI flow (real scores in their cards).
        whenever(poiDao.allPois()).thenReturn(flowOf(emptyList()))
        val repository = MapRepositoryImpl(
            locationFlow = emptyFlow(),
            poiDao = poiDao,
            travelCityDao = mock(),
            travelRepository = mock(),
            ghostSimulator = GhostFactory()
        )

        val ghosts = repository.observeGhosts().first()

        assertTrue("ghosts are emitted even before the first fix", ghosts.isNotEmpty())
        ghosts.forEach { ghost ->
            val distance = GeoUtils.haversineMeters(45.0703, 7.6869, ghost.marker.lat, ghost.marker.lon)
            assertTrue("ghost too far from default anchor: $distance m", distance < 10_000)
        }
    }
}
