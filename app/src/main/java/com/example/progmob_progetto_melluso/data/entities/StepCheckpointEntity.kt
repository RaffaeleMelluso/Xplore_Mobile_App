package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the last cumulative step sensor value for a travel.
 */
@Entity(
    tableName = "step_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId"], unique = true)]
)
data class StepCheckpointEntity(
    // The travel this checkpoint belongs to.
    @PrimaryKey val travelId: Long,
    // Last value returned by the cumulative step sensor.
    val lastSensorValue: Long,
    // Time of the checkpoint stored in UTC.
    val lastCheckpointUtcMillis: Long,
    // Local date at the time the checkpoint was written.
    val lastKnownLocalDate: String,
    // Time zone used when the checkpoint was created.
    val lastKnownTimeZoneId: String
)
