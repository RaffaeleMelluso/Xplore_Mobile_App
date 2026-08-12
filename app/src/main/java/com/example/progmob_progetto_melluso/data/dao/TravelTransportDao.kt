package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelTransportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transport: TravelTransportEntity): Long

    @Query("SELECT * FROM travel_transports WHERE travelId = :travelId ORDER BY startUtcMillis ASC")
    fun transportsForTravel(travelId: Long): Flow<List<TravelTransportEntity>>
}
