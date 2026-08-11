package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "travels")
data class TravelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val title: String?,
    val isAnonymous: Boolean = false
)
