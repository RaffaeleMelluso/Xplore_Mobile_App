package com.example.progmob_progetto_melluso.auth

/**
 * Domain-level authentication error, decoupled from any provider.
 *
 * How it works: the repository translates provider-specific failures (for
 * example Firebase error codes) into these cases at the data boundary, so the
 * ViewModel and the UI never depend on provider types. The UI layer maps each
 * case to a user-facing string resource.
 */
sealed class AuthError {

    /** The email is missing or malformed. */
    data object InvalidEmail : AuthError()

    /** The email/password pair does not match an existing account. */
    data object InvalidCredentials : AuthError()

    /** A password is required but was left empty (sign-in only). */
    data object PasswordRequired : AuthError()

    /** The password is too short to satisfy the minimum policy. */
    data object WeakPassword : AuthError()

    /** The email is already linked to an existing account. */
    data object EmailAlreadyInUse : AuthError()

    /** The account exists but has been disabled. */
    data object UserDisabled : AuthError()

    /** The provider rate-limited the request. */
    data object TooManyRequests : AuthError()

    /** The operation failed because of a connectivity problem. */
    data object Network : AuthError()

    /**
     * Any failure that does not map to a known case.
     *
     * @param cause the original provider/implementation exception, kept only
     * for logging and debugging; it is never shown to the user.
     */
    data class Unknown(val cause: Throwable? = null) : AuthError()
}
