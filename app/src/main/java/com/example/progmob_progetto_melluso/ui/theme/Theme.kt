package com.example.progmob_progetto_melluso.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CapriBlue,
    onPrimary = OnDarkAccent,
    secondary = BlueIsle,
    tertiary = GreenScallion,
    background = LotusGreen,
    onBackground = OnDarkAccent,
    surface = Color(0xFF255C12),
    onSurface = OnDarkAccent
)

private val LightColorScheme = lightColorScheme(
    primary = CapriBlue,
    onPrimary = OnDarkAccent,
    secondary = BlueIsle,
    tertiary = GreenScallion,
    background = WaferBrown,
    onBackground = LotusGreen,
    surface = Color(0xFFFFFFFF),
    onSurface = LotusGreen,
    primaryContainer = GreenScallion,
    onPrimaryContainer = OnDarkAccent
)

@Composable
fun ProgMob_Progetto_MellusoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep the travel palette fixed and consistent in every screen.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}