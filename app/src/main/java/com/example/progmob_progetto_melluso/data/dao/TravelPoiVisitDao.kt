package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelPoiVisitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: TravelPoiVisitEntity): Long

    @Query("SELECT * FROM travel_poi_visits WHERE travelId = :travelId ORDER BY visitedAtUtcMillis DESC")
    fun visitsForTravel(travelId: Long): Flow<List<TravelPoiVisitEntity>>

    @Query("SELECT * FROM travel_poi_visits WHERE poiId = :poiId ORDER BY visitedAtUtcMillis DESC")
    fun visitsForPoi(poiId: Long): Flow<List<TravelPoiVisitEntity>>
}
