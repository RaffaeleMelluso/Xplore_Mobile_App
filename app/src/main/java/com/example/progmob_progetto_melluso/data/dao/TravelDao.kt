package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDao {
    @Insert
    suspend fun insert(travel: TravelEntity): Long

    /** Updates an existing travel row (e.g. stamps its end time). */
    @Update
    suspend fun update(travel: TravelEntity)

    @Query("SELECT * FROM travels ORDER BY startUtcMillis DESC")
    fun allTravels(): Flow<List<TravelEntity>>

    @Query("SELECT * FROM travels WHERE id = :travelId LIMIT 1")
    fun travelById(travelId: Long): Flow<TravelEntity?>

    /** Emits the single travel still in progress (endUtcMillis is null). via flow*/
    @Query("SELECT * FROM travels WHERE endUtcMillis IS NULL LIMIT 1")
    fun activeTravel(): Flow<TravelEntity?>

    /** One-shot variant used inside the repository while it holds its lock. it's flowless */
    @Query("SELECT * FROM travels WHERE endUtcMillis IS NULL LIMIT 1")
    suspend fun activeTravelOnce(): TravelEntity?
}
