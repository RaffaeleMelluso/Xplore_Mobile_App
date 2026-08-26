package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiVoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun vote(vote: PoiVoteEntity): Long

    @Query("SELECT * FROM poi_votes WHERE poiId = :poiId ORDER BY votedAtUtcMillis DESC")
    fun votesForPoi(poiId: Long): Flow<List<PoiVoteEntity>>

    @Query("SELECT COALESCE(SUM(voteValue), 0) FROM poi_votes WHERE poiId = :poiId")
    fun voteScoreForPoi(poiId: Long): Flow<Long>

    /**
     * All the votes of the signed-in user, so the journal can highlight the
     * up/down state of every visited POI reactively.
     */
    @Query("SELECT * FROM poi_votes WHERE voterUserId = :voterId")
    fun myVotes(voterId: String): Flow<List<PoiVoteEntity>>

    /** The current vote of the user on a POI (null when never voted). */
    @Query("SELECT voteValue FROM poi_votes WHERE poiId = :poiId AND voterUserId = :voterId LIMIT 1")
    suspend fun myVoteOnce(poiId: Long, voterId: String): Int?

    /** One-shot score used to refresh the cached POI counter after voting. */
    @Query("SELECT COALESCE(SUM(voteValue), 0) FROM poi_votes WHERE poiId = :poiId")
    suspend fun voteScoreForPoiOnce(poiId: Long): Long

    /** Removes the user's vote (toggling the same vote off again). */
    @Query("DELETE FROM poi_votes WHERE poiId = :poiId AND voterUserId = :voterId")
    suspend fun removeVote(poiId: Long, voterId: String)
}
