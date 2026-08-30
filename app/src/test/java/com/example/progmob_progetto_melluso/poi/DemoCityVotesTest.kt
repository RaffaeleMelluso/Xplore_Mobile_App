package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.ghost.DemoGhostVisits
import com.example.progmob_progetto_melluso.map.DemoPoiSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica l'integrità dei dati demo dei voti città e — soprattutto — che la
 * loro distribuzione temporale faccia CAMBIARE ORDINE ai filtri dei trends:
 * giorno, settimana, mese e anno devono produrre classifiche diverse.
 */
class DemoCityVotesTest {

    private val yearMinutes = 365L * 24L * 60L

    /** Ogni voto demo deve puntare a un POI che esiste nel seed condiviso. */
    @Test
    fun everyCityVote_resolvesToASeededPoi() {
        val seededTitles = DemoPoiSeed.all.map { it.title }.toSet()

        DemoCityVotes.votes.forEach { vote ->
            assertTrue(
                "Vote references unknown POI '${vote.poiTitle}'",
                vote.poiTitle in seededTitles
            )
        }
    }

    /** I voti demo sono voti reali (+1/-1) nell'arco di UN anno. */
    @Test
    fun cityVotes_areRealUpDownVotesWithinOneYear() {
        DemoCityVotes.votes.forEach { vote ->
            assertTrue("voteValue must be +/-1", vote.voteValue == 1 || vote.voteValue == -1)
            assertTrue("age must be within the year window", vote.minutesAgo in 1..yearMinutes)
            assertTrue("voter id must be a demo voter", vote.voterUserId.startsWith("demo-voter-"))
        }
    }

    /** Massa sufficiente a popolare i trends (il flagship supera i 50 voti). */
    @Test
    fun cityVotes_haveEnoughMassForTheTrends() {
        assertTrue("expected hundreds of demo votes", DemoCityVotes.votes.size >= 300)

        val eiffelScore = DemoCityVotes.votes
            .filter { it.poiTitle == "Tour Eiffel" }
            .sumOf { it.voteValue }
        assertTrue("Tour Eiffel should hold ~50 demo votes, was $eiffelScore", eiffelScore >= 50)
    }

    /**
     * IL test chiave della richiesta: le finestre temporali (day/week/month/
     * year) devono classificare le attrazioni in ordine DIVERSO, così
     * l'utente vede i filtri cambiare la classifica.
     */
    @Test
    fun trendWindows_rankAttractionsDifferently() {
        val allVotes = DemoCityVotes.votes + DemoGhostVisits.votes.map { vote ->
            DemoCityVotes.Vote(vote.voterUserId, vote.poiTitle, vote.voteValue, vote.minutesAgo)
        }

        fun ranking(windowMinutes: Long): List<String> =
            allVotes.filter { it.minutesAgo <= windowMinutes }
                .groupBy { it.poiTitle }
                .mapValues { (_, votes) -> votes.sumOf { it.voteValue.toLong() } }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key }

        val day = 24L * 60L
        val week = 7L * day
        val month = 30L * day

        val dayRank = ranking(day)
        val weekRank = ranking(week)
        val monthRank = ranking(month)
        val yearRank = ranking(yearMinutes)

        assertTrue("day ranking must not be empty", dayRank.isNotEmpty())
        assertNotEquals("day vs year must differ", dayRank, yearRank)
        assertNotEquals("day vs week must differ", dayRank, weekRank)
        assertNotEquals("week vs month must differ", weekRank, monthRank)
        assertNotEquals("month vs year must differ", monthRank, yearRank)
    }

    /**
     * Anche le CITTÀ devono ordinarsi diversamente a seconda della finestra
     * (l'agglomerato dei voti dei POI di ogni città, come nel city scope).
     */
    @Test
    fun trendWindows_rankCitiesDifferently() {
        val seededCityByTitle = DemoPoiSeed.all.associate { it.title to it.city }
        val allVotes = DemoCityVotes.votes + DemoGhostVisits.votes.map { vote ->
            DemoCityVotes.Vote(vote.voterUserId, vote.poiTitle, vote.voteValue, vote.minutesAgo)
        }

        fun cityRanking(windowMinutes: Long): List<String> =
            allVotes.filter { it.minutesAgo <= windowMinutes }
                .groupBy { seededCityByTitle[it.poiTitle] }
                .filterKeys { it != null }
                .mapValues { (_, votes) -> votes.sumOf { it.voteValue.toLong() } }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String?, Long>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key as String }

        val day = 24L * 60L
        val month = 30L * day

        val dayRank = cityRanking(day)
        val yearRank = cityRanking(yearMinutes)

        assertTrue("day city ranking must not be empty", dayRank.isNotEmpty())
        assertEquals("every ranked city exists in the seed", true, dayRank.all { it.isNotBlank() })
        assertNotEquals("day vs year city order must differ", dayRank, yearRank)
    }
}
