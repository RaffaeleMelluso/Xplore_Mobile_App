package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents one ranked destination inside a trending time span.
 */
@Entity(
    tableName = "trending_destinations",
    foreignKeys = [
        ForeignKey(
            entity = TrendingDestinationTimeSpanEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeSpanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timeSpanId", "rankPosition"], unique = true)]
)
data class TrendingDestinationEntity(
    // Local database identifier for the ranked destination row.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Parent ranking period.
    val timeSpanId: Long,
    // Position in the ranking, from 1 to 30.
    val rankPosition: Int,
    // Destination name shown to the user.
    val destinationName: String,
    // Optional country or macro-area of the destination.
    val destinationCountry: String? = null,
    // Number of travels recorded for this destination in the period.
    val travelCount: Long,
    // Score used to sort the ranking when needed.
    val score: Double
)
