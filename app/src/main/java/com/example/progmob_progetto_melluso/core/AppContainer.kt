package com.example.progmob_progetto_melluso.core

import android.content.Context
import androidx.room.Room
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.FirebaseAuthRepository
import com.example.progmob_progetto_melluso.data.database.AppDatabase
import com.example.progmob_progetto_melluso.journal.TravelJournalRepository
import com.example.progmob_progetto_melluso.journal.TravelJournalRepositoryImpl
import com.example.progmob_progetto_melluso.map.DemoPoiSeed
import com.example.progmob_progetto_melluso.map.GhostSimulator
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.map.MapRepositoryImpl
import com.example.progmob_progetto_melluso.service.LocationUpdates
import com.example.progmob_progetto_melluso.travel.TravelCityDetector
import com.example.progmob_progetto_melluso.travel.TravelRepository
import com.example.progmob_progetto_melluso.travel.TravelRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
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
            )
                .addMigrations(AppDatabase.MIGRATION_5_6)
                .build()
                .also { database = it }
        }
    }

    fun authRepository(context: Context): AuthRepository {
        return FirebaseAuthRepository(
            userProfileDao = database(context).userProfileDao()
        )
    }

    /** Travel repository wired to the Room database and the device timezone. */
    fun travelRepository(context: Context): TravelRepository {
        val db = database(context)
        return TravelRepositoryImpl(
            travelDao = db.travelDao(),
            pathPointDao = db.travelPathPointDao(),
            poiDao = db.poiDao(),
            travelPoiVisitDao = db.travelPoiVisitDao(),
            timeZoneIdProvider = { TimeZone.getDefault().id }
        )
    }

    /**
     * Map repository wired to the sensor stream, the travel repository and the
     * demo POI seed. Ghosts are simulated locally until a real backend exists.
     */
    fun mapRepository(context: Context): MapRepository {
        val db = database(context)
        return MapRepositoryImpl(
            locationFlow = LocationUpdates.samples,
            poiDao = db.poiDao(),
            travelCityDao = db.travelCityDao(),
            travelRepository = travelRepository(context),
            ghostSimulator = GhostSimulator(),
            poiSeed = DemoPoiSeed.turin + DemoPoiSeed.cerignola
        )
    }

    /**
     * Journal repository wired to the Room database and the signed-in user
     * (Firebase uid, falling back to a local id when anonymous).
     */
    fun travelJournalRepository(context: Context): TravelJournalRepository {
        val db = database(context)
        return TravelJournalRepositoryImpl(
            travelDao = db.travelDao(),
            travelMediaDao = db.travelMediaDao(),
            travelCityDao = db.travelCityDao(),
            travelPoiVisitDao = db.travelPoiVisitDao(),
            pathPointDao = db.travelPathPointDao(),
            poiDao = db.poiDao(),
            poiVoteDao = db.poiVoteDao(),
            travelRepository = travelRepository(context),
            cityDetector = TravelCityDetector(),
            voterIdProvider = {
                FirebaseAuth.getInstance().currentUser?.uid ?: "local-user"
            },
            mediaFilesDir = context.filesDir
        )
    }
}
