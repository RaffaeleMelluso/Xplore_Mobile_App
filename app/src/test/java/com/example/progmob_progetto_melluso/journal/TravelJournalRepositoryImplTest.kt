package com.example.progmob_progetto_melluso.journal

import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelCityDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the travel journal mutations, focused on the delete flow: a
 * deleted trip must take the user's votes on its visited POIs with it, and an
 * active trip must be closed first (single-active-travel invariant). Also
 * covers the auto-title: a titleless trip gets "Trip N" (the trip-name fix).
 */
class TravelJournalRepositoryImplTest {

    private fun journalRepository(
        travelDao: TravelDao,
        travelPoiVisitDao: TravelPoiVisitDao,
        poiVoteRepository: PoiVoteRepository,
        travelRepository: TravelRepository,
        travelCityDao: TravelCityDao = mock(),
        pathPointDao: TravelPathPointDao = mock()
    ) = TravelJournalRepositoryImpl(
        travelDao = travelDao,
        travelMediaDao = mock(),
        travelCityDao = travelCityDao,
        travelPoiVisitDao = travelPoiVisitDao,
        stepDailySummaryDao = mock(),
        travelTransportDao = mock(),
        pathPointDao = pathPointDao,
        poiDao = mock(),
        poiVoteRepository = poiVoteRepository,
        travelRepository = travelRepository
    )

    /**
     * Verifica che eliminando un viaggio vengano rimossi anche i voti dati sui
     * POI visitati in quel viaggio (deduplicati), senza toccare gli altri POI.
     */
    @Test
    fun deleteTravel_removesVotesOnVisitedPois() = runTest {
        val travelDao = mock<TravelDao>()
        val travelPoiVisitDao = mock<TravelPoiVisitDao>()
        val poiVoteRepository = mock<PoiVoteRepository>()
        val travelRepository = mock<TravelRepository>()
        val closed = TravelEntity(
            id = 5L,
            startUtcMillis = 0L,
            endUtcMillis = 1_000L,
            travelTimeZoneId = "UTC",
            title = null
        )
        whenever(travelDao.travelByIdOnce(5L)).thenReturn(closed)
        // The POI 10 was visited twice in the same trip (duplicate rows from
        // older builds): its vote is removed exactly once.
        whenever(travelPoiVisitDao.visitsForTravelOnce(5L)).thenReturn(
            listOf(
                TravelPoiVisitEntity(id = 1L, travelId = 5L, poiId = 10L, visitedAtUtcMillis = 1L),
                TravelPoiVisitEntity(id = 2L, travelId = 5L, poiId = 10L, visitedAtUtcMillis = 2L),
                TravelPoiVisitEntity(id = 3L, travelId = 5L, poiId = 11L, visitedAtUtcMillis = 3L)
            )
        )

        journalRepository(travelDao, travelPoiVisitDao, poiVoteRepository, travelRepository)
            .deleteTravel(5L)

        verify(travelDao).deleteById(5L)
        // The trip was already closed: no stop needed.
        verify(travelRepository, never()).stopTravel()
        verify(poiVoteRepository, times(2)).removeVote(any())
        verify(poiVoteRepository).removeVote(10L)
        verify(poiVoteRepository).removeVote(11L)
    }

    /**
     * Verifica che l'eliminazione di un viaggio ANCORA attivo lo chiuda prima
     * (invariante del viaggio attivo unico) e che senza POI visitati non
     * rimuova alcun voto.
     */
    @Test
    fun deleteTravel_closesActiveTravelFirst() = runTest {
        val travelDao = mock<TravelDao>()
        val travelPoiVisitDao = mock<TravelPoiVisitDao>()
        val poiVoteRepository = mock<PoiVoteRepository>()
        val travelRepository = mock<TravelRepository>()
        val active = TravelEntity(
            id = 5L,
            startUtcMillis = 0L,
            endUtcMillis = null,
            travelTimeZoneId = "UTC",
            title = null
        )
        whenever(travelDao.travelByIdOnce(5L)).thenReturn(active)
        whenever(travelPoiVisitDao.visitsForTravelOnce(5L)).thenReturn(emptyList())

        journalRepository(travelDao, travelPoiVisitDao, poiVoteRepository, travelRepository)
            .deleteTravel(5L)

        verify(travelRepository).stopTravel()
        verify(travelDao).deleteById(5L)
        verify(poiVoteRepository, never()).removeVote(any())
    }

    /**
     * Verifica la correzione del bug dei nomi duplicati: a un viaggio senza
     * titolo viene assegnato "Trip N" prendendo il numero PIÙ BASSO libero
     * tra tutti i titoli esistenti, così due viaggi non possono mai chiamarsi
     * allo stesso modo (l'auto-titolo non deriva più dalle città rilevate).
     */
    @Test
    fun detectCities_autoTitlesTitlelessTripWithLowestFreeNumber() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val travelCityDao = mock<TravelCityDao>()
        val travel = TravelEntity(
            id = 9L,
            startUtcMillis = 0L,
            endUtcMillis = 1_000L,
            travelTimeZoneId = "UTC",
            title = null
        )
        // The trip already has its path points (two clusters = two cities).
        whenever(pathPointDao.pointsForTravelOnce(9L)).thenReturn(
            listOf(
                TravelPathPointEntity(id = 1L, travelId = 9L, latitude = 0.0, longitude = 0.0, recordedAtUtcMillis = 0L),
                TravelPathPointEntity(id = 2L, travelId = 9L, latitude = 1.0, longitude = 1.0, recordedAtUtcMillis = 1L)
            )
        )
        whenever(travelCityDao.citiesForTravelOnce(9L)).thenReturn(emptyList())
        whenever(travelDao.travelByIdOnce(9L)).thenReturn(travel)
        // Existing titles leave "Trip 2" free (hole left by a deleted trip):
        // the generator must pick it instead of guessing from the max.
        whenever(travelDao.allTitlesOnce()).thenReturn(listOf("Trip 1", "Trip 3", "My vacation"))

        journalRepository(
            travelDao,
            travelPoiVisitDao = mock(),
            poiVoteRepository = mock(),
            travelRepository = mock(),
            travelCityDao = travelCityDao,
            pathPointDao = pathPointDao
        ).detectCities(9L)

        verify(travelDao).update(argThat { title == "Trip 2" })
    }

    /**
     * Verifica che un viaggio GIÀ titolato dall'utente non venga mai
     * rinominato dall'auto-titolo.
     */
    @Test
    fun detectCities_keepsUserChosenTitle() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val travelCityDao = mock<TravelCityDao>()
        val travel = TravelEntity(
            id = 9L,
            startUtcMillis = 0L,
            endUtcMillis = 1_000L,
            travelTimeZoneId = "UTC",
            title = "Road trip"
        )
        whenever(pathPointDao.pointsForTravelOnce(9L)).thenReturn(
            listOf(
                TravelPathPointEntity(id = 1L, travelId = 9L, latitude = 0.0, longitude = 0.0, recordedAtUtcMillis = 0L)
            )
        )
        whenever(travelCityDao.citiesForTravelOnce(9L)).thenReturn(emptyList())
        whenever(travelDao.travelByIdOnce(9L)).thenReturn(travel)
        whenever(travelDao.allTitlesOnce()).thenReturn(emptyList())

        journalRepository(
            travelDao,
            travelPoiVisitDao = mock(),
            poiVoteRepository = mock(),
            travelRepository = mock(),
            travelCityDao = travelCityDao,
            pathPointDao = pathPointDao
        ).detectCities(9L)

        verify(travelDao, never()).update(any())
    }

    /**
     * Verifica che la durata traveled sia esposta in delega al
     * TravelRepository: journal e mappa mostrano lo STESSO valore (una sola
     * logica dei blocchi di sessione), e lo schermo lo legge attraverso
     * questo repository (MVVM separation, mai dal travel repository diretto).
     */
    @Test
    fun observeTraveledDuration_delegatesToTravelRepository() = runTest {
        val travelRepository = mock<TravelRepository>()
        whenever(travelRepository.observeTraveledDuration(5L)).thenReturn(
            kotlinx.coroutines.flow.flowOf(1_234L)
        )

        val repo = journalRepository(
            travelDao = mock(),
            travelPoiVisitDao = mock(),
            poiVoteRepository = mock(),
            travelRepository = travelRepository
        )

        org.junit.Assert.assertEquals(
            1_234L,
            repo.observeTraveledDuration(5L).first()
        )
    }
}
