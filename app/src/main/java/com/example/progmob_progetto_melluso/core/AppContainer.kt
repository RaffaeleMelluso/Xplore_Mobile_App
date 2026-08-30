package com.example.progmob_progetto_melluso.core

import android.content.Context
import androidx.room.Room
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.FirebaseAuthRepository
import com.example.progmob_progetto_melluso.data.database.AppDatabase
import com.example.progmob_progetto_melluso.journal.TravelJournalRepository
import com.example.progmob_progetto_melluso.journal.TravelJournalRepositoryImpl
import com.example.progmob_progetto_melluso.map.DemoPoiSeed
import com.example.progmob_progetto_melluso.ghost.GhostFactory
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.map.MapRepositoryImpl
import com.example.progmob_progetto_melluso.poi.PoiRepository
import com.example.progmob_progetto_melluso.poi.PoiRepositoryImpl
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepositoryImpl
import com.example.progmob_progetto_melluso.service.LocationUpdates
import com.example.progmob_progetto_melluso.travel.TravelCityDetector
import com.example.progmob_progetto_melluso.travel.TravelRepository
import com.example.progmob_progetto_melluso.travel.TravelRepositoryImpl
import com.example.progmob_progetto_melluso.trend.TrendRepository
import com.example.progmob_progetto_melluso.trend.TrendRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import java.util.TimeZone

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null

    fun database(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?:            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "xplore.db"
            )
                .addMigrations(
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_8_9,
                    AppDatabase.MIGRATION_9_10
                )
                .build()
                .also { database = it }
        }
    }

    fun authRepository(context: Context): AuthRepository {
        return FirebaseAuthRepository(
            userProfileDao = database(context).userProfileDao()
        )
    }

    /**
     * Shared vote engine: the single set of vote rules used by the journal,
     * the map POI card and the trends page.
     */
    fun poiVoteRepository(context: Context): PoiVoteRepository {
        val db = database(context)
        return PoiVoteRepositoryImpl(
            poiVoteDao = db.poiVoteDao(),
            poiDao = db.poiDao(),
            voterIdProvider = { FirebaseAuth.getInstance().currentUser?.uid ?: "local-user" }
        )
    }

    /**
     * POI catalog repository: reading is the same reactive table every screen
     * observes; addPoi stores the user-created points of interest
     * stamped with their owner, so the "my POIs" screen can list and remove
     * only the user's own ones.
     */
    fun poiRepository(context: Context): PoiRepository {
        val db = database(context)
        return PoiRepositoryImpl(
            poiDao = db.poiDao(),
            userIdProvider = { FirebaseAuth.getInstance().currentUser?.uid ?: "local-user" }
        )
    }

    /**
     * Trends repository wired to the votes table, the shared vote engine and
     * the demo POI seed (so the page is populated from the first launch).
     */
    fun trendRepository(context: Context): TrendRepository {
        val db = database(context)
        return TrendRepositoryImpl(
            poiVoteDao = db.poiVoteDao(),
            poiDao = db.poiDao(),
            poiVoteRepository = poiVoteRepository(context),
            seedPois = DemoPoiSeed.all
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
            ghostSimulator = GhostFactory(),
            poiSeed = DemoPoiSeed.all
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
            travelTransportDao = db.travelTransportDao(),
            pathPointDao = db.travelPathPointDao(),
            poiDao = db.poiDao(),
            poiVoteRepository = poiVoteRepository(context),
            travelRepository = travelRepository(context),
            cityDetector = TravelCityDetector(),
            mediaFilesDir = context.filesDir
        )
    }
}
