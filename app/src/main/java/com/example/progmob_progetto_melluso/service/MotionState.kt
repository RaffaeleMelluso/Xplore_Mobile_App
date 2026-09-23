package com.example.progmob_progetto_melluso.service

/**
 * What the platform says the user is DOING (context based on actions).
 *
 * Why it exists: up to here the app described its context with position alone
 * — a trip is open, the app is on screen, the user moved enough between two
 * fixes. That last one is a guess: a displacement between two fixes cannot
 * tell walking from a GPS jump, and it cannot tell moving from driving. The
 * Activity Recognition transition API answers exactly that question, and it
 * answers it with the system's own detector (cell, Wi-Fi, accelerometer,
 * significant motion), so the app no longer has to infer motion from noise.
 *
 * How it is used: [MotionState] is the small, GMS-free vocabulary the app
 * decisions are written against — the sampling profile
 * ([TrackingMode]) and the automatic trip start ([AutoStartPolicy]) — while
 * the mapping from the platform's `DetectedActivity` ids lives in the Android
 * layer ([ActivityRecognitionMonitor]), so the whole decision logic stays
 * unit-testable on the JVM.
 */
enum class MotionState {

    /** The platform has not classified the user yet (or cannot). */
    UNKNOWN,

    /** The user is standing still: the trip auto-start must not fire. */
    STILL,

    /** The user is on foot, on a bike or in a vehicle: they are moving. */
    MOVING
}

/**
 * A platform activity id, reduced to the only distinction the app needs.
 *
 * The reduction happens in the Android layer, where the `DetectedActivity`
 * constants are available; everything downstream works on this enum.
 */
enum class MotionActivity {

    /** Standing still. */
    STILL,

    /** Walking, running, cycling or driving: the user is getting somewhere. */
    MOVING,

    /** Activity the app has no opinion about (e.g. `UNKNOWN`, `TILTING`). */
    OTHER
}

/**
 * Pure reducer of Activity Recognition transitions into a [MotionState].
 *
 * Why it exists: the transition stream is event-shaped ("WALKING ENTER",
 * "STILL EXIT"), while both consumers need a state ("is the user moving?").
 * Deriving it in one pure place keeps the rule explicit, testable and
 * identical for every consumer.
 *
 * How it works — the only events that change the state are:
 * - ENTER of a moving activity: the user is moving;
 * - ENTER of STILL: the user stopped;
 * - EXIT of STILL: stillness ended, so the user is moving again. This is the
 *   event that wakes the tracker up after a pause.
 *
 * EXIT of a moving activity is deliberately IGNORED: walking into a car is a
 * `WALKING EXIT` followed by an `IN_VEHICLE ENTER` in the same result, so
 * treating the exit as "now still" would produce a spurious stop (and a
 * pointless location re-request) between two moving activities. The next ENTER
 * decides, which is exactly what the platform promises to send.
 */
object MotionTransitions {

    /**
     * The motion state an activity transition implies.
     *
     * @param activity the reduced activity id.
     * @param entering true for `ACTIVITY_TRANSITION_ENTER`, false for EXIT.
     * @return the state to publish, or null when this event changes nothing.
     */
    fun impliedState(activity: MotionActivity, entering: Boolean): MotionState? = when {
        activity == MotionActivity.OTHER -> null
        entering && activity == MotionActivity.MOVING -> MotionState.MOVING
        entering && activity == MotionActivity.STILL -> MotionState.STILL
        !entering && activity == MotionActivity.STILL -> MotionState.MOVING
        else -> null
    }
}
