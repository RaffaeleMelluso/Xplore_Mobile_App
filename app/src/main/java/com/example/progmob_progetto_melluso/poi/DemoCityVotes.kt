package com.example.progmob_progetto_melluso.poi

/**
 * Extra demo community votes for the points of interest.
 *
 * How the spread works: every POI gets a temporal profile made of buckets
 * (age in minutes -> how many up/down votes fall in that bucket):
 * - "viral today" profiles vote only in the last hours: they top the DAY
 *   filter but are low on the YEAR one;
 * - "weekly" / "monthly" profiles concentrate on 1-7 / 8-30 days;
 * - "heritage" profiles only have old votes (45-340 days): they appear ONLY
 *   in the MONTH/YEAR windows;
 * - "evergreen" flagships collect votes in every bucket: they top the YEAR
 *   ranking and stay visible everywhere.
 *
 * The votes are stored as REAL `poi_votes` rows by [DemoVoteSeeder] together
 * with the ghosts' votes, so every screen (map card, ghost journal, trends)
 * aggregates the same data. Timestamps are re-anchored to "now" on each seed,
 * so the demo spread stays meaningful whenever the app is launched.
 */
object DemoCityVotes {

    /** One demo vote: which POI, the vote value (1/-1) and how many minutes before "now". */
    data class Vote(val voterUserId: String, val poiTitle: String, val voteValue: Int, val minutesAgo: Long)

    /** Votes of the whole demo community (city profiles + core POI mass). */
    val votes: List<Vote> by lazy { build() }

    /** Time buckets in minutes, named for readability of the profiles. */
    private const val H1 = 60L
    private const val H3 = 180L
    private const val H8 = 480L
    private const val D1 = 1_440L        // 1 day
    private const val D2 = 2_880L
    private const val D3 = 4_320L
    private const val D5 = 7_200L
    private const val W1 = 10_080L       // 1 week
    private const val W2 = 20_160L       // 2 weeks
    private const val D12 = 17_280L
    private const val D20 = 28_800L
    private const val M1 = 43_200L       // 1 month
    private const val D45 = 64_800L
    private const val D75 = 108_000L
    private const val D120 = 172_800L    // ~4 months
    private const val D180 = 259_200L    // ~6 months
    private const val D240 = 345_600L    // ~8 months
    private const val D300 = 432_000L    // ~10 months
    private const val D340 = 489_600L    // ~11 months (inside the year window)

    /** One temporal bucket: (up votes, down votes, age in minutes). */
    private data class Bucket(val up: Int, val down: Int, val ageMinutes: Long)

    /** Compact spec builder: a POI title with its temporal profile. */
    private fun profile(title: String, vararg buckets: Bucket) = title to buckets.toList()

    /**
     * Every demo POI with its temporal profile. Counts scale per city tier
     * (Paris/Tokyo above, Marseille/Munich below) so the CITY ranking has a
     * clear, filter-dependent order too.
     */
    private val specs: List<Pair<String, List<Bucket>>> = listOf(
        // ---- Tokyo (tier 1) ----
        profile(
            "Senso-ji Temple",
            Bucket(2, 0, H1), Bucket(2, 0, H8), Bucket(3, 0, D1), Bucket(3, 1, D3),
            Bucket(3, 0, W1), Bucket(3, 0, W2), Bucket(4, 1, M1), Bucket(4, 0, D45),
            Bucket(4, 0, D75), Bucket(4, 1, D120), Bucket(4, 0, D180), Bucket(4, 0, D240),
            Bucket(3, 0, D300), Bucket(3, 0, D340)
        ),
        profile(
            "Tokyo Skytree",
            Bucket(10, 0, H1), Bucket(7, 0, H3), Bucket(5, 0, H8), Bucket(4, 0, D1),
            Bucket(1, 1, M1), Bucket(2, 0, D120)
        ),
        profile(
            "Meiji Shrine",
            Bucket(2, 0, D1), Bucket(4, 0, D2), Bucket(5, 1, D5), Bucket(4, 0, W1),
            Bucket(3, 0, W2), Bucket(2, 0, D12)
        ),
        profile(
            "Shibuya Crossing",
            Bucket(1, 0, D1), Bucket(3, 0, D12), Bucket(5, 0, D20), Bucket(4, 1, M1),
            Bucket(3, 0, D45), Bucket(2, 0, D75)
        ),
        profile(
            "Shinjuku Gyoen",
            Bucket(3, 0, D45), Bucket(3, 0, D75), Bucket(4, 1, D120), Bucket(3, 0, D180),
            Bucket(3, 0, D240), Bucket(2, 0, D300)
        ),
        // ---- Paris (tier 1, the flagship "50+ votes" POI) ----
        profile(
            "Tour Eiffel",
            Bucket(3, 0, H1), Bucket(3, 0, H8), Bucket(4, 0, D1), Bucket(4, 1, D3),
            Bucket(4, 0, D5), Bucket(4, 0, W1), Bucket(4, 1, W2), Bucket(5, 0, M1),
            Bucket(4, 0, D45), Bucket(4, 0, D75), Bucket(5, 1, D120), Bucket(4, 0, D180),
            Bucket(4, 0, D240), Bucket(4, 0, D300), Bucket(3, 0, D340)
        ),
        profile(
            "Musée du Louvre",
            Bucket(7, 0, H1), Bucket(5, 0, H3), Bucket(4, 0, H8), Bucket(3, 0, D1),
            Bucket(2, 0, D120), Bucket(1, 1, M1)
        ),
        profile(
            "Notre-Dame de Paris",
            Bucket(3, 0, D1), Bucket(4, 0, D2), Bucket(5, 1, D5), Bucket(4, 0, W1),
            Bucket(3, 0, W2), Bucket(2, 0, D12)
        ),
        profile(
            "Arc de Triomphe",
            Bucket(1, 0, D1), Bucket(3, 0, D12), Bucket(4, 0, D20), Bucket(4, 1, M1),
            Bucket(3, 0, D45), Bucket(2, 0, D75)
        ),
        profile(
            "Montmartre / Sacré-Cœur",
            Bucket(3, 0, D45), Bucket(3, 0, D75), Bucket(3, 1, D120), Bucket(3, 0, D180),
            Bucket(2, 0, D240), Bucket(2, 0, D300)
        ),
        // ---- Milano (tier 2) ----
        profile(
            "Duomo di Milano",
            Bucket(2, 0, H1), Bucket(2, 0, D1), Bucket(3, 0, D3), Bucket(3, 0, W1),
            Bucket(3, 0, W2), Bucket(4, 1, M1), Bucket(4, 0, D45), Bucket(3, 0, D75),
            Bucket(4, 1, D120), Bucket(4, 0, D180), Bucket(4, 0, D240), Bucket(3, 0, D300),
            Bucket(3, 0, D340)
        ),
        profile(
            "Galleria Vittorio Emanuele II",
            Bucket(5, 0, H1), Bucket(4, 0, H3), Bucket(3, 0, H8), Bucket(3, 0, D1),
            Bucket(1, 1, D120)
        ),
        profile(
            "Castello Sforzesco",
            Bucket(2, 0, D1), Bucket(3, 0, D2), Bucket(4, 0, D5), Bucket(3, 1, W1),
            Bucket(3, 0, W2), Bucket(1, 0, D12)
        ),
        profile(
            "Parco Sempione",
            Bucket(1, 0, D1), Bucket(3, 0, D12), Bucket(4, 0, D20), Bucket(3, 1, M1),
            Bucket(2, 0, D45), Bucket(2, 0, D75)
        ),
        profile(
            "Teatro alla Scala",
            Bucket(2, 0, D45), Bucket(3, 0, D75), Bucket(3, 1, D120), Bucket(3, 0, D180),
            Bucket(2, 0, D240), Bucket(2, 0, D300)
        ),
        // ---- Madrid (tier 2) ----
        profile(
            "Palacio Real",
            Bucket(2, 0, H1), Bucket(2, 0, D1), Bucket(2, 0, D3), Bucket(2, 0, D5),
            Bucket(3, 0, W1), Bucket(3, 0, W2), Bucket(3, 1, M1), Bucket(3, 0, D45),
            Bucket(3, 0, D75), Bucket(3, 1, D120), Bucket(3, 0, D180), Bucket(3, 0, D240),
            Bucket(3, 0, D300), Bucket(2, 0, D340)
        ),
        profile(
            "Parque del Retiro",
            Bucket(5, 0, H1), Bucket(4, 0, H3), Bucket(3, 0, D1), Bucket(2, 0, D120),
            Bucket(1, 1, D240)
        ),
        profile(
            "Museo del Prado",
            Bucket(2, 0, D1), Bucket(3, 0, D2), Bucket(3, 0, D5), Bucket(3, 1, W1),
            Bucket(2, 0, W2), Bucket(1, 0, D12)
        ),
        profile(
            "Puerta del Sol",
            Bucket(1, 0, D1), Bucket(3, 0, D12), Bucket(3, 0, D20), Bucket(3, 1, M1),
            Bucket(2, 0, D45)
        ),
        profile(
            "Plaza Mayor",
            Bucket(2, 0, D45), Bucket(2, 0, D75), Bucket(3, 0, D120), Bucket(2, 1, D180),
            Bucket(2, 0, D300)
        ),
        // ---- Barcellona (tier 3) ----
        profile(
            "Sagrada Família",
            Bucket(1, 0, H1), Bucket(2, 0, D1), Bucket(2, 0, W1), Bucket(2, 0, W2),
            Bucket(3, 0, M1), Bucket(3, 0, D45), Bucket(2, 1, D75), Bucket(3, 1, D120),
            Bucket(3, 0, D180), Bucket(3, 0, D240), Bucket(2, 0, D300), Bucket(2, 0, D340)
        ),
        profile(
            "Park Güell",
            Bucket(4, 0, H1), Bucket(3, 0, H3), Bucket(3, 0, D1), Bucket(1, 0, D120)
        ),
        profile(
            "Casa Batlló",
            Bucket(1, 0, D1), Bucket(2, 0, D2), Bucket(3, 0, D5), Bucket(3, 0, W1),
            Bucket(2, 0, W2)
        ),
        profile(
            "La Rambla",
            Bucket(1, 0, D1), Bucket(2, 0, D12), Bucket(3, 0, D20), Bucket(2, 1, M1),
            Bucket(2, 0, D45)
        ),
        profile(
            "Barceloneta Beach",
            Bucket(2, 0, D45), Bucket(2, 0, D120), Bucket(2, 0, D180), Bucket(2, 1, D300)
        ),
        // ---- Berlin (tier 3) ----
        profile(
            "Brandenburg Gate",
            Bucket(1, 0, H1), Bucket(2, 0, D1), Bucket(2, 0, W1), Bucket(3, 0, W2),
            Bucket(3, 0, M1), Bucket(2, 0, D45), Bucket(2, 0, D75), Bucket(3, 1, D120),
            Bucket(3, 0, D180), Bucket(2, 0, D240), Bucket(2, 0, D300), Bucket(2, 0, D340)
        ),
        profile(
            "Reichstag",
            Bucket(4, 0, H1), Bucket(3, 0, H3), Bucket(2, 0, D1), Bucket(1, 0, D180)
        ),
        profile(
            "Berliner Dom",
            Bucket(1, 0, D1), Bucket(2, 0, D2), Bucket(3, 0, D5), Bucket(2, 0, W1),
            Bucket(2, 0, W2), Bucket(1, 0, D12)
        ),
        profile(
            "East Side Gallery",
            Bucket(1, 0, D1), Bucket(2, 0, D12), Bucket(3, 0, D20), Bucket(2, 1, M1),
            Bucket(1, 0, D45)
        ),
        profile(
            "Tiergarten",
            Bucket(2, 0, D45), Bucket(2, 0, D120), Bucket(2, 0, D240), Bucket(1, 0, D300)
        ),
        // ---- Marseille (tier 4) ----
        profile(
            "Vieux-Port",
            Bucket(1, 0, D1), Bucket(2, 0, W1), Bucket(2, 0, W2), Bucket(2, 0, M1),
            Bucket(2, 0, D45), Bucket(2, 0, D75), Bucket(2, 0, D120), Bucket(2, 0, D180),
            Bucket(1, 0, D300)
        ),
        profile(
            "Basilique Notre-Dame de la Garde",
            Bucket(3, 0, H1), Bucket(2, 0, H3), Bucket(2, 0, D1), Bucket(1, 0, D120)
        ),
        profile(
            "Calanques National Park",
            Bucket(1, 0, D2), Bucket(2, 0, D5), Bucket(2, 0, W1), Bucket(2, 0, W2),
            Bucket(1, 0, D12)
        ),
        profile(
            "MUCEM",
            Bucket(2, 0, D75), Bucket(2, 0, D120), Bucket(1, 0, D240), Bucket(2, 0, D300)
        ),
        // ---- Munich (tier 4) ----
        profile(
            "Marienplatz",
            Bucket(1, 0, H1), Bucket(1, 0, D1), Bucket(2, 0, D2), Bucket(2, 0, W1),
            Bucket(2, 0, W2), Bucket(2, 0, M1), Bucket(2, 0, D45), Bucket(2, 0, D75),
            Bucket(2, 1, D120), Bucket(2, 0, D180), Bucket(2, 0, D300)
        ),
        profile(
            "Neues Rathaus",
            Bucket(3, 0, H1), Bucket(2, 0, H3), Bucket(2, 0, D1), Bucket(1, 0, D240)
        ),
        profile(
            "Englischer Garten",
            Bucket(1, 0, D1), Bucket(2, 0, D5), Bucket(2, 0, W1), Bucket(2, 0, W2),
            Bucket(1, 0, D12)
        ),
        profile(
            "Nymphenburg Palace",
            Bucket(2, 0, D75), Bucket(2, 0, D120), Bucket(1, 0, D240), Bucket(2, 0, D300)
        ),
        profile(
            "Alte Pinakothek",
            Bucket(1, 0, D12), Bucket(2, 0, D20), Bucket(2, 1, M1), Bucket(1, 0, D45)
        ),
        // ---- Turin / Cerignola boosts (on top of the ghosts' own votes) so
        // the "home" cities compete in the city ranking too. ----
        profile(
            "Mole Antonelliana",
            Bucket(2, 0, H1), Bucket(2, 0, D1), Bucket(2, 0, W1), Bucket(3, 0, W2),
            Bucket(2, 0, M1), Bucket(3, 0, D45), Bucket(2, 0, D75), Bucket(2, 1, D120),
            Bucket(2, 0, D180), Bucket(2, 0, D240), Bucket(1, 0, D300), Bucket(2, 0, D340)
        ),
        profile(
            "Museo Egizio",
            Bucket(1, 0, D1), Bucket(2, 0, D12), Bucket(2, 0, D20), Bucket(2, 1, M1),
            Bucket(2, 0, D45), Bucket(1, 0, D75)
        ),
        profile(
            "Parco del Valentino",
            Bucket(1, 0, D1), Bucket(2, 0, D2), Bucket(2, 0, D5), Bucket(2, 0, W1),
            Bucket(2, 0, W2), Bucket(1, 0, D12)
        ),
        profile(
            "Piazza Castello",
            Bucket(1, 0, D1), Bucket(1, 0, W1), Bucket(2, 0, M1), Bucket(1, 0, D45),
            Bucket(1, 0, D120), Bucket(1, 0, D240)
        ),
        profile(
            "Stazione Porta Nuova",
            Bucket(1, 0, H1), Bucket(1, 0, D2), Bucket(1, 0, D20), Bucket(1, 0, D180)
        ),
        profile(
            "Cattedrale San Pietro Apostolo",
            Bucket(1, 0, D1), Bucket(1, 0, W2), Bucket(2, 0, M1), Bucket(1, 0, D45),
            Bucket(1, 0, D120), Bucket(1, 0, D240)
        )
    )

    /**
     * Expands every bucket into single votes with sequential demo voter ids
     * (one vote per user per POI, as the unique index requires). The age is
     * jittered slightly YOUNGER inside the bucket (never across its window),
     * so votes in the same bucket do not share the exact timestamp.
     */
    private fun build(): List<Vote> {
        val result = mutableListOf<Vote>()
        var voterIndex = 0
        specs.forEach { (title, buckets) ->
            buckets.forEach { bucket ->
                repeat(bucket.up) { i ->
                    result += Vote(voterId(voterIndex++), title, 1, jitter(bucket.ageMinutes, i))
                }
                repeat(bucket.down) { i ->
                    result += Vote(voterId(voterIndex++), title, -1, jitter(bucket.ageMinutes, i))
                }
            }
        }
        return result
    }

    private fun voterId(index: Int) = "demo-voter-%03d".format(index)

    /** Deterministic jitter: at most ~4 minutes back per step, capped at 240. */
    private fun jitter(ageMinutes: Long, index: Int): Long {
        val cap = minOf(ageMinutes / 3L, 240L)
        return if (cap <= 1L) ageMinutes else ageMinutes - (index * 47L) % cap
    }
}
