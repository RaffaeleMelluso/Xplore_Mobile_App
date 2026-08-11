package com.example.progmob_progetto_melluso.data.database


import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.progmob_progetto_melluso.data.dao.GhostDao
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity

@Database(entities = [TravelEntity::class, PoiEntity::class, GhostEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun travelDao(): TravelDao
    abstract fun poiDao(): PoiDao
    abstract fun ghostDao(): GhostDao
}