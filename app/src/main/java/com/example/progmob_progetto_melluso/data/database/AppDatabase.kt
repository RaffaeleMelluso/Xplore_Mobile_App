package com.example.progmob_progetto_melluso.data.database


import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.dao.StepCheckpointDao
import com.example.progmob_progetto_melluso.data.dao.StepDailySummaryDao
import com.example.progmob_progetto_melluso.data.dao.GhostDao
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TrendingDestinationDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelPhotoDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.dao.TravelTransportDao
import com.example.progmob_progetto_melluso.data.dao.UserProfileDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPhotoEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationTimeSpanEntity
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity

@Database(
    entities = [
        TravelEntity::class,
        UserProfileEntity::class,
        StepDailySummaryEntity::class,
        StepCheckpointEntity::class,
        TrendingDestinationTimeSpanEntity::class,
        TrendingDestinationEntity::class,
        TravelPhotoEntity::class,
        TravelTransportEntity::class,
        TravelPoiVisitEntity::class,
        TravelPathPointEntity::class,
        PoiVoteEntity::class,
        PoiEntity::class,
        GhostEntity::class
    ],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun travelDao(): TravelDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun stepDailySummaryDao(): StepDailySummaryDao
    abstract fun stepCheckpointDao(): StepCheckpointDao
    abstract fun trendingDestinationDao(): TrendingDestinationDao
    abstract fun travelPhotoDao(): TravelPhotoDao
    abstract fun travelTransportDao(): TravelTransportDao
    abstract fun travelPoiVisitDao(): TravelPoiVisitDao
    abstract fun travelPathPointDao(): TravelPathPointDao
    abstract fun poiVoteDao(): PoiVoteDao
    abstract fun poiDao(): PoiDao
    abstract fun ghostDao(): GhostDao
}