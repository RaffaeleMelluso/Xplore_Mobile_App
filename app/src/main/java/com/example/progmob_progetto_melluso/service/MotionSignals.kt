package com.example.progmob_progetto_melluso.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide carrier of the current motion state (context based on actions).
 *
 * Why it exists: the Activity Recognition transitions arrive in a
 * [android.content.BroadcastReceiver] (a component the system instantiates on
 * its own), while the decisions that read them live in the tracking service
 * coroutines. The receiver and the service share a process, so a state flow is
 * the smallest, testable bridge between the two — the same shape already used
 * for the position stream (`LocationUpdates`) and for the app-visibility signal
 * (`TrackingService.appInForeground`).
 *
 * How it works: the receiver publishes the state the pure [MotionTransitions]
 * reducer derives from each event; the service combines it with the trip state
 * and the app visibility to pick the sampling profile and to allow or refuse
 * the automatic trip start. It stays [MotionState.UNKNOWN] until the platform
 * classifies the user — a device without Activity Recognition, a denied
 * permission or a very recent boot — and every consumer treats UNKNOWN as "no
 * opinion", so the app keeps working exactly as before on its own signals.
 */
object MotionSignals {

    private val _motion = MutableStateFlow(MotionState.UNKNOWN)

    /** Current motion state, observable by the tracking service. */
    val motion: StateFlow<MotionState> = _motion.asStateFlow()

    /**
     * Publishes a new motion state.
     *
     * @param motion the state derived from the latest Activity Recognition event.
     */
    fun publish(motion: MotionState) {
        _motion.value = motion
    }

    /**
     * Forgets the classification.
     *
     * Called when the Activity Recognition updates are unregistered (tracking
     * torn down, permission revoked): an old classification must never survive
     * the subscription that produced it, otherwise the app would later decide
     * from a fact nobody is watching for anymore.
     */
    fun resetToUnknown() {
        _motion.value = MotionState.UNKNOWN
    }
}
