package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val lat: Double,
    val lon: Double,
    val title: String,
    val description: String?,
    val category: String?,
    val createdByUserId: String?,
    val votes: Int = 0
)
