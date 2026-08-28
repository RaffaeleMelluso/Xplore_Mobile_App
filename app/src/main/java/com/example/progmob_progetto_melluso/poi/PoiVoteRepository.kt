package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the POI up/down votes.
 *
 * Why it exists: every screen that shows a POI (journal visits, the map POI
 * card, the trends ranking) must vote with the SAME rules, so the vote logic
 * lives here once instead of being duplicated per screen. Rules (spec §5 /
 * checklist §5): one vote per user per POI, toggling the same direction again
 * removes the vote, the opposite direction replaces it, and the cached POI
 * counter is refreshed right after so rankings stay consistent.
 */
interface PoiVoteRepository {

    /** Emits all the votes of the signed-in user, so the up/down state of every
     *  POI shown in any screen stays reactive. */
    fun observeMyVotes(): Flow<List<PoiVoteEntity>>

    /**
     * Votes a POI up (+1) or down (-1). Voting the same direction again
     * removes the vote; the opposite direction replaces it. The cached POI
     * score is kept in sync right after.
     */
    suspend fun votePoi(poiId: Long, voteValue: Int)

    /**
     * Removes the signed-in user's vote on a POI, whichever direction it was
     * (used when a travel is deleted: the votes given on its POIs go with it).
     * The cached POI score is kept in sync right after.
     */
    suspend fun removeVote(poiId: Long)

    /**
     * Idempotent demo seed: stores the votes the simulated travelers (ghosts)
     * show in their cards as REAL votes, so trends and ghost cards always
     * agree. No-op when votes already exist.
     */
    suspend fun ensureDemoVotes()
}
