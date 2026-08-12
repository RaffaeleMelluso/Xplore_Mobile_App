package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the daily step summary for a single travel day.
 */
@Entity(
    tableName = "step_daily_summaries",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId", "localDate", "timeZoneId"], unique = true)]
)
data class StepDailySummaryEntity(
    // Local database identifier for the daily summary row.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // Trip-local date in ISO format, derived from UTC plus the travel time zone.
    val localDate: String,
    // Time zone used to compute this daily bucket.
    val timeZoneId: String,
    // Number of steps accumulated in this trip-local day.
    val steps: Long,
    // Estimated distance covered in meters during the day.
    val distanceMeters: Double,
    // Last cumulative sensor reading used to compute this summary.
    val lastSensorValue: Long,
    // Last update time stored in UTC.
    val updatedAtUtcMillis: Long
)
