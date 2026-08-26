package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a media item (photo or video) added to a travel diary.
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
data class TravelMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val travelId: Long,
    /** 'photo' or 'video'. */
    val mediaType: String = "photo",
    /** Local file URI of the image/video. */
    val imageUri: String,
    val caption: String?,
    val capturedAtUtcMillis: Long,
    val createdByUserId: String?
)
