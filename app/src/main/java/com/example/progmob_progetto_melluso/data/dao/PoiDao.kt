package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.PoiEntity

@Dao
interface PoiDao {
    @Insert
    suspend fun insert(poi: PoiEntity): Long

    @Query("SELECT * FROM pois ORDER BY id DESC")
    fun allPois(): Flow<List<PoiEntity>>

    /** One-shot variant used by the visit detection while recording. */
    @Query("SELECT * FROM pois")
    suspend fun allPoisOnce(): List<PoiEntity>

    /** Refreshes the cached ranking counter after a vote. */
    @Query("UPDATE pois SET votes = :votes WHERE id = :id")
    suspend fun updateVotes(id: Long, votes: Int)

    @Query("SELECT COUNT(*) FROM pois")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pois WHERE title = :title")
    suspend fun countByTitle(title: String): Int
}
