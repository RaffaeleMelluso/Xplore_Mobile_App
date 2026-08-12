package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a transport segment used during a travel.
 */
@Entity(
    tableName = "travel_transports",
    foreignKeys = [
        ForeignKey(
            entity = TravelEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["travelId"]), Index(value = ["transportType"])]
)
data class TravelTransportEntity(
    // Local database identifier for the transport segment.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Related travel session.
    val travelId: Long,
    // Transport type, for example bus, train, plane or metro.
    val transportType: String,
    // Optional transport line or route name.
    val lineName: String?,
    // Optional departure stop or station.
    val departureName: String?,
    // Optional arrival stop or station.
    val arrivalName: String?,
    // Start time of the transport segment in UTC.
    val startUtcMillis: Long,
    // End time of the transport segment in UTC, if available.
    val endUtcMillis: Long?
)
