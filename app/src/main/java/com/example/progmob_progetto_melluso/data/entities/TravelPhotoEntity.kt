package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a photo added to a travel diary.
 */
@Entity(
    tableName = "travel_photos",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId"]), Index(value = ["capturedAtUtcMillis"])]
)
data class TravelPhotoEntity(
    // Local database identifier for the photo.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // Uri or path of the image stored locally or remotely.
    val imageUri: String,
    // Optional caption for the photo.
    val caption: String?,
    // Photo capture time stored in UTC.
    val capturedAtUtcMillis: Long,
    // Optional user identifier of who uploaded the photo.
    val createdByUserId: String?
)
