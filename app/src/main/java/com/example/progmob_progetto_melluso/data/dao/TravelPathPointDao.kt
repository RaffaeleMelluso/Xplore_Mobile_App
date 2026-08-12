package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelPathPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: TravelPathPointEntity): Long

    @Query("SELECT * FROM travel_path_points WHERE travelId = :travelId ORDER BY recordedAtUtcMillis ASC")
    fun pointsForTravel(travelId: Long): Flow<List<TravelPathPointEntity>>
}
