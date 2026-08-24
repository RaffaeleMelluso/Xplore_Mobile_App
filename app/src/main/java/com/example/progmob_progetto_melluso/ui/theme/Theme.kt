package com.example.progmob_progetto_melluso.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Dark color scheme: deep sea at dusk.
 *
 * Every container/text pair keeps a high contrast ratio for readability on dark
 * surfaces.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DeepSeaTeal,
    onPrimary = OnDeepSeaTeal,
    primaryContainer = DeepSeaTealContainer,
    onPrimaryContainer = OnDeepSeaTealContainer,
    secondary = DuskCoral,
    onSecondary = OnDuskCoral,
    secondaryContainer = DuskCoralContainer,
    onSecondaryContainer = OnDuskCoralContainer,
    tertiary = MoonlitSand,
    onTertiary = OnMoonlitSand,
    tertiaryContainer = MoonlitSandContainer,
    onTertiaryContainer = OnMoonlitSandContainer,
    background = AbyssBackground,
    onBackground = OnAbyss,
    surface = AbyssBackground,
    onSurface = OnAbyss,
    surfaceVariant = AbyssSurfaceVariant,
    onSurfaceVariant = OnAbyssSurfaceVariant,
    surfaceContainer = AbyssSurfaceContainer,
    outline = AbyssOutline,
    error = ErrorDark,
    onError = OnErrorDark
)

/**
 * Light color scheme: sunny seaside day.
 *
 * Every container/text pair keeps a high contrast ratio for readability on light
 * surfaces.
 */
private val LightColorScheme = lightColorScheme(
    primary = OceanTeal,
    onPrimary = OnOceanTeal,
    primaryContainer = OceanTealContainer,
    onPrimaryContainer = OnOceanTealContainer,
    secondary = CoralSunset,
    onSecondary = OnCoralSunset,
    secondaryContainer = CoralSunsetContainer,
    onSecondaryContainer = OnCoralSunsetContainer,
    tertiary = GoldenSand,
    onTertiary = OnGoldenSand,
    tertiaryContainer = GoldenSandContainer,
    onTertiaryContainer = OnGoldenSandContainer,
    background = SeashellBackground,
    onBackground = OnSeashell,
    surface = SeashellBackground,
    onSurface = OnSeashell,
    surfaceVariant = SeashellSurfaceVariant,
    onSurfaceVariant = OnSeashellSurfaceVariant,
    surfaceContainer = SeashellSurfaceContainer,
    outline = SeashellOutline,
    error = ErrorLight,
    onError = OnErrorLight
)

/**
 * App theme wrapper.
 *
 * How it works: selects the dark or light scheme based on [darkTheme] (or the
 * system setting) and applies the shared typography. [dynamicColor] is disabled
 * by default so the travel palette stays consistent on every device.
 *
 * @param darkTheme force dark/light mode; defaults to the system setting.
 * @param dynamicColor enable Material You dynamic colors on Android 12+.
 * @param content the composable content themed with this scheme.
 */
@Composable
fun ProgMob_Progetto_MellusoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
