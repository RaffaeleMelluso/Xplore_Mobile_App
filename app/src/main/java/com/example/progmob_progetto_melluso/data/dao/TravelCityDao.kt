package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `travel_cities`: the destinations detected/reported inside a trip.
 *
 * It is a child of `travels` (`travelId` FK, CASCADE): one travel can visit
 * several cities in order of arrival, and the map filter uses these rows to
 * label each trip with its destinations.
 */
@Dao
interface TravelCityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: TravelCityEntity): Long

    /** Updates a city row, for example when the user renames the destination. */
    @Update
    suspend fun update(city: TravelCityEntity)

    @Query("SELECT * FROM travel_cities WHERE travelId = :travelId ORDER BY arrivedAtUtcMillis ASC")
    fun citiesForTravel(travelId: Long): Flow<List<TravelCityEntity>>

    /** One-shot variant used by the journal to reconcile detected cities. */
    @Query("SELECT * FROM travel_cities WHERE travelId = :travelId ORDER BY arrivedAtUtcMillis ASC")
    suspend fun citiesForTravelOnce(travelId: Long): List<TravelCityEntity>

    /** One-shot lookup used by the journal before renaming a destination. */
    @Query("SELECT * FROM travel_cities WHERE id = :id LIMIT 1")
    suspend fun cityByIdOnce(id: Long): TravelCityEntity?

    @Query("DELETE FROM travel_cities WHERE travelId = :travelId")
    suspend fun deleteForTravel(travelId: Long)

    /**
     * All detected destinations across every travel, used by the map filter to
     * label each trip with the cities it visited (grouped in the ViewModel).
     */
    @Query("SELECT * FROM travel_cities ORDER BY travelId, arrivedAtUtcMillis ASC")
    fun allCities(): Flow<List<TravelCityEntity>>
}