package com.example.progmob_progetto_melluso.trend

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiTrendRow
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.dao.CityTrendRow
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifica che il repository dei trends aggreghi i voti reali (righe del DAO)
 * nei modelli di UI, attacchi il voto dell'utente, deleghi il voto al motore
 * condiviso e popoli i dati demo solo quando serve.
 */
class TrendRepositoryImplTest {

    private val poiVoteDao = mock<PoiVoteDao>()
    private val poiDao = mock<PoiDao>()
    private val voteRepository = mock<PoiVoteRepository>()
    private val now = 1_000_000_000_000L

    private fun repository(seedPois: List<PoiEntity> = emptyList()) = TrendRepositoryImpl(
        poiVoteDao = poiVoteDao,
        poiDao = poiDao,
        poiVoteRepository = voteRepository,
        seedPois = seedPois,
        clock = { now }
    )

    private fun poi(id: Long = 1L) = PoiEntity(
        id = id,
        lat = 45.0,
        lon = 7.0,
        title = "Mole Antonelliana",
        description = null,
        category = "Museum",
        createdByUserId = null,
        city = "Torino"
    )

    @Test
    fun observePoiTrends_mapsRowsAndAttachesMyVote() = runTest {
        whenever(poiVoteDao.poiTrends(TrendPeriod.WEEK.startUtcMillis(now)))
            .thenReturn(flowOf(listOf(PoiTrendRow(poi = poi(), windowScore = 12L, windowVotes = 8L))))
        whenever(voteRepository.observeMyVotes())
            .thenReturn(flowOf(listOf(PoiVoteEntity(poiId = 1L, voterUserId = "me", voteValue = 1, votedAtUtcMillis = now))))

        val items = repository().observePoiTrends(TrendPeriod.WEEK).first()

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(1L, item.poiId)
        assertEquals("Mole Antonelliana", item.title)
        assertEquals("Museum", item.category)
        assertEquals("Torino", item.city)
        assertEquals(12L, item.windowScore)
        assertEquals(8L, item.windowVotes)
        assertEquals(1, item.myVote)
    }

    @Test
    fun observePoiTrends_withoutMyVote_myVoteIsZero() = runTest {
        whenever(poiVoteDao.poiTrends(TrendPeriod.DAY.startUtcMillis(now)))
            .thenReturn(flowOf(listOf(PoiTrendRow(poi = poi(), windowScore = 4L, windowVotes = 3L))))
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val items = repository().observePoiTrends(TrendPeriod.DAY).first()

        assertEquals(0, items[0].myVote)
    }

    @Test
    fun observeCityTrends_mapsRows() = runTest {
        whenever(poiVoteDao.cityTrends(TrendPeriod.MONTH.startUtcMillis(now)))
            .thenReturn(flowOf(listOf(CityTrendRow("Torino", 42L, 20L), CityTrendRow("Cerignola", 9L, 5L))))

        val items = repository().observeCityTrends(TrendPeriod.MONTH).first()

        assertEquals(listOf(CityTrendItem("Torino", 42L, 20L), CityTrendItem("Cerignola", 9L, 5L)), items)
    }

    @Test
    fun votePoi_delegatesToTheSharedEngine() = runTest {
        repository().votePoi(1L, -1)

        verify(voteRepository).votePoi(1L, -1)
    }

    @Test
    fun ensureTrendSeed_seedsPoisWhenEmptyAndDelegatesVotes() = runTest {
        whenever(poiDao.count()).thenReturn(0)

        repository(seedPois = listOf(poi(1L), poi(2L))).ensureTrendSeed()

        // The missing demo POIs are inserted, and the community votes are
        // seeded through the shared vote engine (the ghosts' own votes).
        verify(poiDao, times(2)).insert(any<PoiEntity>())
        verify(voteRepository).ensureDemoVotes()
    }

    @Test
    fun ensureTrendSeed_keepsExistingPois() = runTest {
        whenever(poiDao.count()).thenReturn(3)

        repository(seedPois = listOf(poi(1L))).ensureTrendSeed()

        verify(poiDao, never()).insert(any<PoiEntity>())
        verify(voteRepository).ensureDemoVotes()
    }
}
