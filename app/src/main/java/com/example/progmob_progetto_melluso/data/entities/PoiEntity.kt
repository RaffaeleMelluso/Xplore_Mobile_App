package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a point of interest suggested or stored by the app.
 */
@Entity(tableName = "pois")
data class PoiEntity(
    // Local database identifier for the POI.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Latitude of the point of interest.
    val lat: Double,
    // Longitude of the point of interest.
    val lon: Double,
    // Main title shown in the UI.
    val title: String,
    // Optional description or note about the place.
    val description: String?,
    // Optional category such as museum, restaurant or attraction.
    val category: String?,
    // Optional identifier of the user that created the POI.
    val createdByUserId: String?,
    // City the POI belongs to, used by the trends ranking (city scope).
    val city: String? = null,
    // Cached vote counter for quick ranking in the UI.
    val votes: Int = 0
)
