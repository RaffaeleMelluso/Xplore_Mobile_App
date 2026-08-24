package com.example.progmob_progetto_melluso.core

import android.content.Context
import androidx.room.Room
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.FirebaseAuthRepository
import com.example.progmob_progetto_melluso.data.database.AppDatabase
import com.example.progmob_progetto_melluso.map.DemoPoiSeed
import com.example.progmob_progetto_melluso.map.GhostSimulator
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.map.MapRepositoryImpl
import com.example.progmob_progetto_melluso.service.LocationUpdates
import com.example.progmob_progetto_melluso.travel.TravelRepository
import com.example.progmob_progetto_melluso.travel.TravelRepositoryImpl
import java.util.TimeZone

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null

    fun database(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "xplore.db"
            ).build().also { database = it }
        }
    }

    fun authRepository(context: Context): AuthRepository {
        return FirebaseAuthRepository(
            userProfileDao = database(context).userProfileDao()
        )
    }

    /** Travel repository wired to the Room database and the device timezone. */
    fun travelRepository(context: Context): TravelRepository {
        return TravelRepositoryImpl(
            travelDao = database(context).travelDao(),
            pathPointDao = database(context).travelPathPointDao(),
            timeZoneIdProvider = { TimeZone.getDefault().id }
        )
    }

    /**
     * Map repository wired to the sensor stream, the travel repository and the
     * demo POI seed. Ghosts are simulated locally until a real backend exists.
     */
    fun mapRepository(context: Context): MapRepository {
        return MapRepositoryImpl(
            locationFlow = LocationUpdates.samples,
            poiDao = database(context).poiDao(),
            travelRepository = travelRepository(context),
            ghostSimulator = GhostSimulator(),
            poiSeed = DemoPoiSeed.turin + DemoPoiSeed.cerignola
        )
    }
}
