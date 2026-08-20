package com.example.progmob_progetto_melluso.core

import android.content.Context
import androidx.room.Room
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.FirebaseAuthRepository
import com.example.progmob_progetto_melluso.data.database.AppDatabase
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
}
