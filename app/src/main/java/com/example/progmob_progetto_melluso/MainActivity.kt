package com.example.progmob_progetto_melluso

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.progmob_progetto_melluso.auth.AuthError
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
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
            //
            // The navigation is guarded by the CURRENT destination: on a
            // configuration change (rotation) this effect re-runs, and re-
            // navigating would REPLACE the home back-stack entry, discarding
            // its saved state (selected tab, open dialogs, edited fields).
            // Skipping the navigate when already on the target lets the
            // NavHost restore the previous entry with all its UI state.
            LaunchedEffect(authenticated) {
                if (authenticated) {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    if (navController.currentDestination?.route != ROUTE_HOME) {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    context.stopService(Intent(context, TrackingService::class.java))
                    if (navController.currentDestination?.route != ROUTE_LOGIN) {
                        navController.navigate(ROUTE_LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }

            // Theme: the user-selected mode (System/Light/Dark) is read from
            // DataStore per user and resolved app-wide here; SYSTEM follows the
            // device night/day setting.
            val prefsRepository = remember { AppContainer.userPreferencesRepository(context) }
            val userPrefs by prefsRepository.preferences.collectAsState(initial = null)
            val isDarkTheme = when (userPrefs?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ProgMob_Progetto_MellusoTheme(darkTheme = isDarkTheme) {
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

        private val REQUIRED_PERMISSIONS: Array<String> by lazy {
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.POST_NOTIFICATIONS)
                // Android 10+ gating the step sensor behind a runtime
                // permission: without it the pedometer never counts.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }.toTypedArray()
        }

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
