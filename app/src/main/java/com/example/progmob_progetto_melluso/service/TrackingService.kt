package com.example.progmob_progetto_melluso.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.progmob_progetto_melluso.MainActivity
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.PoiProximity
import com.example.progmob_progetto_melluso.travel.TravelConfig
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

    /**
     * Reacts to the user enabling location while the service is already
     * running (quick settings or system settings, no app restart).
     *
     * How it works: the fused provider usually resumes by itself, but asking
     * for an immediate current fix makes the map recover right away instead of
     * waiting for the next periodic update.
     */
    private val providerChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION && isLocationOn()) {
                requestCurrentLocation()
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location)
    }

    // The hardware step counter (TYPE_STEP_COUNTER, cumulative since boot):
    // feeds the pedometer repository while the service runs, so steps are
    // recorded in background exactly like the position. No extra permission is
    // required for this sensor type.
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val stepRepository by lazy { AppContainer.stepRepository(applicationContext) }
    private val stepSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull()?.toLong() ?: return
            serviceScope.launch { stepRepository.onStepCount(value) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    // Geofencing: the POI the user is currently inside (last notified), so the
    // near-POI notification is posted once per entry and re-armed when the user
    // leaves the fence.
    private var fenceNotifiedPoiId: Long? = null
    private val poiDao by lazy { AppContainer.database(applicationContext).poiDao() }
    private val travelConfig = TravelConfig()

    /**
     * Processes one location fix.
     *
     * How it works: converts the platform location into a provider-agnostic
     * [LocationSample], broadcasts it to the UI through [LocationUpdates] (map
     * rendering) and feeds it to the travel repository, which applies the
     * spatial/temporal filters and appends it to the active travel path.
     *
     * @param location the raw fix delivered by the fused location provider.
     */
    private fun handleLocation(location: Location) {
        val sample = LocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            recordedAtUtcMillis = location.time,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
        )
        // Let the map layer render the new position immediately.
        LocationUpdates.publish(sample)
        // Persist it into the active travel path (filters live in the repository)
        // and check the POI geofences for the near-POI notification.
        serviceScope.launch {
            AppContainer.travelRepository(applicationContext).recordLocation(sample)
            checkPoiFence(sample)
        }
    }

    /**
     * Posts the near-POI notification when the user enters a geofence.
     *
     * How it works: every fix is tested against the POI radius (the same
     * [TravelConfig.poiVisitRadiusMeters] the journal visit detection uses).
     * When a POI is inside the fence and it is not the one already notified,
     * a notification is posted; when the user is outside every fence the
     * notified id is cleared, so re-entering the same POI notifies again.
     * The map screen ALSO auto-opens the POI bottom sheet on the same signal
     * (computed independently in MapViewModel), so notification and UI agree.
     */
    private suspend fun checkPoiFence(sample: LocationSample) {
        val pois = poiDao.allPoisOnce()
        if (pois.isEmpty()) return
        val near = PoiProximity.near(
            sample.latitude,
            sample.longitude,
            pois,
            travelConfig.poiVisitRadiusMeters
        )
        if (near.isEmpty()) {
            fenceNotifiedPoiId = null
            return
        }
        // The closest POI inside the fence wins the notification.
        val closest = near.minByOrNull { poi ->
            kotlin.math.abs(poi.lat - sample.latitude) + kotlin.math.abs(poi.lon - sample.longitude)
        } ?: return
        if (fenceNotifiedPoiId == closest.id) return
        fenceNotifiedPoiId = closest.id
        postNearPoiNotification(closest.title)
    }

    /** Shows the "you are near <POI>" notification (separate channel). */
    private fun postNearPoiNotification(poiTitle: String) {
        val openApp = PendingIntent.getActivity(
            this,
            FENCE_OPEN_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, FENCE_CHANNEL_ID)
            .setContentTitle(getString(R.string.fence_notification_title))
            .setContentText(getString(R.string.fence_notification_text, poiTitle))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(FENCE_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createFenceNotificationChannel()
        // Only promote to foreground if this is a real tracking session: a
        // system restart after the user already stopped must not flash the
        // notification (the stop is handled in onStartCommand below).
        if (!wasStopRequested()) {
            startAsForeground()
        }
        // Listen for the location providers being turned on, so tracking can
        // resume with an immediate fix (see [providerChangedReceiver]).
        ContextCompat.registerReceiver(
            this,
            providerChangedReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Pedometer in background: the step counter sensor keeps running with
        // the same lifecycle as the location tracking (foreground service).
        registerStepSensor()
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
        unregisterReceiver(providerChangedReceiver)
        stopLocationUpdates()
        unregisterStepSensor()
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
     * service safe if it is restarted by the system). Uses high accuracy when
     * the fine location permission is granted (a street-adherent path needs
     * frequent, precise fixes), falling back to balanced power with coarse
     * permission only. The 5 s / 5 m thresholds keep the sampling dense; the
     * repository accuracy/spatial filters discard the resulting noise.
     */
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val priority = if (hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .build()
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, locationListener, Looper.getMainLooper())
    }

    /**
     * Asks the fused provider for a single fresh fix.
     *
     * How it works: called when the user enables location while the service is
     * already running, so the map gets a position without waiting for the next
     * periodic update (or an app restart). Failures are ignored: the periodic
     * [startLocationUpdates] request will deliver fixes shortly after.
     */
    private fun requestCurrentLocation() {
        if (!hasLocationPermission()) return
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location -> location?.let { handleLocation(it) } }
            .addOnFailureListener { /* periodic updates will cover it */ }
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            return fine == PackageManager.PERMISSION_GRANTED ||
                coarse == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** True when the app may use the GPS-grade fine location provider. */
    private fun hasFineLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun isLocationOn(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

    /** Channel of the near-POI (geofence) notifications. */
    private fun createFenceNotificationChannel() {
        val channel = NotificationChannel(
            FENCE_CHANNEL_ID,
            getString(R.string.fence_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Starts listening to the hardware step counter, when the device has one.
     * Devices without the sensor simply keep the pedometer at zero: the app
     * must not crash on unsupported hardware (spec §2.1, fault tolerance).
     */
    private fun registerStepSensor() {
        val sensor = stepSensor ?: return
        try {
            sensorManager.registerListener(
                stepListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                Handler(Looper.getMainLooper())
            )
        } catch (_: SecurityException) {
            // Android 10+: the ACTIVITY_RECOGNITION runtime permission was
            // denied, so the step sensor cannot be registered. The pedometer
            // stays at zero WITHOUT crashing the tracking service (fault
            // tolerance, spec §2.1).
        }
    }

    private fun unregisterStepSensor() {
        sensorManager.unregisterListener(stepListener)
    }

    companion object {
        /** Notification action used to stop tracking from the notification. */
        const val ACTION_STOP = "com.example.progmob_progetto_melluso.action.STOP_TRACKING"

        private const val PREFS_NAME = "tracking_service"
        private const val KEY_STOP_REQUESTED = "stop_requested"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val STOP_REQUEST_CODE = 100
        private const val FENCE_CHANNEL_ID = "geofence"
        private const val FENCE_NOTIFICATION_ID = 2
        private const val FENCE_OPEN_CODE = 101
        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_DISPLACEMENT_METERS = 5f
    }
}
