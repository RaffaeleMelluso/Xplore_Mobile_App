package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.ghost.DemoGhostVisits

/**
 * Seeds the demo community votes as REAL votes.
 *
 * How it works: the simulated travelers ("ghosts") carry their visits/votes in
 * [DemoGhostVisits]; this seeder turns every visit with a vote into a
 * `poi_votes` row (resolving the POI title to its id), so the trends page
 * aggregates exactly the votes the ghost cards display. Idempotent: when any
 * vote already exists it does nothing, so it is safe to call on every launch.
 * Vote timestamps are anchored to "now" (minus each visit's age), so the
 * day/week/month/year trend windows reflect the sample data spread.
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
        if (poiVoteDao.count() > 0L) return
        val poisByTitle = poiDao.allPoisOnce().associateBy { it.title }
        val now = clock()
        DemoGhostVisits.votes.forEach { vote ->
            val poi = poisByTitle[vote.poiTitle] ?: return@forEach
            poiVoteDao.vote(
                PoiVoteEntity(
                    poiId = poi.id,
                    voterUserId = vote.voterUserId,
                    voteValue = vote.voteValue,
                    votedAtUtcMillis = now - vote.minutesAgo * MINUTE_MILLIS
                )
            )
        }
        // Keep the cached ranking counters in sync with the fresh votes.
        poisByTitle.values.forEach { poi ->
            poiDao.updateVotes(poi.id, poiVoteDao.voteScoreForPoiOnce(poi.id).toInt())
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
