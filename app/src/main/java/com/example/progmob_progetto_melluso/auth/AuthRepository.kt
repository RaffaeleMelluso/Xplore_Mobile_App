package com.example.progmob_progetto_melluso.auth

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for authentication.
 *
 * How it works: this interface hides the concrete authentication provider
 * (Firebase) from the UI and the ViewModels. Implementations talk to the
 * external identity service and expose only domain objects ([AuthUser],
 * [AuthError], [AuthResult]) plus a reactive [authState] stream, so the rest
 * of the app never depends on provider-specific types.
 */
interface AuthRepository {

    /**
     * Reactive stream that emits the currently signed-in user, or `null` when
     * nobody is authenticated (including while a previous session is being
     * restored at startup).
     */
    val authState: Flow<AuthUser?>

    /**
     * Exchanges a Google ID token for an authenticated session.
     *
     * How it works: the token is produced by the Google Sign-In flow owned by
     * the UI. It is sent to the Firebase Auth backend, which validates it and
     * returns the matching user. The local profile cache is then refreshed.
     *
     * API usage: Firebase Auth `signInWithCredential` with a
     * `GoogleAuthProvider` credential.
     *
     * @param idToken the one-time ID token returned by Google Sign-In.
     * @return [AuthResult] wrapping the authenticated user or a domain error.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthUser>

    /**
     * Authenticates an existing account with email and password.
     *
     * How it works: the credentials are sent to the Firebase Auth backend,
     * which verifies them and opens a session for the matching user. The app
     * never stores or reads the password in clear text.
     *
     * API usage: Firebase Auth `signInWithEmailAndPassword`.
     *
     * @param email the account email.
     * @param password the account password.
     * @return [AuthResult] with the authenticated user or a domain error.
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser>

    /**
     * Creates a new account with email and password and signs it in.
     *
     * How it works: the credentials are sent to the Firebase Auth backend,
     * which creates the user, keeps only a secure hash of the password and
     * opens a session. The local profile cache is created or updated
     * afterwards.
     *
     * API usage: Firebase Auth `createUserWithEmailAndPassword`.
     *
     * @param email the email of the new account.
     * @param password the password of the new account.
     * @return [AuthResult] with the newly created user or a domain error.
     */
    suspend fun signUpWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser>

    /**
     * Sends a password reset email for an existing account.
     *
     * How it works: the email is handed to the Firebase Auth backend, which
     * emits the reset message. To avoid account enumeration the operation can
     * succeed even when the address is not registered.
     *
     * API usage: Firebase Auth `sendPasswordResetEmail`.
     *
     * @param email the email whose password must be reset.
     * @return [AuthResult] with [Unit] on success or a domain error.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>

    /**
     * Ends the current session, both locally and on the provider side.
     */
    suspend fun signOut()
}
