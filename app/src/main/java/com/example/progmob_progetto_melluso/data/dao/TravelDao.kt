package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.TravelEntity

@Dao
interface TravelDao {
    @Insert
    suspend fun insert(travel: TravelEntity): Long

    @Query("SELECT * FROM travels ORDER BY startTimestamp DESC")
    fun allTravels(): Flow<List<TravelEntity>>
}
