package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `travel_transports`: a transport leg reported inside a trip (bus,
 * tram, metro, ferry, walking) with an optional line number.
 *
 * It is a child of `travels` (`travelId` FK, CASCADE): one travel can have
 * several transport segments, shown in the journal detail.
 */
@Dao
interface TravelTransportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transport: TravelTransportEntity): Long

    @Query("SELECT * FROM travel_transports WHERE travelId = :travelId ORDER BY startUtcMillis ASC")
    fun transportsForTravel(travelId: Long): Flow<List<TravelTransportEntity>>

    /** One transport segment by id, used before deleting it. */
    @Query("SELECT * FROM travel_transports WHERE id = :id LIMIT 1")
    suspend fun transportById(id: Long): TravelTransportEntity?

    @Query("DELETE FROM travel_transports WHERE id = :id")
    suspend fun deleteById(id: Long)
}
