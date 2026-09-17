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
import com.example.progmob_progetto_melluso.travel.AutoStartGate
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.PoiProximity
import com.example.progmob_progetto_melluso.travel.TravelConfig
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service that tracks the user position while the app is in the
 * background or the screen is off (the background-tracking requirement).
 *
 * How it works: on start it promotes itself to a foreground service with a
 * persistent notification (so Android does not kill it), then requests
 * location updates from the fused location provider. Every fix is:
 * 1. broadcast to the UI through [LocationUpdates] (map rendering), and
 * 2. fed to the travel repository, which applies the spatial/temporal filters
 *    and appends it to the active travel path.
 *
 * How often and how precisely the position is sampled follows [TrackingMode]:
 * high accuracy while a trip is being recorded or the app is on screen (the
 * path must follow the user and the automatic start needs timely fixes), and
 * the cheap balanced profile with batched deliveries when no trip runs and the
 * app is off screen, where the only thing left to watch is the POI geofence.
 * Every threshold comes from [TravelConfig], so the provider is never asked for
 * fixes the repository would discard anyway.
 *
 * The AUTO-START of a trip is gated on real movement: with the app in the
 * foreground a trip opens only after [AutoStartGate] has seen the user move
 * at least [TravelConfig.autoStartMovementMeters] between two fixes (a noise
 * buffer of a few tens of meters), so sitting still with the app open never
 * starts a trip. MANUAL starts (map / journal buttons) do not pass here.
 *
 * The service also OWNS the tracked clock of the trip: it opens it when
 * tracking starts and closes it when tracking stops (see [onStartCommand] and
 * [onDestroy]). That is what makes the trip duration keep growing while the
 * phone tracks the user with the app in the background, instead of freezing at
 * the last time the app was on screen.
 */
class TrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
     * NOTE on foreground detection: `onStartCommand` cannot distinguish "the
     * app is visible" from "started while the screen is off", and the service
     * runs in the same process as the activity, so the current visibility is
     * read from the companion signal the ACTIVITY maintains
     * ([appInForeground], see the companion docs).
     */

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

    /**
     * Travel repository used only to bracket the tracked clock of the ongoing
     * trip (see [onStartCommand] / [onDestroy]).
     */
    private val travelRepository by lazy { AppContainer.travelRepository(applicationContext) }
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

    // Moved-while-app-open gate: a foreground fix opens a trip only when the
    // user really moved since the previous fix (the noise buffer keeps sensor
    // jitter from starting trips while sitting still). The gate is per
    // service instance, so a process restart always requires a fresh
    // displacement before an auto-start can fire again.
    private val autoStartGate by lazy { AutoStartGate(travelConfig.autoStartMovementMeters) }

    // Sampling profile currently requested from the fused provider, so a state
    // change that lands on the SAME profile does not re-issue the request:
    // each call is a binder round trip and resets the provider's internal
    // batching, and the state flow re-emits on every travel-table write.
    private var appliedMode: TrackingMode? = null

    // Whether the step listener is registered right now, so the trip-state
    // collector can ask for register/unregister repeatedly without ending up
    // with two registrations of the same listener.
    private var stepSensorRegistered = false

    // The single collector of [observeTrackingState], kept so a second start of
    // the service replaces it instead of adding another one.
    private var trackingStateJob: Job? = null

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
        // Persist it into the active travel path (filters live in the
        // repository). Auto-start is allowed ONLY while the app is open, and
        // only on a REAL displacement since the previous fix (AutoStartGate):
        // a fix arriving from the background, or a foreground jitter fix while
        // the user is still, never opens a trip (the manual/auto start
        // requirement). With a trip already active the flag is irrelevant —
        // the sample is recorded into it either way.
        serviceScope.launch {
            val autoStart = appInForeground.value &&
                autoStartGate.onFix(sample.latitude, sample.longitude)
            AppContainer.travelRepository(applicationContext)
                .recordLocation(sample, autoStart = autoStart)
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
        // A fix whose own reported error is larger than the fence cannot tell
        // whether the user is inside it: outside a trip the provider samples
        // with the cheaper IDLE profile, and such a fix would post a wrong
        // "you are near ..." notification. The repository applies the same
        // idea to the path, with its own tighter threshold.
        val reportedError = sample.accuracyMeters?.toDouble()
        if (reportedError != null && reportedError > travelConfig.poiVisitRadiusMeters) return
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
        // The step sensor is NOT registered here: [observeTrackingState] turns
        // it on only while a trip is actually being recorded.
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
        // Aligns the sampling profile and the pedometer with the current state
        // and keeps them aligned from here on.
        observeTrackingState()
        // The trip's tracked clock follows the TRACKING, not the app visibility:
        // while this service runs, the duration of an ongoing trip keeps growing
        // even with the app in the background (pocket mode), and the journal,
        // the map and the trip timer all read the same clock. The call is a
        // no-op when no trip is active and idempotent for the running one, so a
        // service restart never restarts the timer.
        serviceScope.launch { travelRepository.startSession() }
        // Request one fresh fix immediately when tracking starts. This is
        // important after the user grants location permission from the map:
        // waiting for the first periodic update made the map appear empty
        // until the activity was restarted.
        requestCurrentLocation()
        // Restart the service if the system kills it while tracking is expected.
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(providerChangedReceiver)
        stopLocationUpdates()
        unregisterStepSensor()
        // Close the tracked clock: the tail of the open segment becomes a
        // stored block, so the trip keeps the time it was really tracked and
        // stops growing once nothing tracks the user. It cannot run on
        // serviceScope, which is cancelled right below (the write would be
        // dropped); [teardownScope] is a single Room insert on the IO
        // dispatcher, so keeping it alive for the process is bounded.
        teardownScope.launch { travelRepository.endSession() }
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
     * Keeps the sampling profile and the pedometer aligned with what the app is
     * actually doing.
     *
     * How it works: it observes the two states that can change while the
     * service lives — whether a trip is running and whether the app is on
     * screen — and reacts to each change. Both sources emit immediately when
     * collected (the active-travel flow is a Room query with an initial
     * emission, the visibility is a state flow), so the first profile is
     * applied as soon as the service starts, and `distinctUntilChanged` makes
     * every later emission a no-op while the pair is unchanged.
     */
    private fun observeTrackingState() {
        // A single collector per service instance: onStartCommand may run again
        // (the map starts the service whenever it becomes usable), and stacking
        // collectors would leave orphan Room subscriptions behind.
        trackingStateJob?.cancel()
        trackingStateJob = serviceScope.launch {
            combine(
                travelRepository.observeActiveTravel(),
                appInForeground
            ) { travel, foreground ->
                TrackingState(tripActive = travel != null, appInForeground = foreground)
            }
                .distinctUntilChanged()
                .collect { state ->
                    requestLocationUpdates(
                        TrackingMode.of(state.tripActive, state.appInForeground)
                    )
                    // The pedometer has a destination only while a trip is being
                    // recorded: outside a trip the readings would only become a
                    // baseline, so the hardware sensor is not needed at all.
                    if (state.tripActive) registerStepSensor() else unregisterStepSensor()
                    // Leaving the screen re-arms the movement gate: the last
                    // foreground fix must not be compared with one taken after
                    // the user moved with the app closed, or reopening the app
                    // far from where it was left would open a trip by itself.
                    if (!state.appInForeground) autoStartGate.reset()
                }
        }
    }

    /**
     * The two facts that decide how the service tracks, grouped so the state
     * comparison is a single value: whether a trip is open and whether the app
     * is on screen.
     */
    private data class TrackingState(
        val tripActive: Boolean,
        val appInForeground: Boolean
    )

    /**
     * Requests location updates from the fused provider with the thresholds of
     * [mode].
     *
     * How it works: the request is built from [TravelConfig] and issued on the
     * single [locationListener], which the provider REPLACES together with the
     * previous request, so switching profile never leaves two streams running.
     * The IDLE profile also sets a batching window: with no trip and the app
     * off screen the fixes may be held and delivered together, letting the
     * radio sleep. A call that lands on the profile already in use is dropped,
     * and so is any call without the location permission (the activity asks for
     * it before starting the service, but the guard keeps a system restart
     * safe).
     */
    private fun requestLocationUpdates(mode: TrackingMode) {
        if (!hasLocationPermission()) return
        if (mode == appliedMode) return
        val active = mode == TrackingMode.ACTIVE
        // Without the fine location permission the platform only allows the
        // coarse provider: the profile is honoured as far as it can be.
        val priority = if (active && hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val intervalMillis = if (active) {
            travelConfig.locationIntervalMillis
        } else {
            travelConfig.idleLocationIntervalMillis
        }
        val displacementMeters = if (active) {
            travelConfig.locationMinDisplacementMeters
        } else {
            travelConfig.idleLocationMinDisplacementMeters
        }
        val builder = LocationRequest.Builder(priority, intervalMillis)
            .setMinUpdateDistanceMeters(displacementMeters)
        if (!active) {
            builder.setMaxUpdateDelayMillis(travelConfig.idleLocationMaxUpdateDelayMillis)
        }
        appliedMode = mode
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(builder.build(), locationListener, Looper.getMainLooper())
    }

    /**
     * Asks the fused provider for a single fresh fix.
     *
     * How it works: called when the user enables location while the service is
     * already running, so the map gets a position without waiting for the next
     * periodic update (or an app restart). Failures are ignored: the periodic
     * [requestLocationUpdates] stream will deliver fixes shortly after.
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
     * Starts listening to the hardware step counter while a trip is recorded,
     * when the device has one. Devices without the sensor simply keep the
     * pedometer at zero: the app must not crash on unsupported hardware (fault
     * tolerance). Idempotent, so the trip-state collector may call it on every
     * emission. The repository re-baselines from the first reading of a travel,
     * so registering per trip never imports steps walked outside it.
     */
    private fun registerStepSensor() {
        if (stepSensorRegistered) return
        val sensor = stepSensor ?: return
        try {
            sensorManager.registerListener(
                stepListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                Handler(Looper.getMainLooper())
            )
            stepSensorRegistered = true
        } catch (_: SecurityException) {
            // Android 10+: the ACTIVITY_RECOGNITION runtime permission was
            // denied, so the step sensor cannot be registered. The pedometer
            // stays at zero WITHOUT crashing the tracking service (fault
            // tolerance).
        }
    }

    private fun unregisterStepSensor() {
        if (!stepSensorRegistered) return
        stepSensorRegistered = false
        sensorManager.unregisterListener(stepListener)
    }

    companion object {
        /** Notification action used to stop tracking from the notification. */
        const val ACTION_STOP = "com.example.progmob_progetto_melluso.action.STOP_TRACKING"

        /**
         * Whether the app is on screen, published by the ACTIVITY lifecycle.
         *
         * How it works: the service keeps running with the screen off, so it
         * cannot know by itself whether the app is visible. The activity is the
         * only component whose lifecycle matches "the app is on screen", so it
         * writes the signal in onStart/onStop. Two decisions read it: the
         * automatic trip start is gated on it (a trip never opens from the
         * background) and the sampling profile ([TrackingMode]). A state flow
         * rather than a bare flag so the service can react to CHANGES of it
         * with the same collector that watches the trip state.
         */
        private val _appInForeground = MutableStateFlow(false)

        /** Observable app-visibility signal (see [_appInForeground]). */
        val appInForeground: StateFlow<Boolean> = _appInForeground.asStateFlow()

        /** Called by the activity in ON_START (true) and ON_STOP (false). */
        fun setAppInForeground(inForeground: Boolean) {
            _appInForeground.value = inForeground
        }

        /**
         * Scope for the write that must outlive the service: closing the
         * tracked clock in onDestroy cannot use the service scope, which is
         * cancelled immediately afterwards. The work is one Room insert on IO.
         */
        private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private const val PREFS_NAME = "tracking_service"
        private const val KEY_STOP_REQUESTED = "stop_requested"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val STOP_REQUEST_CODE = 100
        private const val FENCE_CHANNEL_ID = "geofence"
        private const val FENCE_NOTIFICATION_ID = 2
        private const val FENCE_OPEN_CODE = 101
    }
}
