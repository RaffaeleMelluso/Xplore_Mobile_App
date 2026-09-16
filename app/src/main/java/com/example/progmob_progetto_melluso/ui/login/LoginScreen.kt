package com.example.progmob_progetto_melluso.ui.login

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.auth.AuthError

/**
 * Login and registration screen.
 *
 * How it works: it is a pure state renderer. It receives the immutable
 * [LoginUiState], renders it and forwards every user intent to the callbacks
 * provided by the caller. It is also the only layer that maps domain
 * [AuthError]s to user-facing text, keeping presentation out of the ViewModel.
 *
 * @param uiState current screen state collected from the ViewModel.
 * @param onGoogleSignInClick starts the Google Sign-In flow.
 * @param onSignOutClick ends the current session.
 * @param onEmailChanged updates the email field.
 * @param onPasswordChanged updates the password field.
 * @param onToggleMode switches between sign-in and registration.
 * @param onEmailSubmit submits the email/password form.
 * @param onForgotPasswordClick requests a password reset email.
 */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onGoogleSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onToggleMode: () -> Unit,
    onEmailSubmit: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    // Scaffold provides the same background as the home screen, keeping
    // the light/dark palette consistent across every screen.
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header: app name and a short description.
            Text(
                text = stringResource(id = R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                text = stringResource(id = R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            // Busy indicator shown while any authentication operation is running.
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            }

            if (uiState.isAuthenticated) {
                // Already authenticated: show identity and logout only.
                SignedInSection(uiState = uiState, onSignOutClick = onSignOutClick)
            } else {
                // Not authenticated: show the credentials form plus the Google provider.
                CredentialsForm(
                    uiState = uiState,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onToggleMode = onToggleMode,
                    onEmailSubmit = onEmailSubmit,
                    onForgotPasswordClick = onForgotPasswordClick
                )

                // Visual separator between the two authentication providers.
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedButton(
                    onClick = onGoogleSignInClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.login_with_google))
                }
            }

            // Validation or provider error mapped to a localized message here in the UI.
            uiState.error?.let { error ->
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(id = error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            // Confirmation that the password reset email was sent.
            uiState.passwordResetEmailSentTo?.let { email ->
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(id = R.string.login_password_reset_sent, email),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Renders the signed-in state (identity label + logout button).
 *
 * @param uiState the current state.
 * @param onSignOutClick ends the session.
 */
@Composable
private fun SignedInSection(
    uiState: LoginUiState,
    onSignOutClick: () -> Unit
) {
    // Prefer the display name, fall back to the email for email-only accounts.
    val label = uiState.displayName ?: uiState.email
    Text(
        text = label?.let { stringResource(id = R.string.login_signed_in_as, it) }
            ?: stringResource(id = R.string.login_signed_in),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    OutlinedButton(
        onClick = onSignOutClick,
        enabled = !uiState.isLoading
    ) {
        Text(text = stringResource(id = R.string.logout))
    }
}

/**
 * Renders the email/password form with mode switch and password reset.
 *
 * @param uiState the current state.
 * @param onEmailChanged updates the email field.
 * @param onPasswordChanged updates the password field.
 * @param onToggleMode switches between sign-in and registration.
 * @param onEmailSubmit submits the form.
 * @param onForgotPasswordClick requests a password reset email.
 */
@Composable
private fun CredentialsForm(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onToggleMode: () -> Unit,
    onEmailSubmit: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    // Email input.
    OutlinedTextField(
        value = uiState.emailInput,
        onValueChange = onEmailChanged,
        label = { Text(stringResource(id = R.string.login_email_label)) },
        singleLine = true,
        enabled = !uiState.isLoading,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Local UI-only state: whether the password is shown in clear text.
    // This is pure presentation state, so it lives here and not in the ViewModel.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Password input with a toggle to reveal or hide the value.
    OutlinedTextField(
        value = uiState.passwordInput,
        onValueChange = onPasswordChanged,
        label = { Text(stringResource(id = R.string.login_password_label)) },
        singleLine = true,
        enabled = !uiState.isLoading,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            Text(
                text = stringResource(
                    id = if (passwordVisible) {
                        R.string.login_hide_password
                    } else {
                        R.string.login_show_password
                    }
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(enabled = !uiState.isLoading) {
                        passwordVisible = !passwordVisible
                    }
                    .padding(8.dp)
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Primary action: sign in or create account depending on the mode.
    Button(
        onClick = onEmailSubmit,
        enabled = !uiState.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                id = if (uiState.isSignUpMode) {
                    R.string.login_submit_sign_up
                } else {
                    R.string.login_submit_sign_in
                }
            )
        )
    }

    // Link to switch between the two modes.
    TextButton(
        onClick = onToggleMode,
        enabled = !uiState.isLoading
    ) {
        Text(
            text = stringResource(
                id = if (uiState.isSignUpMode) {
                    R.string.login_switch_to_sign_in
                } else {
                    R.string.login_switch_to_sign_up
                }
            )
        )
    }

    // Password reset only makes sense in sign-in mode.
    if (!uiState.isSignUpMode) {
        TextButton(
            onClick = onForgotPasswordClick,
            enabled = !uiState.isLoading
        ) {
            Text(text = stringResource(id = R.string.login_forgot_password))
        }
    }
}

/**
 * Maps a domain [AuthError] to the localized string resource shown to the user.
 *
 * How it works: this presentation mapping lives in the UI layer on purpose. The
 * ViewModel only exposes the domain error and never builds display text.
 *
 * @return the string resource id for the error message.
 */
@StringRes
private fun AuthError.messageRes(): Int =
    when (this) {
        AuthError.InvalidEmail -> R.string.login_error_invalid_email
        AuthError.InvalidCredentials -> R.string.login_error_invalid_credentials
        AuthError.PasswordRequired -> R.string.login_error_password_required
        AuthError.WeakPassword -> R.string.login_error_weak_password
        AuthError.EmailAlreadyInUse -> R.string.login_error_email_in_use
        AuthError.UserDisabled -> R.string.login_error_user_disabled
        AuthError.TooManyRequests -> R.string.login_error_too_many_requests
        AuthError.Network -> R.string.login_error_network
        AuthError.NotConfigured -> R.string.login_error_not_configured
        is AuthError.Unknown -> R.string.login_error_unknown
    }
