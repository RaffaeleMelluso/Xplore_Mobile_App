package com.example.progmob_progetto_melluso.journal

import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.travel.TravelRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the travel journal mutations, focused on the delete flow: a
 * deleted trip must take the user's votes on its visited POIs with it, and an
 * active trip must be closed first (single-active-travel invariant).
 */
class TravelJournalRepositoryImplTest {

    private fun journalRepository(
        travelDao: TravelDao,
        travelPoiVisitDao: TravelPoiVisitDao,
        poiVoteRepository: PoiVoteRepository,
        travelRepository: TravelRepository
    ) = TravelJournalRepositoryImpl(
        travelDao = travelDao,
        travelMediaDao = mock(),
        travelCityDao = mock(),
        travelPoiVisitDao = travelPoiVisitDao,
        pathPointDao = mock(),
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
}
