package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents another user visible on the map as a ghost or presence marker.
 */
@Entity(tableName = "ghosts")
data class GhostEntity(
    // Local database identifier for the ghost marker.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Optional user identifier if the marker is linked to a known user.
    val userId: String?,
    // Latitude of the ghost position.
    val lat: Double,
    // Longitude of the ghost position.
    val lon: Double,
    // Time of the ghost position, stored in UTC.
    val timestamp: Long
)
