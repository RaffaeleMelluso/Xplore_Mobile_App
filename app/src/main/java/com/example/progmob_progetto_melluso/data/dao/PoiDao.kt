package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.PoiEntity

@Dao
interface PoiDao {
    @Insert
    suspend fun insert(poi: PoiEntity): Long

    @Query("SELECT * FROM pois ORDER BY id DESC")
    fun allPois(): Flow<List<PoiEntity>>

    /** One-shot variant used by the visit detection while recording. */
    @Query("SELECT * FROM pois")
    suspend fun allPoisOnce(): List<PoiEntity>

    /** Refreshes the cached ranking counter after a vote. */
    @Query("UPDATE pois SET votes = :votes WHERE id = :id")
    suspend fun updateVotes(id: Long, votes: Int)

    @Query("SELECT COUNT(*) FROM pois")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pois WHERE title = :title")
    suspend fun countByTitle(title: String): Int

    /** The single POI by id, or null (ownership checks before deleting). */
    @Query("SELECT * FROM pois WHERE id = :id LIMIT 1")
    suspend fun byIdOnce(id: Long): PoiEntity?

    /** Reactive list of the POIs created by one user (the "my POIs" screen). */
    @Query("SELECT * FROM pois WHERE createdByUserId = :userId ORDER BY id DESC")
    fun myPois(userId: String): Flow<List<PoiEntity>>

    /** Removes a POI; its votes go with it (FK CASCADE on poi_votes). */
    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Heals demo POIs stored before the photo column existed (or with a stale
     * seed URL): replaces the imageUrl only when the row has none or still
     * points at a Commons seed link. User-chosen photos (content:// and
     * file:// URIs) are never touched, so the seed refresh cannot overwrite
     * what the user picked from the gallery.
     */
    @Query(
        "UPDATE pois SET imageUrl = :imageUrl " +
            "WHERE title = :title AND (imageUrl IS NULL OR imageUrl = '' " +
            "OR imageUrl LIKE 'https://commons.wikimedia.org/%')"
    )
    suspend fun updateImageUrlFromSeed(title: String, imageUrl: String)
}
