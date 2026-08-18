package com.example.progmob_progetto_melluso.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.auth.AuthError
import com.example.progmob_progetto_melluso.auth.AuthRepository
import com.example.progmob_progetto_melluso.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the login and registration screen.
 *
 * How it works: it owns a single immutable [LoginUiState] exposed as a
 * [StateFlow]. The UI sends intents through its public methods and the
 * ViewModel delegates the real authentication work to [AuthRepository] inside
 * [viewModelScope]. It consumes domain [AuthResult]/[AuthError] values and
 * never builds presentation strings, which remain a UI concern.
 *
 * @param authRepository the authentication repository to use.
 */
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Start observing the session as soon as the ViewModel exists.
        observeAuthState()
    }

    /**
     * Handles the Google ID token returned by the account chooser.
     *
     * How it works: sets the loading flag, asks the repository to exchange the
     * token with Firebase Auth and surfaces a domain error on failure. Success
     * is not handled here because [observeAuthState] will emit the new user.
     *
     * @param idToken the one-time Google ID token.
     */
    fun onGoogleTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, passwordResetEmailSentTo = null)
            }
            val result = authRepository.signInWithGoogleIdToken(idToken)
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /**
     * Updates the email field of the form.
     *
     * How it works: stores the typed value and clears previous error/info
     * feedback so stale messages do not linger while the user edits.
     *
     * @param value the text typed in the email field.
     */
    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(emailInput = value, error = null, passwordResetEmailSentTo = null)
        }
    }

    /**
     * Updates the password field of the form.
     *
     * How it works: stores the typed value and clears previous error/info
     * feedback so stale messages do not linger while the user edits.
     *
     * @param value the text typed in the password field.
     */
    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(passwordInput = value, error = null, passwordResetEmailSentTo = null)
        }
    }

    /**
     * Switches the form between sign-in and registration mode.
     *
     * How it works: flips [LoginUiState.isSignUpMode] and resets transient
     * feedback plus the password, since the two flows have different rules.
     */
    fun onToggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                passwordInput = "",
                error = null,
                passwordResetEmailSentTo = null
            )
        }
    }

    /**
     * Submits the email/password form (sign-in or registration).
     *
     * How it works: validates the input locally using domain [AuthError] cases,
     * then calls the matching repository method based on
     * [LoginUiState.isSignUpMode]. All repository calls run in [viewModelScope]
     * so the UI stays responsive.
     */
    fun onEmailSubmit() {
        // Snapshot the state so validation and the coroutine read a consistent mode.
        val state = _uiState.value
        val email = state.emailInput.trim()
        val password = state.passwordInput
        val isSignUp = state.isSignUpMode

        // Local validation: email format first, then password rules per mode.
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(error = AuthError.InvalidEmail) }
            return
        }
        if (isSignUp) {
            if (password.length < MIN_PASSWORD_LENGTH) {
                _uiState.update { it.copy(error = AuthError.WeakPassword) }
                return
            }
        } else if (password.isEmpty()) {
            _uiState.update { it.copy(error = AuthError.PasswordRequired) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, passwordResetEmailSentTo = null)
            }

            // Pick the right provider operation for the current mode.
            val result = if (isSignUp) {
                authRepository.signUpWithEmailAndPassword(email, password)
            } else {
                authRepository.signInWithEmailAndPassword(email, password)
            }

            // Success is reflected by observeAuthState(); only failures need state here.
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /**
     * Requests a password reset email for the typed address.
     *
     * How it works: validates the email and asks the repository to send the
     * reset message. On success it records which address received the email so
     * the UI can show the confirmation.
     */
    fun onForgotPasswordClick() {
        val email = _uiState.value.emailInput.trim()

        // The reset flow needs a valid address before contacting Firebase.
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(error = AuthError.InvalidEmail) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, passwordResetEmailSentTo = null)
            }
            val result = authRepository.sendPasswordResetEmail(email)
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(isLoading = false, error = result.error) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, passwordResetEmailSentTo = email)
                }
            }
        }
    }

    /**
     * Ends the current session.
     *
     * How it works: delegates to the repository and clears the password field
     * on success so credentials do not remain on screen after logout.
     */
    fun onSignOutRequested() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, passwordResetEmailSentTo = null)
            }
            try {
                authRepository.signOut()
                _uiState.update { it.copy(isLoading = false, passwordInput = "") }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = AuthError.Unknown(exception))
                }
            }
        }
    }

    /**
     * Clears the current error.
     *
     * How it works: used before starting a new operation (for example before
     * launching the Google account chooser).
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reports an error detected by the platform layer (for example the Google
     * chooser).
     *
     * How it works: lets the Activity feed domain errors into the single state
     * owner without building presentation strings outside the UI layer.
     *
     * @param error the domain error to surface.
     */
    fun onExternalError(error: AuthError) {
        _uiState.update { it.copy(isLoading = false, error = error) }
    }

    /**
     * Observes the repository auth stream and projects it into the UI state.
     *
     * How it works: every emission (sign-in, sign-out or session restore) is
     * converted into the `isAuthenticated`/identity fields of [LoginUiState].
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authUser ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = authUser != null,
                        displayName = authUser?.displayName,
                        email = authUser?.email
                    )
                }
            }
        }
    }

    /**
     * Validates an email address with a small local regex.
     *
     * How it works: avoids an Android framework dependency
     * ([android.util.Patterns]) so the ViewModel remains unit-testable with
     * plain JUnit.
     *
     * @param email the address to validate.
     * @return true when the address has a plausible format.
     */
    private fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

    companion object {
        /** Minimum password length required by Firebase for new accounts. */
        private const val MIN_PASSWORD_LENGTH = 6

        /** Simple email format check; the provider remains the final authority. */
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
