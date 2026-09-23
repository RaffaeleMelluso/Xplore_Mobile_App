package com.example.progmob_progetto_melluso.service

/**
 * Pure rule deciding whether a location fix may open a trip automatically.
 *
 * Why it exists: the automatic start is the most invasive thing the app can do
 * on its own (it creates a vacation nobody asked for), so it is gated by two
 * independent, deliberately different signals:
 *
 * 1. `movedBeyondBuffer` — the user has really DISPLACED since the previous
 *    fix ([com.example.progmob_progetto_melluso.travel.AutoStartGate], a noise
 *    buffer of a few tens of meters);
 * 2. `motion` — the Activity Recognition state says the user is actually
 *    getting somewhere, which is the "start tracking when the user starts
 *    moving" recipe of the context lecture, applied to a trip.
 *
 * Displacement alone can be fooled by GPS jumps (urban multipath, an indoor
 * Wi-Fi fix); the activity alone can be reported while the user stays put
 * (standing up, a brief walk to the bin). Requiring both makes the automatic
 * start much harder to trigger by accident, while a manual start always stays
 * possible: the user's decision never needs to pass here.
 *
 * The state is a three-valued signal on purpose: [MotionState.UNKNOWN] means
 * "no opinion" (no Activity Recognition on the device, permission refused, or
 * nothing classified yet) and must fall back to the pre-existing behaviour,
 * otherwise the feature would silently stop working on those devices.
 */
object AutoStartPolicy {

    /**
     * Whether a fix that passed the displacement gate may open a trip.
     *
     * @param appInForeground true when the app is on screen: a trip must never
     *   open from the background.
     * @param movedBeyondBuffer true when the movement gate has seen a real
     *   displacement between two fixes.
     * @param motion the current Activity Recognition state, UNKNOWN when the
     *   platform has no opinion.
     * @return true only when every available signal agrees.
     */
    fun shouldStart(
        appInForeground: Boolean,
        movedBeyondBuffer: Boolean,
        motion: MotionState
    ): Boolean = appInForeground && movedBeyondBuffer && motion != MotionState.STILL
}
