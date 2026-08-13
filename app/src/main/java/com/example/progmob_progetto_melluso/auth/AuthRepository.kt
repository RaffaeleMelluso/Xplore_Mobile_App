package com.example.progmob_progetto_melluso.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    suspend fun signOut()
}
