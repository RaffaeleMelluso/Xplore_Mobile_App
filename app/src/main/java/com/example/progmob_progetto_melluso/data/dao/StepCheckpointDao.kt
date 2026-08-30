package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `step_checkpoints`: the pedometer state of one trip.
 *
 * It is a child of `travels` (`travelId` FK, CASCADE): at most one row per
 * travel, used to keep the rolling step count while the trip is active.
 */
@Dao
interface StepCheckpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: StepCheckpointEntity)

    @Query("SELECT * FROM step_checkpoints WHERE travelId = :travelId LIMIT 1")
    fun checkpointForTravel(travelId: Long): Flow<StepCheckpointEntity?>
}
