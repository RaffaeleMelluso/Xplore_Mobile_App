package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed [PoiVoteRepository].
 *
 * How it works: it wraps [PoiVoteDao] (the votes table) and [PoiDao] (the
 * cached score kept in sync), keyed by the signed-in user id provided by
 * [voterIdProvider]. The voter identity is injected so tests can substitute a
 * fixed id and the clock is injectable for deterministic vote timestamps.
 *
 * @param poiVoteDao DAO for the votes table.
 * @param poiDao DAO for the POIs (cached score refresh).
 * @param voterIdProvider supplies the signed-in user id for the votes.
 * @param clock provides "now" in UTC millis; injectable for deterministic tests.
 */
class PoiVoteRepositoryImpl(
    private val poiVoteDao: PoiVoteDao,
    private val poiDao: PoiDao,
    private val voterIdProvider: () -> String,
    private val clock: () -> Long = System::currentTimeMillis
) : PoiVoteRepository {

    override fun observeMyVotes(): Flow<List<PoiVoteEntity>> =
        poiVoteDao.myVotes(voterIdProvider())

    override suspend fun votePoi(poiId: Long, voteValue: Int) {
        require(voteValue == 1 || voteValue == -1) { "voteValue must be +1 or -1" }
        val voterId = voterIdProvider()
        val current = poiVoteDao.myVoteOnce(poiId, voterId)
        when {
            // Never voted: record the vote.
            current == null -> poiVoteDao.vote(newVote(poiId, voterId, voteValue))
            // Same vote again: toggle it off.
            current == voteValue -> poiVoteDao.removeVote(poiId, voterId)
            // Opposite vote: replace it.
            else -> poiVoteDao.vote(newVote(poiId, voterId, voteValue))
        }
        // Keep the cached ranking counter on the POI row in sync, so every
        // screen (journal, map, trends) reads the same score.
        poiDao.updateVotes(poiId, poiVoteDao.voteScoreForPoiOnce(poiId).toInt())
    }

    override suspend fun removeVote(poiId: Long) {
        poiVoteDao.removeVote(poiId, voterIdProvider())
        // Keep the cached ranking counter on the POI row in sync, so every
        // screen (journal, map, trends) reads the same score.
        poiDao.updateVotes(poiId, poiVoteDao.voteScoreForPoiOnce(poiId).toInt())
    }

    override suspend fun ensureDemoVotes() =
        demoVoteSeeder.ensureSeeded()

    private val demoVoteSeeder = DemoVoteSeeder(
        poiVoteDao = poiVoteDao,
        poiDao = poiDao,
        clock = clock
    )

    private fun newVote(poiId: Long, voterId: String, voteValue: Int): PoiVoteEntity =
        PoiVoteEntity(
            poiId = poiId,
            voterUserId = voterId,
            voteValue = voteValue,
            votedAtUtcMillis = clock()
        )
}
