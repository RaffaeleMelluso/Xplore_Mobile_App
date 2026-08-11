package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.GhostEntity

@Dao
interface GhostDao {
    @Insert
    suspend fun insert(ghost: GhostEntity): Long

    @Query("SELECT * FROM ghosts ORDER BY timestamp DESC")
    fun recentGhosts(): Flow<List<GhostEntity>>
}
