package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
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

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }

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
     * Verifica che il seed dei voti demo scriva come voti REALI i dati di
     * demo: i voti dei ghost (Parco Dora è votato da ghost-turin-1, -2 e -19
     * con i loro valori ed età) e i voti demo delle altre città (Tour Eiffel
     * riceve decine di voti dal suo profilo), poi aggiorni il contatore cache
     * di ogni POI. Così la card di un ghost e i trends mostrano sempre lo
     * stesso punteggio, e il voto dell'utente si SOMMA ai voti demo (50 -> 51).
     */
    @Test
    fun ensureDemoVotes_seedsGhostAndCityVotes() = runTest {
        val parcoDora = poi(1L, "Parco Dora", "Torino")
        val tourEiffel = poi(2L, "Tour Eiffel", "Paris")
        whenever(poiDao.allPoisOnce()).thenReturn(listOf(parcoDora, tourEiffel))
        whenever(poiVoteDao.voteScoreForPoiOnce(any())).thenReturn(1L)

        repository.ensureDemoVotes()

        // Ghost votes: exactly the rows DemoGhostVisits shows in the cards.
        verify(poiVoteDao).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-1", voteValue = -1, votedAtUtcMillis = now - 15 * MINUTE_MILLIS))
        verify(poiVoteDao).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-2", voteValue = -1, votedAtUtcMillis = now - 30 * MINUTE_MILLIS))
        verify(poiVoteDao).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-19", voteValue = 1, votedAtUtcMillis = now - 12 * MINUTE_MILLIS))
        // City demo votes: the Tour Eiffel profile, tens of up/down rows.
        verify(poiVoteDao, atLeast(20)).vote(org.mockito.kotlin.argThat { poiId == 2L })
        // Cached counters refreshed for both POIs.
        verify(poiDao).updateVotes(1L, 1)
        verify(poiDao).updateVotes(2L, 1)
    }

    /**
     * Verifica che il seed dei voti demo sia IDEMPOTENTE: rilanciato due
     * volte, ogni riga demo viene scritta esattamente DUE volte con gli stessi
     * valori (REPLACE + indice UNIQUE su (poiId, voterUserId) rinfrescano i
     * voti demo senza mai duplicarli), così il totale non cresce rilanciando
     * e il voto dell'utente resta una riga a parte.
     */
    @Test
    fun ensureDemoVotes_reSeedingIsIdempotent() = runTest {
        val parcoDora = poi(1L, "Parco Dora", "Torino")
        whenever(poiDao.allPoisOnce()).thenReturn(listOf(parcoDora))
        whenever(poiVoteDao.voteScoreForPoiOnce(any())).thenReturn(3L)

        repository.ensureDemoVotes()
        repository.ensureDemoVotes()

        // Each of the 3 Parco Dora demo rows was inserted exactly TWICE (once
        // per run): the unique index keeps the table at 3 rows, never 6.
        verify(poiVoteDao, times(2)).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-1", voteValue = -1, votedAtUtcMillis = now - 15 * MINUTE_MILLIS))
        verify(poiVoteDao, times(2)).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-2", voteValue = -1, votedAtUtcMillis = now - 30 * MINUTE_MILLIS))
        verify(poiVoteDao, times(2)).vote(PoiVoteEntity(poiId = 1L, voterUserId = "ghost-turin-19", voteValue = 1, votedAtUtcMillis = now - 12 * MINUTE_MILLIS))
    }

    private fun poi(id: Long, title: String, city: String) = PoiEntity(
        id = id,
        lat = 0.0,
        lon = 0.0,
        title = title,
        description = null,
        category = null,
        createdByUserId = null,
        city = city
    )
}
