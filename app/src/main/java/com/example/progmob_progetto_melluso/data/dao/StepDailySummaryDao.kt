package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `step_daily_summaries`: the per-day pedometer totals of a trip.
 *
 * It is a child of `travels` (`travelId` FK, CASCADE): one travel can gather
 * several daily summaries (steps + distance per local date), aggregated by
 * [totalStepsForTravel]/[totalDistanceForTravel] for the journal.
 */
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

    /** One-shot variant used by the pedometer to accumulate into one day bucket. */
    @Query(
        """
        SELECT * FROM step_daily_summaries
        WHERE travelId = :travelId AND localDate = :localDate AND timeZoneId = :timeZoneId
        LIMIT 1
        """
    )
    suspend fun summaryForTravelAndDateOnce(
        travelId: Long,
        localDate: String,
        timeZoneId: String
    ): StepDailySummaryEntity?

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
