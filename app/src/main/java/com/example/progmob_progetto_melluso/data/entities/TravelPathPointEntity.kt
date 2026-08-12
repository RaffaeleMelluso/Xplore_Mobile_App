package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a single point of the travel path for map rendering or path history.
 */
@Entity(
    tableName = "travel_path_points",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId"]), Index(value = ["recordedAtUtcMillis"])]
)
data class TravelPathPointEntity(
    // Local database identifier for the path point.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // Latitude of the recorded point.
    val latitude: Double,
    // Longitude of the recorded point.
    val longitude: Double,
    // Time when the point was recorded, stored in UTC.
    val recordedAtUtcMillis: Long,
    // Optional location accuracy in meters.
    val accuracyMeters: Float? = null
)
