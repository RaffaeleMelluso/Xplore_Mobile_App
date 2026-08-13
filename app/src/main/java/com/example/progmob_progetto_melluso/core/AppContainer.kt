package com.example.progmob_progetto_melluso.core

import android.content.Context
import androidx.room.Room
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.FirebaseAuthRepository
import com.example.progmob_progetto_melluso.data.database.AppDatabase

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
}
