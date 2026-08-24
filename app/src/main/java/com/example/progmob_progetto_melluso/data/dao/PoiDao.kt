package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.progmob_progetto_melluso.data.entities.PoiEntity

@Dao
interface PoiDao {
    @Insert
    suspend fun insert(poi: PoiEntity): Long

    @Query("SELECT * FROM pois ORDER BY id DESC")
    fun allPois(): Flow<List<PoiEntity>>

    @Query("SELECT COUNT(*) FROM pois")
    suspend fun count(): Int
}
