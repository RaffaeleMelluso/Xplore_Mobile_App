package com.example.progmob_progetto_melluso.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.auth.AuthRepository

/**
 * Creates [LoginViewModel] instances and injects their [AuthRepository]
 * dependency.
 *
 * How it works: Compose/ViewModelProvider needs a factory when the ViewModel
 * has constructor parameters. This factory only knows how to build
 * [LoginViewModel] and fails loudly for any other requested type.
 *
 * @param authRepository the authentication repository to inject.
 */
class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Return the ViewModel with its dependency when the requested type matches.
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
