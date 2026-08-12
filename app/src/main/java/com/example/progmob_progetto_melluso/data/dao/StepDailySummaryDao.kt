package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: StepDailySummaryEntity)

    @Query(
        """
        SELECT * FROM step_daily_summaries
        WHERE travelId = :travelId
        ORDER BY localDate ASC
        """
    )
    fun summariesForTravel(travelId: Long): Flow<List<StepDailySummaryEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(steps), 0) FROM step_daily_summaries
        WHERE travelId = :travelId
        """
    )
    fun totalStepsForTravel(travelId: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(distanceMeters), 0) FROM step_daily_summaries
        WHERE travelId = :travelId
        """
    )
    fun totalDistanceForTravel(travelId: Long): Flow<Double>
}
