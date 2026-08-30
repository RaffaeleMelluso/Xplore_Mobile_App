package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationTimeSpanEntity
import kotlinx.coroutines.flow.Flow

/**
 * Tables `trending_destinations` + `trending_destination_time_spans`: cached
 * snapshots of a ranking computed ahead of time.
 *
 * Each rank row is linked to the `pois` it ranks (via `poiId`) and to its
 * parent snapshot (`timeSpanId`); a snapshot groups a rank position list with
 * the period (day/week/month/year) it was generated for.
 */
@Dao
interface TrendingDestinationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeSpan(timeSpan: TrendingDestinationTimeSpanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDestinations(destinations: List<TrendingDestinationEntity>)

    @Query("SELECT * FROM trending_destination_time_spans ORDER BY generatedAtUtcMillis DESC")
    fun allTimeSpans(): Flow<List<TrendingDestinationTimeSpanEntity>>

    @Query(
        """
        SELECT * FROM trending_destination_time_spans
        WHERE periodType = :periodType
        ORDER BY generatedAtUtcMillis DESC
        LIMIT 1
        """
    )
    fun latestTimeSpan(periodType: String): Flow<TrendingDestinationTimeSpanEntity?>

    @Query(
        """
        SELECT * FROM trending_destinations
        WHERE timeSpanId = :timeSpanId
        ORDER BY rankPosition ASC
        """
    )
    fun destinationsForTimeSpan(timeSpanId: Long): Flow<List<TrendingDestinationEntity>>
}
