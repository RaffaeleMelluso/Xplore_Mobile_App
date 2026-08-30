package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `travel_photos`: the photos/videos attached to the journal.
 *
 * It is a child of `travels` (`travelId` FK, CASCADE). Note the DAO only
 * deletes the DB row, not the media file on disk (kept on purpose).
 */
@Dao
interface TravelMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: TravelMediaEntity): Long

    @Query("SELECT * FROM travel_photos WHERE travelId = :travelId ORDER BY capturedAtUtcMillis DESC")
    fun mediaForTravel(travelId: Long): Flow<List<TravelMediaEntity>>

    /** One-shot lookup used by the journal before deleting a media item. */
    @Query("SELECT * FROM travel_photos WHERE id = :id LIMIT 1")
    suspend fun mediaById(id: Long): TravelMediaEntity?

    /** Removes a photo/video from the travel journal (the file itself is kept). */
    @Delete
    suspend fun delete(media: TravelMediaEntity)
}
