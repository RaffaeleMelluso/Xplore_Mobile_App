package com.example.progmob_progetto_melluso.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Subscription to the Activity Recognition transition API (context based on
 * actions).
 *
 * Why it exists: the app needs two things the position stream cannot give it.
 * The first is an honest answer to "is the user moving?" — a displacement
 * between two fixes is a guess, and a guess is what the automatic trip start
 * was built on until now ([AutoStartPolicy] adds this as a second vote). The
 * second is the ability to STOP tracking that is safe: with the only remaining
 * reason for a fix being the POI geofence, the app may put the GPS to sleep
 * while the user is standing still ([TrackingMode.PAUSED]) *if* something will
 * wake it up when they move again — and this subscription is that something.
 * It is the "context based on actions" pattern of the context lecture: the
 * platform watches the transitions, the app is woken only when it matters.
 *
 * How it works: every activity of interest (`ON_FOOT`, `WALKING`, `RUNNING`,
 * `ON_BICYCLE`, `IN_VEHICLE`, `STILL`) is subscribed for BOTH directions of the
 * transition; the system then delivers only the changes it detects to
 * [ActivityTransitionsReceiver], which publishes them through [MotionSignals].
 *
 * The subscription is owned by the tracking service: it is registered while the
 * service lives (i.e. exactly while the tracking decisions are being taken) and
 * removed when the service is torn down, so the app never subscribes to a
 * sensor-like stream it is not using — the "stop what you don't need" rule of
 * the sensing lecture. A device without the API, a refused permission or a
 * failed registration leaves the state at [MotionState.UNKNOWN], which every
 * consumer reads as "no opinion": the app degrades to the behaviour it had
 * before this signal existed instead of breaking.
 *
 * @param context any context; the application context is used internally.
 */
class ActivityRecognitionMonitor(private val context: Context) {

    /** Whether the subscription is currently registered. */
    private var started = false

    /**
     * PendingIntent the platform currently delivers the transitions to, or null
     * when the app is not subscribed.
     *
     * It is rebuilt for every subscription instead of being cached: the
     * official guide cancels it once the removal is confirmed, and a cancelled
     * PendingIntent must not be handed out again. Two equivalent PendingIntents
     * (same component, action and request code) match each other, so rebuilding
     * one for the removal is correct.
     */
    private var transitionsPendingIntent: PendingIntent? = null

    /**
     * Builds the PendingIntent the platform delivers the transitions to.
     *
     * It targets OUR receiver explicitly, which is the documented way to make a
     * PendingIntent un-interceptable. The mutability flag is the one documented
     * exception to the "always immutable" rule: the Activity Recognition client
     * must WRITE the detected activities into this intent, so it cannot be
     * immutable (an immutable PendingIntent is exactly what the platform refuses
     * to fill in). From Android 12 the mutable flag is the only one that works
     * for this API, hence the version check.
     */
    private fun buildTransitionsPendingIntent(): PendingIntent {
        val intent = Intent(context.applicationContext, ActivityTransitionsReceiver::class.java)
            .setAction(ActivityTransitionsReceiver.ACTION_ACTIVITY_TRANSITION)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context.applicationContext, REQUEST_CODE, intent, flags)
    }

    /**
     * Subscribes to the transitions, if the permission allows it.
     *
     * Idempotent: a second call for an already registered subscription does
     * nothing, while a call after a refused permission has no effect and leaves
     * the state UNKNOWN. A later successful call (the user granted the
     * permission, the service started again) registers normally, so granting the
     * permission at any point is enough — no restart of the app required.
     */
    fun start() {
        if (started) return
        if (!hasActivityRecognitionPermission()) {
            MotionSignals.resetToUnknown()
            return
        }
        started = true
        val pendingIntent = transitionsPendingIntent ?:
            buildTransitionsPendingIntent().also { transitionsPendingIntent = it }
        ActivityRecognition.getClient(context.applicationContext)
            .requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitionsOfInterest()),
                pendingIntent
            )
            .addOnFailureListener {
                // No transitions will arrive: the state stays "no opinion" so
                // every consumer falls back to the signals it already had, and
                // the next start() (permission granted, service restarted)
                // simply tries again.
                started = false
                MotionSignals.resetToUnknown()
            }
    }

    /** Removes the subscription and forgets the classification it produced. */
    fun stop() {
        val pendingIntent = transitionsPendingIntent
        transitionsPendingIntent = null
        started = false
        // The state is forgotten right away: after this call nothing is watching
        // for transitions, so keeping a stale classification would let the app
        // decide from a fact nobody is monitoring anymore.
        MotionSignals.resetToUnknown()
        if (pendingIntent == null) return
        // The removal is asynchronous and may fail (the platform may have
        // removed it already on its own). On success the PendingIntent is
        // cancelled, as the official guide prescribes: it is no longer needed
        // and holding on to it would keep an intent alive for nothing.
        runCatching {
            ActivityRecognition.getClient(context.applicationContext)
                .removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener { pendingIntent.cancel() }
        }
    }

    /**
     * The transitions to subscribe to: every activity of interest, in both
     * directions.
     *
     * How it works: the API is requested per (activity, direction) pair, so a
     * state machine like [MotionTransitions] needs both. The activities are
     * exactly the ones the official transition guide lists (driving, cycling,
     * running, walking, standing still) plus nothing else: `ON_FOOT` is NOT
     * subscribed even though it exists as a coarser parent of walking/running,
     * because the guide does not list it for this API and a request carrying a
     * type the platform does not accept would be refused as a whole — losing
     * every transition instead of one.
     */
    private fun transitionsOfInterest(): List<ActivityTransition> {
        val activities = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.STILL
        )
        val directions = listOf(
            ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            ActivityTransition.ACTIVITY_TRANSITION_EXIT
        )
        return activities.flatMap { activity ->
            directions.map { direction ->
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(direction)
                    .build()
            }
        }
    }

    /** True when the platform will deliver transitions to this app. */
    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Request code of the transitions PendingIntent (one per app). */
        private const val REQUEST_CODE = 200
    }
}
