package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.GhostEntity

/**
 * Table `ghosts`: simulated nearby travelers shown on the map.
 *
 * It is a self-contained snapshot: each row is one ghost with its own
 * coordinates/path, so it does NOT reference any other table (no FK).
 * Its only consumer is the map screen, which lists recent ghosts.
 */
@Dao
interface GhostDao {
    @Insert
    suspend fun insert(ghost: GhostEntity): Long

    @Query("SELECT * FROM ghosts ORDER BY timestamp DESC")
    fun recentGhosts(): Flow<List<GhostEntity>>
}
