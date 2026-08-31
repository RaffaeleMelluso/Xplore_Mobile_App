package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `travels`: the trips recorded by the user (the journal).
 *
 * It is the parent of the whole travel cluster. Deleting a travel cascades to
 * everything that belongs to it: `travel_cities`, `travel_path_points`,
 * `travel_photos`, `travel_poi_visits`, `travel_transports` and the `step_*`
 * row, all linked by `travelId` with ON DELETE CASCADE.
 */
@Dao
interface TravelDao {
    @Insert
    suspend fun insert(travel: TravelEntity): Long

    /** Updates an existing travel row (e.g. stamps its end time). */
    @Update
    suspend fun update(travel: TravelEntity)

    @Query("SELECT * FROM travels ORDER BY startUtcMillis DESC")
    fun allTravels(): Flow<List<TravelEntity>>

    /** All travels of one user (the journal list is per-account). */
    @Query("SELECT * FROM travels WHERE userId = :userId ORDER BY startUtcMillis DESC")
    fun allTravelsForUser(userId: String): Flow<List<TravelEntity>>

    @Query("SELECT * FROM travels WHERE id = :travelId LIMIT 1")
    fun travelById(travelId: Long): Flow<TravelEntity?>

    /** One-shot variant used by the journal when editing a travel. */
    @Query("SELECT * FROM travels WHERE id = :travelId LIMIT 1")
    suspend fun travelByIdOnce(travelId: Long): TravelEntity?

    /**
     * The most recently closed travel: used by the journal right after a stop
     * to finalize the destinations and the POI visits of the trip just ended.
     */
    @Query("SELECT * FROM travels WHERE endUtcMillis IS NOT NULL ORDER BY endUtcMillis DESC LIMIT 1")
    suspend fun mostRecentClosedTravelOnce(): TravelEntity?

    /** Most recently closed travel of one user (per-account finalize). */
    @Query(
        "SELECT * FROM travels WHERE userId = :userId AND endUtcMillis IS NOT NULL " +
            "ORDER BY endUtcMillis DESC LIMIT 1"
    )
    suspend fun mostRecentClosedTravelOnceForUser(userId: String): TravelEntity?

    /**
     * Deletes a travel. Everything linked to it (path points, photos, cities,
     * POI visits) is removed by the ON DELETE CASCADE foreign keys.
     */
    @Query("DELETE FROM travels WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Emits the single travel still in progress (endUtcMillis is null). via flow*/
    @Query("SELECT * FROM travels WHERE endUtcMillis IS NULL LIMIT 1")
    fun activeTravel(): Flow<TravelEntity?>

    /** One-shot variant used inside the repository while it holds its lock. it's flowless */
    @Query("SELECT * FROM travels WHERE endUtcMillis IS NULL LIMIT 1")
    suspend fun activeTravelOnce(): TravelEntity?

    /** Active travel of one user (per-account single-active invariant). */
    @Query("SELECT * FROM travels WHERE userId = :userId AND endUtcMillis IS NULL LIMIT 1")
    fun activeTravelForUser(userId: String): Flow<TravelEntity?>

    /** One-shot variant of [activeTravelForUser]. */
    @Query("SELECT * FROM travels WHERE userId = :userId AND endUtcMillis IS NULL LIMIT 1")
    suspend fun activeTravelForUserOnce(userId: String): TravelEntity?

    /**
     * Adopts the travels recorded before the per-user isolation (v11) into the
     * first user who opens the app after the update: their old data is not
     * lost, and any later account starts with an empty journal.
     */
    @Query("UPDATE travels SET userId = :userId WHERE userId IS NULL")
    suspend fun claimLegacyTravels(userId: String)
}
