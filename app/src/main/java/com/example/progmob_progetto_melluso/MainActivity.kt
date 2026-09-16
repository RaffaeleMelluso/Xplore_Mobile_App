package com.example.progmob_progetto_melluso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.progmob_progetto_melluso.auth.AuthError
import com.example.progmob_progetto_melluso.auth.GoogleSignInErrorMapper
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.core.preferences.ThemeMode
import com.example.progmob_progetto_melluso.service.TrackingService
import com.example.progmob_progetto_melluso.ui.home.HomeScreen
import com.example.progmob_progetto_melluso.ui.login.LoginScreen
import com.example.progmob_progetto_melluso.ui.login.LoginViewModel
import com.example.progmob_progetto_melluso.ui.login.LoginViewModelFactory
import com.example.progmob_progetto_melluso.ui.onboarding.OnboardingScreen
import com.example.progmob_progetto_melluso.ui.onboarding.OnboardingViewModel
import com.example.progmob_progetto_melluso.ui.onboarding.OnboardingViewModelFactory
import com.example.progmob_progetto_melluso.ui.theme.ProgMob_Progetto_MellusoTheme
import com.example.progmob_progetto_melluso.ui.theme.ThemeViewModel
import com.example.progmob_progetto_melluso.ui.theme.ThemeViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Single activity host of the application.
 *
 * How it works: it owns the platform-specific Google Sign-In client, routes
 * between the first-run onboarding, the login screen and the home scaffold
 * through a [NavHost], and starts the foreground [TrackingService] for the
 * signed-in user.
 *
 * The destination is driven by two observable states and never by a decision
 * taken here: whether the first-run pages were already accepted (a DataStore
 * flag exposed by [OnboardingViewModel]) and whether a session is active
 * (exposed by [LoginViewModel]). The Android permission request is NOT opened
 * here: the onboarding pages own it, so the system dialog appears right after
 * the reason for it. This activity only STOPS the tracking service (on sign
 * out); starting it belongs to the map, which does it with the app in front.
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
     *
     * A failure is translated by [GoogleSignInErrorMapper] instead of being
     * flattened into a generic error: Google reports a misconfigured build with
     * its own status code, and the user must not be told to retry a password
     * problem that does not exist. Dismissing the chooser reports nothing.
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
                    // The account came back without an ID token: the app is not
                    // linked to the web client the token must be issued for.
                    loginViewModel.onExternalError(AuthError.NotConfigured)
                }
            } catch (exception: ApiException) {
                // The chooser failed; its status code says why (a dismissal
                // returns null here and is not reported as a failure).
                GoogleSignInErrorMapper.toAuthError(exception.statusCode)
                    ?.let(loginViewModel::onExternalError)
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

            // First-run pages: their flag comes from a ViewModel, so the activity
            // never reads the DataStore repository directly (MVVM separation),
            // exactly like the theme preference below.
            val onboardingFactory = remember {
                OnboardingViewModelFactory(AppContainer.userPreferencesRepository(context))
            }
            val onboardingViewModel: OnboardingViewModel = viewModel(factory = onboardingFactory)
            val onboardingState by onboardingViewModel.uiState.collectAsState()

            // Where the app should be, from the first-run flag and the session;
            // null while the stored flag is still being read.
            val onboarded = onboardingState.completed
            val destination = when {
                onboarded == null -> null
                !onboarded -> ROUTE_ONBOARDING
                authenticated -> ROUTE_HOME
                else -> ROUTE_LOGIN
            }

            // React to a destination change: the first-run pages until they are
            // accepted, then the session decides. The tracking service follows
            // the session too.
            //
            // The navigation is guarded by the CURRENT destination: on a
            // configuration change (rotation) this effect re-runs, and re-
            // navigating would REPLACE the current back-stack entry, discarding
            // its saved state (selected tab, open dialogs, edited fields).
            // Skipping the navigate when already on the target lets the
            // NavHost restore the previous entry with all its UI state.
            LaunchedEffect(authenticated, destination) {
                val target = destination ?: return@LaunchedEffect
                if (navController.currentDestination?.route != target) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                // Signing out is the only service action taken here. The service
                // is never STARTED from this effect: an effect also runs while
                // the app is not in the foreground (the session becomes active
                // while the Google account chooser is on top), and Android 12+
                // kills the process when a foreground service is started from
                // the background. The map owns the start instead: its own effect
                // fires when permission and GPS are both available, which only
                // happens with the app in front.
                if (!authenticated) {
                    context.stopService(Intent(context, TrackingService::class.java))
                }
            }

            // Theme: the user-selected mode (System/Light/Dark) is exposed by a
            // dedicated ViewModel, so the View never reads the DataStore
            // repository directly; SYSTEM follows the device night/day setting.
            val themeFactory = remember {
                ThemeViewModelFactory(AppContainer.userPreferencesRepository(context))
            }
            val themeViewModel: ThemeViewModel = viewModel(factory = themeFactory)
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ProgMob_Progetto_MellusoTheme(darkTheme = isDarkTheme) {
                val startDestination = destination
                if (startDestination == null) {
                    // The stored flag has not been read yet (one DataStore
                    // read): keep the app background instead of flashing the
                    // login or the map before the pages appear.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(ROUTE_ONBOARDING) {
                            OnboardingScreen(
                                onAccepted = onboardingViewModel::completeOnboarding
                            )
                        }
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
    }

    companion object {
        private const val ROUTE_ONBOARDING = "onboarding"
        private const val ROUTE_LOGIN = "login"
        private const val ROUTE_HOME = "home"
    }
}
