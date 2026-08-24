package com.example.progmob_progetto_melluso

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.progmob_progetto_melluso.auth.AuthError
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.service.TrackingService
import com.example.progmob_progetto_melluso.ui.home.HomeScreen
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
 * How it works: it owns the platform-specific Google Sign-In client, requests
 * the runtime permissions needed by the tracking service and routes between
 * the login screen and the home scaffold through a [NavHost]. The navigation
 * destination is driven by the authentication state exposed by
 * [LoginViewModel]: authenticated users land on the home (map) screen, and the
 * [TrackingService] is started so the position is recorded even in background.
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
            val authenticated = uiState.isAuthenticated
            val navController = rememberNavController()
            val context = LocalContext.current

            // Requests location + notification permission, then starts tracking.
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (locationGranted) {
                    startTracking(context)
                }
            }

            // React to authentication changes: enter home + start tracking, or
            // stop tracking and go back to login.
            LaunchedEffect(authenticated) {
                if (authenticated) {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    context.stopService(Intent(context, TrackingService::class.java))
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            ProgMob_Progetto_MellusoTheme {
                NavHost(
                    navController = navController,
                    startDestination = if (authenticated) ROUTE_HOME else ROUTE_LOGIN
                ) {
                    composable(ROUTE_LOGIN) {
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
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            displayName = uiState.displayName,
                            email = uiState.email,
                            onSignOut = {
                                googleSignInClient.signOut()
                                loginViewModel.onSignOutRequested()
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val ROUTE_LOGIN = "login"
        private const val ROUTE_HOME = "home"

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        /**
         * Starts the foreground tracking service.
         *
         * How it works: uses [ContextCompat.startForegroundService] which is
         * safe on all API levels; the service must call startForeground within
         * a few seconds, which [TrackingService] does in onCreate.
         *
         * @param context any context with an application scope.
         */
        private fun startTracking(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }
    }
}
