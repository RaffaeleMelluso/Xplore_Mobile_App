package com.example.progmob_progetto_melluso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.auth.AuthError
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.ui.login.LoginScreen
import com.example.progmob_progetto_melluso.ui.login.LoginViewModel
import com.example.progmob_progetto_melluso.ui.login.LoginViewModelFactory
import com.example.progmob_progetto_melluso.ui.theme.ProgMob_Progetto_MellusoTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Single activity host of the application.
 *
 * How it works: it wires the Compose UI to the [LoginViewModel], owns the
 * platform-specific Google Sign-In client and forwards the result of the
 * Google account chooser back to the ViewModel. Email/password credentials are
 * handled entirely by the ViewModel, without any Activity involvement.
 */
class MainActivity : ComponentActivity() {

    private lateinit var loginViewModel: LoginViewModel

    /**
     * Lazily built Google Sign-In client used to launch the account chooser.
     *
     * How it works: uses the `default_web_client_id` string resource injected by
     * the google-services Gradle plugin from `google-services.json`.
     */
    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        GoogleSignIn.getClient(this, options)
    }

    /**
     * Receives the result of the Google account chooser.
     *
     * How it works: the returned intent is resolved into a signed-in Google
     * account; its one-time ID token is extracted and forwarded to the
     * ViewModel, which exchanges it with Firebase Auth.
     */
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val dataIntent = result.data ?: return@registerForActivityResult
            val task = GoogleSignIn.getSignedInAccountFromIntent(dataIntent)
            try {
                val account = task.getResult(ApiException::class.java)
                val token = account.idToken
                if (!token.isNullOrBlank()) {
                    loginViewModel.onGoogleTokenReceived(token)
                } else {
                    // Platform detected a broken sign-in result; report it as a domain error.
                    loginViewModel.onExternalError(AuthError.Unknown())
                }
            } catch (exception: ApiException) {
                // The Google chooser failed; the specific status code is not shown to the user.
                loginViewModel.onExternalError(AuthError.Unknown())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Build the ViewModel through the manual DI container.
        loginViewModel = ViewModelProvider(
            this,
            LoginViewModelFactory(AppContainer.authRepository(this))
        )[LoginViewModel::class.java]

        setContent {
            val uiState by loginViewModel.uiState.collectAsState()
            ProgMob_Progetto_MellusoTheme {
                Scaffold { _ ->
                    LoginScreen(
                        uiState = uiState,
                        onGoogleSignInClick = {
                            // Launch the platform Google chooser; its result is
                            // delivered to googleSignInLauncher.
                            loginViewModel.clearError()
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onSignOutClick = {
                            // Sign out of both the Google client and Firebase.
                            googleSignInClient.signOut()
                            loginViewModel.onSignOutRequested()
                        },
                        onEmailChanged = loginViewModel::onEmailChanged,
                        onPasswordChanged = loginViewModel::onPasswordChanged,
                        onToggleMode = loginViewModel::onToggleMode,
                        onEmailSubmit = loginViewModel::onEmailSubmit,
                        onForgotPasswordClick = loginViewModel::onForgotPasswordClick
                    )
                }
            }
        }
    }
}
