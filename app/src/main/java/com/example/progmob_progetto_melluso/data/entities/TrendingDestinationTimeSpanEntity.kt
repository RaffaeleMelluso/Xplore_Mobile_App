package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the time window used to generate a ranking of trending destinations.
 */
@Entity(tableName = "trending_destination_time_spans")
data class TrendingDestinationTimeSpanEntity(
    // Local database identifier for the ranking period.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Period label used by the app, for example week, month or year.
    val periodType: String,
    // Start of the period in UTC.
    val periodStartUtcMillis: Long,
    // End of the period in UTC.
    val periodEndUtcMillis: Long,
    // Time when this ranking snapshot was generated.
    val generatedAtUtcMillis: Long
)
