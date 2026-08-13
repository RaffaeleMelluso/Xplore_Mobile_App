package com.example.progmob_progetto_melluso.ui.login

data class LoginUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val errorMessage: String? = null
)
