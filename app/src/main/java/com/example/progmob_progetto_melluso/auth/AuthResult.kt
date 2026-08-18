package com.example.progmob_progetto_melluso.auth

/**
 * Result of an authentication operation, modeled as a sealed type.
 *
 * How it works: unlike [kotlin.Result], the failure branch carries a domain
 * [AuthError] instead of a raw [Throwable]. This keeps provider exceptions out
 * of the ViewModel and UI layers, which only see [AuthError].
 *
 * @param T the success payload type.
 */
sealed interface AuthResult<out T> {

    /** Successful outcome carrying the produced value. */
    data class Success<T>(val value: T) : AuthResult<T>

    /** Failed outcome carrying a domain error. */
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}
