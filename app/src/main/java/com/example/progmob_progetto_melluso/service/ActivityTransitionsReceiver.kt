package com.example.progmob_progetto_melluso.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Receives the Activity Recognition transitions and publishes the motion state.
 *
 * Why it exists: the transition API delivers its events to a `PendingIntent`,
 * i.e. to a component the platform can instantiate on its own even when the
 * activity is gone, so this is a [BroadcastReceiver] rather than a coroutine.
 * It is declared in the manifest with `android:exported="false"` and receives
 * the transitions through an intent whose component is set explicitly: nothing
 * outside the app can address it, and the PendingIntent cannot be intercepted
 * by another app (the platform's own guidance on PendingIntent hygiene).
 *
 * How it works: the platform fills a result into the intent; the receiver reads
 * it, reduces each event to a [MotionActivity] and asks the pure
 * [MotionTransitions] reducer which [MotionState] it implies. The state is then
 * published to [MotionSignals], where the tracking service observes it. The
 * receiver does no I/O and no processing on the main thread beyond that: the
 * work the platform asks for here must stay trivial.
 */
class ActivityTransitionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // Defensive: another broadcast could reach the same action, and the
        // lecture's own example validates the action before trusting a result.
        if (intent?.action != ACTION_ACTIVITY_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            val implied = MotionTransitions.impliedState(
                activity = activityOf(event.activityType),
                entering = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ) ?: return@forEach
            // Each event is published as soon as it is derived: the events of
            // one result are chronological, so the last one wins.
            MotionSignals.publish(implied)
        }
    }

    /**
     * Reduces the platform's activity id to the app's own vocabulary.
     *
     * How it works: everything the user does to GET SOMEWHERE counts as moving
     * (walking, running, cycling, driving) — which is what both consumers need:
     * the tracking profile must stay on while the user is approaching a place,
     * and an automatic trip may only open while the user is really on the move.
     * `STILL` is the one state that changes the decision, and the activities the
     * app has no opinion about (the unsupported `UNKNOWN` and `TILTING`) are
     * mapped to [MotionActivity.OTHER] so they never move the state.
     */
    private fun activityOf(activityType: Int): MotionActivity = when (activityType) {
        DetectedActivity.WALKING,
        DetectedActivity.RUNNING,
        DetectedActivity.ON_FOOT,
        DetectedActivity.ON_BICYCLE,
        DetectedActivity.IN_VEHICLE -> MotionActivity.MOVING

        DetectedActivity.STILL -> MotionActivity.STILL

        else -> MotionActivity.OTHER
    }

    companion object {
        /** Action of the transition broadcasts this receiver accepts. */
        const val ACTION_ACTIVITY_TRANSITION =
            "com.example.progmob_progetto_melluso.action.ACTIVITY_TRANSITION"
    }
}
