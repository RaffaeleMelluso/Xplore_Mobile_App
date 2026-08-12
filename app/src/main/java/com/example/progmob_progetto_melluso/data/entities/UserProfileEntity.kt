package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the local user profile linked to the authentication account.
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["firebaseUserId"], unique = true)]
)
data class UserProfileEntity(
    // Local database identifier for the profile.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Firebase user identifier used to link the profile to the account.
    val firebaseUserId: String,
    // Display name shown in the app.
    val displayName: String?,
    // Optional profile image URL.
    val photoUrl: String?,
    // Default anonymous mode preference for the user.
    val isAnonymousDefault: Boolean = false,
    // Default visibility preference for travel paths.
    val showTravelPathsByDefault: Boolean = true,
    // Profile creation time stored in UTC.
    val createdAtUtcMillis: Long
)
