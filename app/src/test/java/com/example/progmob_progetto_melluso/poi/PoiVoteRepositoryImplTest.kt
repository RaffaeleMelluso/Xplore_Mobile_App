package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifica le regole del voto condiviso (un voto per utente per POI): il
 * primo voto si registra, lo stesso voto si rimuove (toggle), il voto opposto
 * sostituisce il precedente, e il contatore cache del POI viene aggiornato.
 * Queste stesse regole valgono nel journal, nella mappa e nei trends.
 */
class PoiVoteRepositoryImplTest {

    private val poiVoteDao = mock<PoiVoteDao>()
    private val poiDao = mock<PoiDao>()
    private var now = 1_000_000L

    private val repository = PoiVoteRepositoryImpl(
        poiVoteDao = poiVoteDao,
        poiDao = poiDao,
        voterIdProvider = { "voter-1" },
        clock = { now }
    )

    @Test
    fun neverVoted_recordsTheVote() = runTest {
        whenever(poiVoteDao.myVoteOnce(7L, "voter-1")).thenReturn(null)
        whenever(poiVoteDao.voteScoreForPoiOnce(7L)).thenReturn(3L)

        repository.votePoi(7L, 1)

        verify(poiVoteDao).vote(
            PoiVoteEntity(poiId = 7L, voterUserId = "voter-1", voteValue = 1, votedAtUtcMillis = now)
        )
        verify(poiDao).updateVotes(7L, 3)
    }

    @Test
    fun sameVoteAgain_togglesItOff() = runTest {
        whenever(poiVoteDao.myVoteOnce(7L, "voter-1")).thenReturn(1)
        whenever(poiVoteDao.voteScoreForPoiOnce(7L)).thenReturn(0L)

        repository.votePoi(7L, 1)

        verify(poiVoteDao).removeVote(7L, "voter-1")
        verify(poiVoteDao, never()).vote(any<PoiVoteEntity>())
        verify(poiDao).updateVotes(7L, 0)
    }

    @Test
    fun oppositeVote_replacesThePreviousOne() = runTest {
        whenever(poiVoteDao.myVoteOnce(7L, "voter-1")).thenReturn(1)
        whenever(poiVoteDao.voteScoreForPoiOnce(7L)).thenReturn(5L)

        repository.votePoi(7L, -1)

        verify(poiVoteDao).vote(
            PoiVoteEntity(poiId = 7L, voterUserId = "voter-1", voteValue = -1, votedAtUtcMillis = now)
        )
        verify(poiVoteDao, never()).removeVote(any(), any())
        verify(poiDao).updateVotes(7L, 5)
    }

    @Test
    fun invalidVoteValue_isRejected() = runTest {
        var threw = false
        try {
            repository.votePoi(7L, 0)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        verify(poiVoteDao, never()).vote(any<PoiVoteEntity>())
    }

    /**
     * Verifica che removeVote elimini il voto dell'utente (in qualsiasi
     * direzione) e aggiorni il contatore cache: usato quando un viaggio viene
     * eliminato e i voti dati sui suoi POI devono sparire.
     */
    @Test
    fun removeVote_deletesTheUsersVoteAndRefreshesScore() = runTest {
        whenever(poiVoteDao.voteScoreForPoiOnce(7L)).thenReturn(2L)

        repository.removeVote(7L)

        verify(poiVoteDao).removeVote(7L, "voter-1")
        verify(poiDao).updateVotes(7L, 2)
    }

    /**
     * Verifica che il seed dei voti demo salvi come voti REALI esattamente i
     * voti che i ghost mostrano nelle loro card (es. Parco Dora è votato da
     * ghost-turin-1, -2 e -19) e aggiorni il contatore cache.
     */
    @Test
    fun ensureDemoVotes_seedsGhostVotesWhenEmpty() = runTest {
        val poi = PoiEntity(
            id = 1L,
            lat = 0.0,
            lon = 0.0,
            title = "Parco Dora",
            description = null,
            category = null,
            createdByUserId = null,
            city = "Torino"
        )
        whenever(poiVoteDao.count()).thenReturn(0L)
        whenever(poiDao.allPoisOnce()).thenReturn(listOf(poi))
        whenever(poiVoteDao.voteScoreForPoiOnce(any())).thenReturn(1L)

        repository.ensureDemoVotes()

        verify(poiVoteDao, times(3)).vote(any<PoiVoteEntity>())
        verify(poiDao).updateVotes(1L, 1)
    }

    /**
     * Verifica che il seed dei voti demo sia idempotente: con voti già
     * presenti non inserisce nulla.
     */
    @Test
    fun ensureDemoVotes_skipsWhenVotesAlreadyExist() = runTest {
        whenever(poiVoteDao.count()).thenReturn(1L)

        repository.ensureDemoVotes()

        verify(poiVoteDao, never()).vote(any<PoiVoteEntity>())
        verify(poiDao, never()).updateVotes(any(), any())
    }
}
