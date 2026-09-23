package com.example.progmob_progetto_melluso.service

/**
 * How much the tracking service should spend to know where the user is.
 *
 * Why three profiles: the app tracks continuously, but the reason to know the
 * position is not the same all the time. While a trip is being recorded the
 * path must follow the user closely ([ACTIVE]) and so must the automatic trip
 * start, which needs timely fixes while the app is on screen. Once no trip is
 * running AND the app is off screen, the only thing left to watch is the POI
 * geofence: a minute-by-minute fix is enough, and the cheaper provider
 * ([IDLE]) saves the GPS and the radio. And when that same situation is joined
 * by the platform saying the user is STANDING STILL ([PAUSED]), even the cheap
 * fixes are pointless — a still user cannot enter a geofence — so the updates
 * are stopped altogether and the Activity Recognition transitions of
 * [MotionSignals] are what starts them again.
 *
 * PAUSED is the closest the app gets to the "geofences are fine with battery
 * power" property of the platform geofencing API: a real geofence is monitored
 * by the OS without requesting continuous positions, and this is the app's own
 * equivalent — no position requested at all while nothing can happen, and a
 * wake-up driven by context instead of by a timer.
 *
 * The decision is a pure function of the three signals so it is unit-testable
 * without a device, without the fused provider and without the platform's
 * activity detector.
 */
enum class TrackingMode {

    /** High accuracy, sampling rate from [com.example.progmob_progetto_melluso.travel.TravelConfig]. */
    ACTIVE,

    /** Cheap balanced-power sampling with batching: geofence-level accuracy. */
    IDLE,

    /** No location requested at all: nothing can happen until the user moves. */
    PAUSED;

    companion object {

        /**
         * Picks the profile for the current state.
         *
         * How it works: a running trip always needs precision (its path and its
         * steps are being recorded), and so does a visible app, because that is
         * the only situation in which a trip can open by itself when the user
         * moves. Only "no trip and app off screen" may degrade — to [IDLE] when
         * the user may be moving, to [PAUSED] when the platform says they are
         * not. [MotionState.UNKNOWN] means the platform cannot answer (no
         * Activity Recognition, refused permission, nothing classified yet) and
         * deliberately falls back to [IDLE]: the app must not stop tracking on
         * the strength of a signal it cannot read.
         *
         * @param tripActive true when a travel is currently open.
         * @param appInForeground true when the app is on screen.
         * @param motion current Activity Recognition state, UNKNOWN when none.
         */
        fun of(
            tripActive: Boolean,
            appInForeground: Boolean,
            motion: MotionState = MotionState.UNKNOWN
        ): TrackingMode = when {
            tripActive || appInForeground -> ACTIVE
            motion == MotionState.STILL -> PAUSED
            else -> IDLE
        }
    }
}
