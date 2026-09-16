package com.example.progmob_progetto_melluso.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the two provider-code translations the app relies on.
 *
 * Why they matter: a code that fails to match its domain case does not fail
 * loudly, it silently becomes [AuthError.Unknown], and the user is shown
 * "Authentication failed." for every possible cause — a wrong password, an
 * address already in use, or a misconfigured build. These tests pin the
 * spellings the providers actually emit.
 */
class AuthErrorMappingTest {

    @Test
    fun firebaseCodes_androidUppercaseSpelling_mapsToDomainCase() {
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("ERROR_INVALID_CREDENTIAL"))
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("ERROR_INVALID_LOGIN_CREDENTIALS"))
        assertEquals(AuthError.InvalidEmail, firebaseErrorCodeToAuthError("ERROR_INVALID_EMAIL"))
        assertEquals(AuthError.EmailAlreadyInUse, firebaseErrorCodeToAuthError("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthError.WeakPassword, firebaseErrorCodeToAuthError("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthError.UserDisabled, firebaseErrorCodeToAuthError("ERROR_USER_DISABLED"))
        assertEquals(AuthError.TooManyRequests, firebaseErrorCodeToAuthError("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(AuthError.Network, firebaseErrorCodeToAuthError("ERROR_NETWORK_REQUEST_FAILED"))
    }

    @Test
    fun firebaseCodes_hyphenatedSpelling_stillMaps() {
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("invalid-credential"))
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError("wrong-password"))
        assertEquals(AuthError.EmailAlreadyInUse, firebaseErrorCodeToAuthError("email-already-in-use"))
        assertEquals(AuthError.TooManyRequests, firebaseErrorCodeToAuthError("too-many-requests"))
    }

    @Test
    fun firebaseCodes_areMatchedRegardlessOfCaseAndPadding() {
        assertEquals(AuthError.InvalidCredentials, firebaseErrorCodeToAuthError(" error_wrong_password "))
    }

    @Test
    fun firebaseCodes_unknownOrMissing_fallThroughToUnknown() {
        assertNull(firebaseErrorCodeToAuthError("ERROR_SOMETHING_NEW"))
        assertNull(firebaseErrorCodeToAuthError(null))
        assertNull(firebaseErrorCodeToAuthError("   "))
    }

    @Test
    fun googleStatuses_mapConfigurationAndNetworkFailures() {
        assertEquals(AuthError.NotConfigured, GoogleSignInErrorMapper.toAuthError(10))
        assertEquals(AuthError.Network, GoogleSignInErrorMapper.toAuthError(7))
        assertTrue(GoogleSignInErrorMapper.toAuthError(12500) is AuthError.Unknown)
    }

    @Test
    fun googleStatuses_dismissedChooser_isNotReportedAsFailure() {
        assertNull(GoogleSignInErrorMapper.toAuthError(12501))
    }
}
