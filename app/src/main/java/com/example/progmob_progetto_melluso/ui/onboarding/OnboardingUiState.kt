package com.example.progmob_progetto_melluso.ui.onboarding

/**
 * Immutable UI state of the first-run onboarding.
 *
 * How it works: [completed] is deliberately tri-state. `null` means the
 * persisted flag has not been read from DataStore yet, so the host activity can
 * show a neutral background instead of flashing the map (or the login) for a
 * frame before the pages appear; `false` and `true` are the real outcomes.
 */
data class OnboardingUiState(
    /** Outcome of the first-run pages, or null while it is still loading. */
    val completed: Boolean? = null
)
