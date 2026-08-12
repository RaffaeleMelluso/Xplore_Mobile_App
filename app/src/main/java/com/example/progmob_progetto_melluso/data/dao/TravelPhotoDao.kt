package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelPhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: TravelPhotoEntity): Long

    @Query("SELECT * FROM travel_photos WHERE travelId = :travelId ORDER BY capturedAtUtcMillis DESC")
    fun photosForTravel(travelId: Long): Flow<List<TravelPhotoEntity>>
}
