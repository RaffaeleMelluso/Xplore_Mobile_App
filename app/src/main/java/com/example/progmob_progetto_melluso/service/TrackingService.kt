package com.example.progmob_progetto_melluso.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that tracks the user position while the app is in the
 * background or the screen is off (spec §5.2).
 *
 * How it works: on start it promotes itself to a foreground service with a
 * persistent notification (so Android does not kill it), then requests
 * location updates from the fused location provider. Every fix is:
 * 1. broadcast to the UI through [LocationUpdates] (map rendering), and
 * 2. fed to the travel repository, which applies the spatial/temporal filters
 *    and appends it to the active travel path.
 *
 * The update interval and the minimum displacement match [TravelConfig]
 * defaults (10 s / 10 m): fixes that do not move the user are never requested,
 * which keeps battery usage low on old devices.
 */
class TrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Becomes true when the user asks to stop tracking (notification "Stop"
    // button, or swiping the app away). Persisted so a START_STICKY restart by
    // the system (common on OEM devices like Realme) does not re-post the
    // notification: the service refuses to start again.
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val sample = LocationSample(
                latitude = location.latitude,
                longitude = location.longitude,
                recordedAtUtcMillis = location.time,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            )
            // Let the map layer render the new position immediately.
            LocationUpdates.publish(sample)
            // Persist it into the active travel path (filters live in the repository).
            serviceScope.launch {
                AppContainer.travelRepository(applicationContext).recordLocation(sample)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Only promote to foreground if this is a real tracking session: a
        // system restart after the user already stopped must not flash the
        // notification (the stop is handled in onStartCommand below).
        if (!wasStopRequested()) {
            startAsForeground()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stop paths: the notification "Stop" action, or a system restart with
        // a null intent after the user already asked to stop (swipe-away on
        // aggressive OEM devices). In both cases do NOT re-post the notification.
        if (intent?.action == ACTION_STOP || (intent == null && wasStopRequested())) {
            requestStop()
            return START_NOT_STICKY
        }
        // Explicit start from the app (e.g. after login or reopening): resume
        // tracking and forget any previous stop request.
        clearStopRequest()
        startAsForeground()
        startLocationUpdates()
        // Restart the service if the system kills it while tracking is expected.
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Records the user's stop request and ends the service.
     *
     * How it works: the flag is persisted so that if the system restarts this
     * START_STICKY service (aggressive battery managers on some OEM devices
     * kill and restart processes), [onStartCommand] sees it and stops again
     * instead of re-posting the tracking notification.
     */
    private fun requestStop() {
        prefs.edit().putBoolean(KEY_STOP_REQUESTED, true).apply()
        stopSelf()
    }

    private fun wasStopRequested(): Boolean = prefs.getBoolean(KEY_STOP_REQUESTED, false)

    /** Called on an explicit app start: a new tracking session begins. */
    private fun clearStopRequest() {
        prefs.edit().remove(KEY_STOP_REQUESTED).apply()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away from the recents screen: end tracking
        // and remove the notification instead of keeping the foreground
        // service alive. Tracking still works while the app is only in the
        // background (screen off), because that path never removes the task.
        requestStop()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Promotes the service to foreground with the tracking notification.
     *
     * How it works: on Android 14+ the notification must declare the
     * foreground service type (location), so it uses [ServiceCompat] which
     * forwards the type on the supported API levels.
     */
    private fun startAsForeground() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    /**
     * Builds the persistent tracking notification.
     *
     * How it works: a low-importance channel keeps the notification quiet but
     * visible, as required for a foreground service.
     */
    private fun buildNotification(): android.app.Notification {
        // The "Stop" action lets the user end tracking and dismiss the
        // notification without opening the app.
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.tracking_stop), stopPendingIntent)
            .build()
    }

    /**
     * Requests location updates from the fused provider.
     *
     * How it works: guards against missing runtime permissions (the activity
     * requests them before starting this service, but the check keeps the
     * service safe if it is restarted by the system). Uses balanced power
     * accuracy plus the 10 s / 10 m thresholds to save battery.
     */
    private fun startLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED &&
                coarse != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .build()
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, locationListener, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        LocationServices.getFusedLocationProviderClient(this)
            .removeLocationUpdates(locationListener)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Notification action used to stop tracking from the notification. */
        const val ACTION_STOP = "com.example.progmob_progetto_melluso.action.STOP_TRACKING"

        private const val PREFS_NAME = "tracking_service"
        private const val KEY_STOP_REQUESTED = "stop_requested"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val STOP_REQUEST_CODE = 100
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_DISPLACEMENT_METERS = 10f
    }
}
