package com.example.progmob_progetto_melluso.auth

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)
