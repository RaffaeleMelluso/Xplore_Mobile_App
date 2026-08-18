package com.example.progmob_progetto_melluso.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Travel / beach palette.
//
// Light theme evokes a sunny seaside day: ocean teal for actions, sunset coral
// for accents and golden sand for highlights, on a warm seashell background.
// Dark theme evokes the sea at dusk: bright sea-foam, dusk coral and moonlit
// sand on a deep abyss background. Every color comes with its "on" counterpart
// and container pair so text keeps a readable contrast in both modes.
// ---------------------------------------------------------------------------

// --- Light theme -----------------------------------------------------------

/** Ocean teal: primary actions in light mode. */
val OceanTeal = Color(0xFF006A6A)
val OnOceanTeal = Color(0xFFFFFFFF)
val OceanTealContainer = Color(0xFF9CF1F0)
val OnOceanTealContainer = Color(0xFF002020)

/** Sunset coral: secondary accents in light mode. */
val CoralSunset = Color(0xFF8C4A2F)
val OnCoralSunset = Color(0xFFFFFFFF)
val CoralSunsetContainer = Color(0xFFFFDBD0)
val OnCoralSunsetContainer = Color(0xFF381000)

/** Golden sand: tertiary accents in light mode. */
val GoldenSand = Color(0xFF7A5A00)
val OnGoldenSand = Color(0xFFFFFFFF)
val GoldenSandContainer = Color(0xFFFFDE9C)
val OnGoldenSandContainer = Color(0xFF261A00)

/** Warm seashell neutrals for light surfaces. */
val SeashellBackground = Color(0xFFFAF8F5)
val OnSeashell = Color(0xFF1B1C1A)
val SeashellSurfaceVariant = Color(0xFFD9E4E4)
val OnSeashellSurfaceVariant = Color(0xFF3F4948)
val SeashellOutline = Color(0xFF6F7978)

// --- Dark theme ------------------------------------------------------------

/** Sea-foam teal: primary actions in dark mode. */
val DeepSeaTeal = Color(0xFF80D4D4)
val OnDeepSeaTeal = Color(0xFF003737)
val DeepSeaTealContainer = Color(0xFF005050)
val OnDeepSeaTealContainer = Color(0xFF9CF1F0)

/** Dusk coral: secondary accents in dark mode. */
val DuskCoral = Color(0xFFFFB59B)
val OnDuskCoral = Color(0xFF551D09)
val DuskCoralContainer = Color(0xFF71301B)
val OnDuskCoralContainer = Color(0xFFFFDBD0)

/** Moonlit sand: tertiary accents in dark mode. */
val MoonlitSand = Color(0xFFE9C46A)
val OnMoonlitSand = Color(0xFF3E2E00)
val MoonlitSandContainer = Color(0xFF594400)
val OnMoonlitSandContainer = Color(0xFFFFDE9C)

/** Deep abyss neutrals for dark surfaces. */
val AbyssBackground = Color(0xFF0F1413)
val OnAbyss = Color(0xFFDFE4E2)
val AbyssSurfaceVariant = Color(0xFF3F4948)
val OnAbyssSurfaceVariant = Color(0xFFBFC9C7)
val AbyssOutline = Color(0xFF899392)

// --- Semantic error colors (high contrast in both modes) --------------------

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
