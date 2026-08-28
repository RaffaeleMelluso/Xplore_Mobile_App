package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifica il repository del catalogo POI: l'osservazione delega ai flussi
 * reattivi del DAO (così un POI aggiunto raggiunge mappa, ghost e trends),
 * l'inserimento azzera il contatore cache e stampa il proprietario, la lista
 * "my POIs" filtra per utente e la rimozione cancella solo i POI propri.
 */
class PoiRepositoryImplTest {

    private val poiDao = mock<PoiDao>()
    private val userId = "user-1"
    private val repository = PoiRepositoryImpl(poiDao, userIdProvider = { userId })

    private fun poi(id: Long = 5L, owner: String? = null) = PoiEntity(
        id = id,
        lat = 35.71,
        lon = 139.79,
        title = "Senso-ji Temple",
        description = null,
        category = "Temple",
        createdByUserId = owner,
        city = "Tokyo"
    )

    @Test
    fun observePois_delegatesToDaoFlow() = runTest {
        val poi = poi()
        whenever(poiDao.allPois()).thenReturn(flowOf(listOf(poi)))

        val pois = repository.observePois().first()

        assertEquals(listOf(poi), pois)
    }

    @Test
    fun observeMyPois_filtersByTheSignedInUser() = runTest {
        whenever(poiDao.myPois(userId)).thenReturn(flowOf(listOf(poi(owner = userId))))

        val pois = repository.observeMyPois().first()

        assertEquals(listOf(poi(owner = userId)), pois)
        verify(poiDao).myPois(userId)
    }

    @Test
    fun addPoi_storesThePoiWithZeroedScoreAndOwner() = runTest {
        // The user accidentally submitted a POI with a non-zero counter: the
        // repository zeroes it, because a fresh POI has no votes yet.
        val userPoi = PoiEntity(lat = 48.85, lon = 2.29, title = "User POI", description = "d", category = "Museum", createdByUserId = null, city = "Paris", imageUrl = "https://example.com/x.jpg", votes = 42)
        whenever(poiDao.insert(any<PoiEntity>())).thenReturn(9L)

        val id = repository.addPoi(userPoi)

        assertEquals(9L, id)
        verify(poiDao).insert(userPoi.copy(createdByUserId = userId, votes = 0))
    }

    @Test
    fun addPoi_keepsAnExplicitOwner() = runTest {
        val userPoi = poi(owner = "someone-else")
        whenever(poiDao.insert(any<PoiEntity>())).thenReturn(1L)

        repository.addPoi(userPoi)

        verify(poiDao).insert(userPoi.copy(votes = 0))
    }

    @Test
    fun removeMyPoi_deletesOnlyWhenThePoiIsOwned() = runTest {
        whenever(poiDao.byIdOnce(7L)).thenReturn(poi(id = 7L, owner = userId))

        val removed = repository.removeMyPoi(7L)

        assertTrue(removed)
        verify(poiDao).delete(7L)
    }

    @Test
    fun removeMyPoi_doesNothingForPoisOfOthers() = runTest {
        whenever(poiDao.byIdOnce(7L)).thenReturn(poi(id = 7L, owner = "someone-else"))

        val removed = repository.removeMyPoi(7L)

        assertFalse(removed)
        verify(poiDao, never()).delete(any())
    }

    @Test
    fun removeMyPoi_doesNothingForMissingPois() = runTest {
        whenever(poiDao.byIdOnce(7L)).thenReturn(null)

        val removed = repository.removeMyPoi(7L)

        assertFalse(removed)
        verify(poiDao, never()).delete(any())
    }
}
