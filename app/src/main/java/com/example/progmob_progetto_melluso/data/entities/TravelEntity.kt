package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a travel session tracked by the app.
 */
@Entity(tableName = "travels")
data class TravelEntity(
    // Local database identifier for the travel.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Travel start time stored in UTC.
    val startUtcMillis: Long,
    // Travel end time stored in UTC, if the travel is still ongoing this stays null.
    val endUtcMillis: Long?,
    // Time zone used to show travel days correctly while the user moves across countries.
    val travelTimeZoneId: String,
    // Optional title chosen by the user for the travel.
    val title: String?,
    // Optional free-text diary note (what the user saw, in their own words).
    val description: String? = null,
    // True when the travel should not expose the identity of the owner.
    val isAnonymous: Boolean = false,
    // Owner of the travel (Firebase uid, or "local-user" when anonymous).
    // Null only for rows created before the per-user isolation (v11) and
    // claimed by the first user who opens the app after the update.
    val userId: String? = null
)
