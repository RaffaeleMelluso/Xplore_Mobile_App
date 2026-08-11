package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ghosts")
data class GhostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String?,
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)
