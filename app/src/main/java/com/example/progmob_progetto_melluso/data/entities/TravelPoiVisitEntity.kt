package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a visit of a point of interest during a travel.
 */
@Entity(
    tableName = "travel_poi_visits",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // One visit per (travel, POI): re-passing near the same POI during the
    // same trip must never duplicate the visit row, whatever the call order.
    indices = [Index(value = ["travelId"]), Index(value = ["poiId"]), Index(value = ["travelId", "poiId"], unique = true)]
)
data class TravelPoiVisitEntity(
    // Local database identifier for the visit row.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // Related point of interest.
    val poiId: Long,
    // Time when the POI was visited, stored in UTC.
    val visitedAtUtcMillis: Long,
    // Optional note written by the user.
    val note: String? = null
)
