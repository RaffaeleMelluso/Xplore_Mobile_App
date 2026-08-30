package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import kotlinx.coroutines.flow.Flow

/** POJO: a travel visit joined with the visited POI's display title. */
data class PoiVisitView(
    val id: Long,
    val travelId: Long,
    val poiId: Long,
    val poiTitle: String?,
    val visitedAtUtcMillis: Long,
    val note: String?
)

/**
 * Table `travel_poi_visits`: the bridge between `travels` and `pois`.
 *
 * One row means "this trip (FK `travelId`, CASCADE) visited this POI (FK
 * `poiId`) at a given time", so the journal detail shows every visited
 * attraction. The object [PoiVisitView] joins the visit with the POI's title.
 */
@Dao
interface TravelPoiVisitDao {
    // IGNORE keeps the FIRST visit when the same (travel, POI) is detected
    // again (also protects against concurrent detections): the unique index
    // on (travelId, poiId) makes duplicates impossible at the DB level.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(visit: TravelPoiVisitEntity): Long

    @Query("SELECT * FROM travel_poi_visits WHERE travelId = :travelId ORDER BY visitedAtUtcMillis DESC")
    fun visitsForTravel(travelId: Long): Flow<List<TravelPoiVisitEntity>>

    @Query("SELECT * FROM travel_poi_visits WHERE poiId = :poiId ORDER BY visitedAtUtcMillis DESC")
    fun visitsForPoi(poiId: Long): Flow<List<TravelPoiVisitEntity>>

    /**
     * One-shot lookup used to keep one visit per (travel, POI): detection is
     * idempotent, re-passing near the same POI does not duplicate the visit.
     */
    @Query("SELECT * FROM travel_poi_visits WHERE travelId = :travelId AND poiId = :poiId LIMIT 1")
    suspend fun visitForTravelAndPoi(travelId: Long, poiId: Long): TravelPoiVisitEntity?

    /** All visits of a travel, one-shot (used before deleting the travel). */
    @Query("SELECT * FROM travel_poi_visits WHERE travelId = :travelId")
    suspend fun visitsForTravelOnce(travelId: Long): List<TravelPoiVisitEntity>

    /** The visits of a travel joined with their POI title, for the journal detail. */
    @Query(
        "SELECT tpv.id, tpv.travelId, tpv.poiId, p.title AS poiTitle, tpv.visitedAtUtcMillis, tpv.note " +
            "FROM travel_poi_visits tpv " +
            "LEFT JOIN pois p ON p.id = tpv.poiId " +
            "WHERE tpv.travelId = :travelId ORDER BY tpv.visitedAtUtcMillis DESC"
    )
    fun visitViewsForTravel(travelId: Long): Flow<List<PoiVisitView>>

    /** One-shot lookup used by the journal before removing a visit. */
    @Query("SELECT * FROM travel_poi_visits WHERE id = :id LIMIT 1")
    suspend fun visitByIdOnce(id: Long): TravelPoiVisitEntity?

    /**
     * Removes a recorded visit from the journal without deleting the POI
     * (spec §7.3: the journal stays editable).
     */
    @Delete
    suspend fun delete(visit: TravelPoiVisitEntity)
}
