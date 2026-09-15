package com.example.progmob_progetto_melluso.ghost

import com.example.progmob_progetto_melluso.journal.TransportMode

/**
 * Single source of truth for the demo community data: the places every
 * simulated traveler ("ghost") visited and how they voted on them.
 *
 * Why it exists: the ghost cards must show the SAME votes the trends page
 * aggregates, so the visits are defined here once and consumed by both sides:
 * - [GhostFactory] builds each ghost's journal-like visits from [visitsByUser]
 *   (the card shows the POI, the real community score and the ghost's vote);
 * - the vote seed ([com.example.progmob_progetto_melluso.poi.DemoVoteSeeder])
 *   turns every visit with `myVote != 0` into a real `poi_votes` row.
 *
 * The `minutesAgo` values are sample data "from the ghosts": recent for the
 * online travelers, spread over days/weeks/months for the past visitors, so
 * the day/week/month/year trend windows show different content.
 */
object DemoGhostVisits {

    /** One place a ghost visited: the POI title, the ghost's vote (1/-1/0 =
     *  none) and how many minutes before "now" the visit happened. */
    data class Visit(val poiTitle: String, val myVote: Int, val minutesAgo: Long)

    /** One real vote derived from a visit (only the visits with a vote). */
    data class Vote(val voterUserId: String, val poiTitle: String, val voteValue: Int, val minutesAgo: Long)

    /** One transport segment a ghost reported (mode + optional line + when). */
    data class TransportSpec(val mode: TransportMode, val lineName: String?, val minutesAgo: Long)

    /** The visits of every ghost, keyed by its stable user id. */
    val visitsByUser: Map<String, List<Visit>> = mapOf(
        // Torino — north (dept / Parco Dora)
        "ghost-turin-1" to listOf(
            Visit("Dipartimento di Informatica", 1, 40),
            Visit("Parco Dora", -1, 15)
        ),
        "ghost-turin-2" to listOf(
            Visit("Parco Dora", -1, 30),
            Visit("Vitali Park", 0, 5)
        ),
        "ghost-turin-3" to listOf(
            Visit("Dipartimento di Informatica", 1, 1_440)
        ),
        "ghost-turin-4" to listOf(
            Visit("Parco Dora", 0, 2_880)
        ),
        "ghost-turin-19" to listOf(
            Visit("Parco Dora", 1, 12)
        ),
        "ghost-turin-20" to listOf(
            Visit("Vitali Park", -1, 1_440)
        ),
        // Torino — centre (Castello, Mole, Porta Nuova)
        "ghost-turin-5" to listOf(
            Visit("Museo Egizio", 1, 90),
            Visit("Piazza Castello", 0, 30)
        ),
        "ghost-turin-6" to listOf(
            Visit("Stazione Porta Nuova", 0, 4_320)
        ),
        "ghost-turin-7" to listOf(
            Visit("Mole Antonelliana", 1, 60),
            Visit("Piazza Vittorio Veneto", 0, 15)
        ),
        "ghost-turin-8" to listOf(
            Visit("Duomo di Torino", -1, 7_200),
            Visit("Mole Antonelliana", 1, 7_170)
        ),
        "ghost-turin-11" to listOf(
            Visit("Piazza Castello", 0, 25)
        ),
        "ghost-turin-12" to listOf(
            Visit("Palazzo Reale", 1, 17_280)
        ),
        "ghost-turin-13" to listOf(
            Visit("Porta Palatina", 1, 35)
        ),
        "ghost-turin-16" to listOf(
            Visit("Stazione Porta Nuova", -1, 10)
        ),
        "ghost-turin-17" to listOf(
            Visit("Piazza Vittorio Veneto", 0, 1_440),
            Visit("Gran Madre di Dio", 1, 1_425)
        ),
        "ghost-turin-18" to listOf(
            Visit("Gran Madre di Dio", 1, 5)
        ),
        // Torino — river (Valentino)
        "ghost-turin-9" to listOf(
            Visit("Parco del Valentino", 1, 45),
            Visit("Borgo Medievale", 0, 20)
        ),
        "ghost-turin-10" to listOf(
            Visit("Parco del Valentino", 1, 11_520)
        ),
        // Torino — south (Lingotto)
        "ghost-turin-15" to listOf(
            Visit("Lingotto", 1, 20)
        ),
        "ghost-turin-14" to listOf(
            Visit("Porta Palatina", 0, 28_800)
        ),
        // Cerignola
        "ghost-cerignola-1" to listOf(
            Visit("Cattedrale San Pietro Apostolo", 1, 50),
            Visit("Piazza della Repubblica", 0, 20),
            Visit("Villa Comunale Umberto I", 1, 5)
        ),
        "ghost-cerignola-2" to listOf(
            Visit("Santuario Madonna di Ripalta", 1, 43_200)
        ),
        "ghost-cerignola-3" to listOf(
            Visit("Cattedrale San Pietro Apostolo", 0, 30),
            Visit("Stazione di Cerignola Città", 1, 5)
        ),
        "ghost-cerignola-4" to listOf(
            Visit("Villa Comunale Umberto I", 1, 1_440)
        ),
        "ghost-cerignola-5" to listOf(
            Visit("Cattedrale San Pietro Apostolo", 1, 25),
            Visit("Piazza della Repubblica", 0, 10),
            Visit("Villa Comunale Umberto I", 1, 2)
        )
    )

    /**
     * The actual votes of the demo community: every visit where the ghost
     * voted (myVote != 0). This is exactly what the vote seed stores as real
     * `poi_votes`, so trends and ghost cards always agree.
     */
    val votes: List<Vote> = visitsByUser.flatMap { (userId, visits) ->
        visits.filter { it.myVote != 0 }
            .map { Vote(userId, it.poiTitle, it.myVote, it.minutesAgo) }
    }

    /**
     * The transport segments every ghost reported, shown as examples in the
     * ghost cards (same model as the travel journal, the transport-reporting requirement): the demo
     * travelers use buses, trams, the metro, ferries and walk, with real line
     * numbers of the cities they move in.
     */
    val transportsByUser: Map<String, List<TransportSpec>> = mapOf(
        "ghost-turin-1" to listOf(
            TransportSpec(TransportMode.BUS, "4", 70),
            TransportSpec(TransportMode.WALK, null, 45)
        ),
        "ghost-turin-5" to listOf(
            TransportSpec(TransportMode.TRAM, "15", 100),
            TransportSpec(TransportMode.WALK, null, 30)
        ),
        "ghost-turin-6" to listOf(
            TransportSpec(TransportMode.METRO, "1", 4_330)
        ),
        "ghost-turin-7" to listOf(
            TransportSpec(TransportMode.TRAM, "13", 75)
        ),
        "ghost-turin-11" to listOf(
            TransportSpec(TransportMode.TRAM, "4", 50)
        ),
        "ghost-turin-13" to listOf(
            TransportSpec(TransportMode.BUS, "11", 40)
        ),
        "ghost-turin-17" to listOf(
            TransportSpec(TransportMode.FERRY, null, 1_430),
            TransportSpec(TransportMode.WALK, null, 1_425)
        ),
        "ghost-cerignola-3" to listOf(
            TransportSpec(TransportMode.BUS, "1", 20),
            TransportSpec(TransportMode.WALK, null, 5)
        ),
        "ghost-cerignola-5" to listOf(
            TransportSpec(TransportMode.WALK, null, 15)
        )
    )
}
