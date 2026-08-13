package com.example.progmob_progetto_melluso.auth

import com.example.progmob_progetto_melluso.data.dao.UserProfileDao
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository(
    private val userProfileDao: UserProfileDao,
    private val firebaseAuth: FirebaseAuth = Firebase.auth
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            trySend(
                user?.let {
                    AuthUser(
                        uid = it.uid,
                        displayName = it.displayName,
                        email = it.email,
                        photoUrl = it.photoUrl?.toString()
                    )
                }
            )
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token is missing"))
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = suspendCancellableCoroutine { continuation ->
            firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener { continuation.resume(Result.success(it)) }
                .addOnFailureListener { continuation.resume(Result.failure(it)) }
        }

        return authResult.mapCatching { result ->
            val firebaseUser = result.user ?: error("Firebase user is null after Google sign-in")
            val authUser = AuthUser(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName,
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString()
            )
            persistUserProfile(authUser)
            authUser
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend fun persistUserProfile(authUser: AuthUser) {
        val existing = userProfileDao.profileByFirebaseUserIdOnce(authUser.uid)
        val createdAt = existing?.createdAtUtcMillis ?: System.currentTimeMillis()
        userProfileDao.upsert(
            UserProfileEntity(
                id = existing?.id ?: 0L,
                firebaseUserId = authUser.uid,
                displayName = authUser.displayName,
                photoUrl = authUser.photoUrl,
                isAnonymousDefault = existing?.isAnonymousDefault ?: false,
                showTravelPathsByDefault = existing?.showTravelPathsByDefault ?: true,
                createdAtUtcMillis = createdAt
            )
        )
    }
}
