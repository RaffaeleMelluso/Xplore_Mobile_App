package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One tracked-duration session block of a travel (the traveled-duration
 * requirement): the wall-clock window while the trip was being TRACKED (the
 * foreground tracking service running, which also covers the app being in the
 * background). The traveled duration of a trip is the sum of its blocks
 * (ongoing trips add the segment that is open right now).
 *
 * Why a table and not a counter: the window is persisted per trip, so the
 * duration survives process restarts and is auditable; deleting a travel
 * cascades to its blocks like every other trip child.
 *
 * The blocks of one travel never overlap: a new segment can only start after
 * the previous one ended (start/endSession, stopTravel).
 */
@Entity(
    tableName = "travel_session_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("travelId")]
)
data class TravelSessionBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Travel this tracked window belongs to.
    val travelId: Long,
    // Start of the window in UTC millis (when tracking of the trip started).
    val fromUtcMillis: Long,
    // End of the window in UTC millis (when tracking stopped / the trip was stopped).
    val toUtcMillis: Long
)
