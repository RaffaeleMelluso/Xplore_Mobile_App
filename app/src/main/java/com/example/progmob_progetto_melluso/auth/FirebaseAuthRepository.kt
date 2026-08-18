package com.example.progmob_progetto_melluso.auth

import com.example.progmob_progetto_melluso.data.dao.UserProfileDao
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException
import com.google.firebase.auth.AuthResult as FirebaseAuthResult

/**
 * Firebase implementation of [AuthRepository].
 *
 * How it works: every operation delegates to the Firebase Auth SDK, which is
 * the real security boundary (password hashing, tokens, session management).
 * This class translates Firebase's callback-based `Task` API into
 * coroutine-friendly suspend functions, keeps the local Room profile cache in
 * sync and converts provider failures into domain [AuthError]s at this
 * boundary.
 *
 * @param userProfileDao local profile cache access.
 * @param firebaseAuth the Firebase Auth client (defaults to the global one).
 */
class FirebaseAuthRepository(
    private val userProfileDao: UserProfileDao,
    private val firebaseAuth: FirebaseAuth = Firebase.auth
) : AuthRepository {

    /**
     * Reactive stream exposing the current Firebase user.
     *
     * How it works: a Firebase [FirebaseAuth.AuthStateListener] is registered
     * and its callbacks are bridged into a cold [Flow] via [callbackFlow]. The
     * listener is removed when the flow collection stops.
     */
    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            trySend(user?.let { toAuthUser(it) })
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Signs in with a Google ID token.
     *
     * How it works: builds a Google credential from the one-time token, asks
     * Firebase to validate it and finally persists the resulting profile.
     *
     * API usage: Firebase Auth `signInWithCredential`.
     *
     * @param idToken the Google Sign-In ID token.
     * @return [AuthResult] with the authenticated user or a domain error.
     */
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthUser> {
        // Guard against an empty token before touching Firebase.
        if (idToken.isBlank()) {
            return AuthResult.Failure(AuthError.Unknown())
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            // Await the Firebase task and map the result to a domain user.
            val firebaseResult = awaitAuthResult(firebaseAuth.signInWithCredential(credential))
            val firebaseUser = firebaseResult.user ?: error("Firebase user is null after Google sign-in")
            val authUser = toAuthUser(firebaseUser)
            persistUserProfile(authUser)
            AuthResult.Success(authUser)
        } catch (exception: Exception) {
            // Translate the provider failure into a domain error.
            AuthResult.Failure(exception.toAuthError())
        }
    }

    /**
     * Signs in an existing user with email and password.
     *
     * How it works: forwards the credentials to Firebase Auth and, on success,
     * refreshes the local profile cache.
     *
     * API usage: Firebase Auth `signInWithEmailAndPassword`.
     *
     * @param email the account email.
     * @param password the account password.
     * @return [AuthResult] with the authenticated user or a domain error.
     */
    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser> {
        // Reject empty input locally to avoid a pointless network round-trip.
        if (email.isBlank() || password.isBlank()) {
            return AuthResult.Failure(AuthError.InvalidCredentials)
        }
        return try {
            val firebaseResult = awaitAuthResult(firebaseAuth.signInWithEmailAndPassword(email.trim(), password))
            val firebaseUser = firebaseResult.user ?: error("Firebase user is null after email sign-in")
            val authUser = toAuthUser(firebaseUser)
            persistUserProfile(authUser)
            AuthResult.Success(authUser)
        } catch (exception: Exception) {
            AuthResult.Failure(exception.toAuthError())
        }
    }

    /**
     * Creates a new account with email and password and signs it in.
     *
     * How it works: forwards the credentials to Firebase Auth, which creates
     * the account and stores only a hash of the password, then refreshes the
     * local profile cache.
     *
     * API usage: Firebase Auth `createUserWithEmailAndPassword`.
     *
     * @param email the email of the new account.
     * @param password the password of the new account.
     * @return [AuthResult] with the newly created user or a domain error.
     */
    override suspend fun signUpWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser> {
        // Reject empty input locally to avoid a pointless network round-trip.
        if (email.isBlank() || password.isBlank()) {
            return AuthResult.Failure(AuthError.InvalidCredentials)
        }
        return try {
            val firebaseResult = awaitAuthResult(firebaseAuth.createUserWithEmailAndPassword(email.trim(), password))
            val firebaseUser = firebaseResult.user ?: error("Firebase user is null after email sign-up")
            val authUser = toAuthUser(firebaseUser)
            persistUserProfile(authUser)
            AuthResult.Success(authUser)
        } catch (exception: Exception) {
            AuthResult.Failure(exception.toAuthError())
        }
    }

    /**
     * Requests a password reset email.
     *
     * How it works: delegates to Firebase Auth, which sends the reset message
     * to the given address.
     *
     * API usage: Firebase Auth `sendPasswordResetEmail`.
     *
     * @param email the email whose password must be reset.
     * @return [AuthResult] with [Unit] on success or a domain error.
     */
    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        // Reject empty input locally.
        if (email.isBlank()) {
            return AuthResult.Failure(AuthError.InvalidEmail)
        }
        return try {
            awaitVoidTask(firebaseAuth.sendPasswordResetEmail(email.trim()))
            AuthResult.Success(Unit)
        } catch (exception: Exception) {
            AuthResult.Failure(exception.toAuthError())
        }
    }

    /**
     * Ends the current Firebase session.
     */
    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Maps a provider user into the domain model used by the app.
     *
     * @param firebaseUser the Firebase user to convert.
     * @return the equivalent [AuthUser].
     */
    private fun toAuthUser(firebaseUser: FirebaseUser): AuthUser =
        AuthUser(
            uid = firebaseUser.uid,
            displayName = firebaseUser.displayName,
            email = firebaseUser.email,
            photoUrl = firebaseUser.photoUrl?.toString()
        )

    /**
     * Creates or refreshes the local profile cache for an authenticated user.
     *
     * How it works: the existing row (if any) is read once so user preferences
     * are preserved, then the row is upserted with the fresh provider data.
     *
     * @param authUser the user whose profile must be cached.
     */
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

    /**
     * Suspends until a Firebase [Task] completes and returns its result.
     *
     * How it works: Firebase exposes asynchronous work through callback-based
     * `Task` objects. This helper bridges that API into a suspend function so
     * callers can write plain sequential code inside a coroutine.
     *
     * @param task the Firebase task to await.
     * @return the successful Firebase auth result.
     * @throws the task exception when the operation fails.
     */
    private suspend fun awaitAuthResult(task: Task<FirebaseAuthResult>): FirebaseAuthResult =
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { completedTask ->
                // Never read the result on a failed task: getResult() throws a
                // RuntimeExecutionException that would crash the listener thread.
                if (completedTask.isSuccessful) {
                    val authResult = completedTask.result
                    if (authResult != null) {
                        continuation.resume(authResult)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("Firebase auth operation failed")
                        )
                    }
                } else {
                    continuation.resumeWithException(
                        completedTask.exception ?: IllegalStateException("Firebase auth operation failed")
                    )
                }
            }
        }

    /**
     * Suspends until a Firebase [Task] of type [Void] completes successfully.
     *
     * How it works: same bridge as [awaitAuthResult], but for operations that
     * produce no result value (for example sending a reset email).
     *
     * @param task the Firebase task to await.
     */
    private suspend fun awaitVoidTask(task: Task<Void>) {
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { completedTask ->
                if (completedTask.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        completedTask.exception ?: IllegalStateException("Firebase operation failed")
                    )
                }
            }
        }
    }

    /**
     * Translates a provider failure into a domain [AuthError].
     *
     * How it works: this is the only place that knows Firebase error codes. It
     * converts them into stable domain cases so the upper layers never see
     * Firebase-specific types.
     *
     * @return the domain error equivalent of this exception.
     */
    private fun Throwable.toAuthError(): AuthError =
        when (this) {
            is FirebaseAuthException ->
                when (errorCode) {
                    "invalid-credential", "user-not-found", "wrong-password" ->
                        AuthError.InvalidCredentials

                    "invalid-email" ->
                        AuthError.InvalidEmail

                    "email-already-in-use" ->
                        AuthError.EmailAlreadyInUse

                    "weak-password" ->
                        AuthError.WeakPassword

                    "user-disabled" ->
                        AuthError.UserDisabled

                    "too-many-requests" ->
                        AuthError.TooManyRequests

                    else ->
                        AuthError.Unknown(this)
                }

            is FirebaseNetworkException, is IOException ->
                AuthError.Network

            else ->
                AuthError.Unknown(this)
        }
}
