package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelSessionBlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `travel_session_blocks`: the open-app windows of each travel (the
 * traveled-duration requirement). The traveled duration of a trip is the sum
 * of its blocks, which excludes the time the app stayed closed.
 */
@Dao
interface TravelSessionBlockDao {

    @Insert
    suspend fun insert(block: TravelSessionBlockEntity): Long

    /** All the stored open-app blocks of one travel, oldest first. */
    @Query("SELECT * FROM travel_session_blocks WHERE travelId = :travelId ORDER BY fromUtcMillis ASC")
    fun blocksForTravel(travelId: Long): Flow<List<TravelSessionBlockEntity>>

    /** One-shot variant used by the duration aggregation in tests. */
    @Query("SELECT * FROM travel_session_blocks WHERE travelId = :travelId ORDER BY fromUtcMillis ASC")
    suspend fun blocksForTravelOnce(travelId: Long): List<TravelSessionBlockEntity>
}
