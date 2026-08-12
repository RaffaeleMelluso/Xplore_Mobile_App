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
}
