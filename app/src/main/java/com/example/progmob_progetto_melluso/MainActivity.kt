package com.example.progmob_progetto_melluso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Scaffold
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.ui.login.LoginScreen
import com.example.progmob_progetto_melluso.ui.login.LoginViewModel
import com.example.progmob_progetto_melluso.ui.login.LoginViewModelFactory
import com.example.progmob_progetto_melluso.ui.theme.ProgMob_Progetto_MellusoTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    private lateinit var loginViewModel: LoginViewModel

    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        GoogleSignIn.getClient(this, options)
    }

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
                    loginViewModel.onExternalError("Missing Google ID token")
                }
            } catch (exception: ApiException) {
                loginViewModel.onExternalError("Google sign-in failed (${exception.statusCode})")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            loginViewModel.clearError()
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onSignOutClick = {
                            googleSignInClient.signOut()
                            loginViewModel.onSignOutRequested()
                        }
                    )
                }
            }
        }
    }
}
