package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.ghost.DemoGhostVisits

/**
 * Seeds the demo community votes as REAL votes.
 *
 * How it works: two demo sources are turned into `poi_votes` rows —
 * - [DemoGhostVisits.votes]: the votes the simulated travelers show in their
 *   cards (Turin / Cerignola);
 * - [DemoCityVotes.votes]: the year-long vote spread across ALL the demo
 *   cities, so the trend windows (day/week/month/year) rank POIs and cities
 *   differently.
 *
 * Idempotency: the `poi_votes` table has a UNIQUE index on (poiId,
 * voterUserId) and the insert is REPLACE, so re-running the seed never
 * duplicates a vote — an already-seeded (poi, demo voter) pair is refreshed
 * in place. Timestamps are re-anchored to "now" on each seed (each vote is
 * stored as now - its age), so the demo spread stays meaningful whenever the
 * app is launched, on fresh installs and on installs that already had votes.
 * The user's own votes are never touched: their voter id is not a demo id.
 *
 * @param poiVoteDao DAO for the votes table.
 * @param poiDao DAO for the POIs (title -> id resolution and score refresh).
 * @param clock provides "now" in UTC millis; injectable for deterministic tests.
 */
class DemoVoteSeeder(
    private val poiVoteDao: PoiVoteDao,
    private val poiDao: PoiDao,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun ensureSeeded() {
        val poisByTitle = poiDao.allPoisOnce().associateBy { it.title }
        val now = clock()
        DemoGhostVisits.votes.forEach { vote ->
            upsert(vote.voterUserId, vote.poiTitle, vote.voteValue, vote.minutesAgo, poisByTitle, now)
        }
        DemoCityVotes.votes.forEach { vote ->
            upsert(vote.voterUserId, vote.poiTitle, vote.voteValue, vote.minutesAgo, poisByTitle, now)
        }
        // Keep the cached ranking counters in sync with the seeded votes, so
        // every screen (map card, ghost journal, trends) reads the same score.
        poisByTitle.values.forEach { poi ->
            poiDao.updateVotes(poi.id, poiVoteDao.voteScoreForPoiOnce(poi.id).toInt())
        }
    }

    /** Resolves the POI title to its id and upserts one demo vote row. */
    private suspend fun upsert(
        voterUserId: String,
        poiTitle: String,
        voteValue: Int,
        minutesAgo: Long,
        poisByTitle: Map<String, com.example.progmob_progetto_melluso.data.entities.PoiEntity>,
        now: Long
    ) {
        val poi = poisByTitle[poiTitle] ?: return
        poiVoteDao.vote(
            PoiVoteEntity(
                poiId = poi.id,
                voterUserId = voterUserId,
                voteValue = voteValue,
                votedAtUtcMillis = now - minutesAgo * MINUTE_MILLIS
            )
        )
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
