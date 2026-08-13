package com.example.progmob_progetto_melluso.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    fun onGoogleTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.signInWithGoogleIdToken(idToken)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Sign-in failed"
                        )
                    }
                }
        }
    }

    fun onSignOutRequested() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { authRepository.signOut() }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Sign-out failed"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onExternalError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

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
}
