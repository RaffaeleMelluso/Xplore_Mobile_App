package com.example.progmob_progetto_melluso.ui.login

import com.example.progmob_progetto_melluso.auth.AuthError

/**
 * Immutable UI state for the login screen.
 *
 * How it works: the Compose screen only collects this state and never mutates
 * it directly. Every change is produced by [LoginViewModel] through its event
 * methods, keeping the UI free of business logic. Errors are exposed as domain
 * [AuthError]s: the UI layer is responsible for mapping them to text.
 */
data class LoginUiState(
    /** True while an authentication operation is in flight. */
    val isLoading: Boolean = false,
    /** True when a session is active. */
    val isAuthenticated: Boolean = false,
    /** Display name of the signed-in user (null for email-only accounts). */
    val displayName: String? = null,
    /** Email of the signed-in user, used as fallback label and identity. */
    val email: String? = null,
    /** Email typed by the user in the login/registration form. */
    val emailInput: String = "",
    /** Password typed by the user in the login/registration form. */
    val passwordInput: String = "",
    /** True when the form is creating a new account instead of signing in. */
    val isSignUpMode: Boolean = false,
    /** Error to render, either from validation or from the provider. */
    val error: AuthError? = null,
    /** Email that just received a password reset message, if any. */
    val passwordResetEmailSentTo: String? = null
)
