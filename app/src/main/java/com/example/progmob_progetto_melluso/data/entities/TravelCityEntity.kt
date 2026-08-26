package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a distinct place (e.g. a city) visited during a travel.
 *
 * How it works: the journal groups the recorded path points into clusters that
 * are far enough from each other to count as different destinations. Each
 * cluster becomes a row here, with its centroid coordinates and the moment the
 * user first arrived. The name is editable: the user can correct the
 * auto-generated label (e.g. a city name).
 */
@Entity(
    tableName = "travel_cities",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId"]), Index(value = ["arrivedAtUtcMillis"])]
)
data class TravelCityEntity(
    // Local database identifier for the city row.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // User-editable name of the destination (defaults to an auto label).
    val name: String,
    // Centroid latitude of the visited cluster.
    val lat: Double,
    // Centroid longitude of the visited cluster.
    val lon: Double,
    // When the user arrived in this place, stored in UTC (earliest point of the cluster).
    val arrivedAtUtcMillis: Long
)