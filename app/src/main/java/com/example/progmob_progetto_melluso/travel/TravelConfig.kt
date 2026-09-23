package com.example.progmob_progetto_melluso.travel

/**
 * Tunable thresholds that control how the travel path is recorded and when a
 * trip is considered finished.
 *
 * Exposed as a data class so that tests can inject small deterministic values
 * instead of the production defaults.
 *
 * @property minDistanceMeters Minimum distance from the last recorded point
 *   before a new path point is stored (spatial filter). Low enough to follow
 *   streets closely, high enough to skip micro-noise (battery saving).
 * @property minElapsedMillis Minimum time since the last recorded point before
 *   a new path point is stored (temporal filter, battery saving). Combined
 *   with an AND semantics on the spatial filter: both thresholds must be met.
 * @property maxAccuracyMeters Maximum horizontal accuracy error (in meters)
 *   accepted for a fix. Fixes reported as less precise than this are discarded
 *   when a previous point exists: they are mostly GPS noise while standing
 *   still and would draw zigzag artifacts on the path. The very first fix of
 *   a travel is always accepted, otherwise no anchor could ever be created
 *   with a cold GPS.
 * @property maxPlausibleSpeedKmh Fastest movement still considered real: a
 *   fix implying a higher speed is a GPS "teleport" (a wild jump or a stale
 *   fix delivered late), not a movement, and is dropped before it can draw a
 *   straight line across the map. 300 km/h leaves high-speed trains and
 *   airplanes outside the app's scope while cutting the impossible jumps.
 * @property tripEndDistanceMeters Straight-line distance from the trip's anchor
 *   point (its first recorded location) beyond which the trip is considered
 *   finished: leaving the vacation area by this distance means the vacation
 *   is over. The user is ASKED before the trip is closed (it is not closed
 *   silently in the background). Default 50 km.
 * @property tripEndDurationMillis Maximum open duration of the active trip
 *   (from its start) before the user is asked whether the vacation is still
 *   ongoing. Default 1 day: after that the "is your trip still going?"
 *   question appears; answering "keep" does NOT close the trip — it only
 *   stops asking (the question may reappear at the NEXT app open, so a
 *   really-ongoing vacation keeps accumulating its open time while one
 *   abandoned days ago is offered the close again on every new session).
 * @property autoStartMovementMeters Displacement (meters, straight-line) a
 *   foreground fix must show from the previous one before an automatic trip is
 *   opened (the moved-while-app-open auto-start). A buffer of a few tens of
 *   meters keeps sensor noise — urban multipath, indoor Wi-Fi fixes, jitter —
 *   from opening a trip while the user is actually sitting still with the app
 *   in front. MANUAL starts (map / journal buttons) bypass this gate
 *   completely, so a trip can be started standing still on purpose.
 * @property poiVisitRadiusMeters Geofencing radius around a POI: when a
 *   recorded path point falls inside it, the visit is registered in the
 *   travel journal (the POI proximity requirement). Configurable, not hardcoded.
 * @property ghostVisibilityRadiusMeters Maximum straight-line distance from
 *   the user position within which simulated travelers are considered visible
 *   (the local ghost simulation requirement / the map visibility limits: proximity filtering before rendering). This
 *   keeps the number of ghosts to render bounded regardless of how many users
 *   exist in the wider world.
 * @property maxGhostsShown Maximum number of ghost markers the map is allowed
 *   to render at once (marker cap, the map visibility limits). When more ghosts are within
 *   the visibility radius, the closest ones win; the load stays constant even
 *   with tens of thousands of simulated users.
 * @property locationIntervalMillis Desired interval between location fixes
 *   while a trip is being recorded (or the app is visible). It is the sampling
 *   rate of the fused provider, set equal to [minElapsedMillis] so the provider
 *   is exactly as fast as the path filter: no fix is fetched to be thrown away,
 *   and none is late when the filter opens. The value is a compromise between
 *   path quality and battery: positions are never precise, and more frequent
 *   requests let the provider's own fusion and smoothing do a better job (the
 *   sensing lecture shows a 3 s rate for that reason), while the filters below
 *   keep the number of WRITES — the expensive part — bounded.
 * @property locationMinDisplacementMeters Minimum displacement before the
 *   fused provider delivers a fix while a trip is recorded: standing still
 *   costs no fix at all. Matches the spatial granularity the path filter uses,
 *   for the same reason as [locationIntervalMillis].
 * @property pathSmoothingReferenceMeters Accuracy (in meters) up to which a fix
 *   is considered fully trustworthy. A fix noisier than this is blended toward
 *   the last recorded point by how much worse it is (see
 *   [PathCleaning.smooth]), so a noisy fix cannot drag the path away on its own.
 *   Kept below [maxAccuracyMeters]: the blends only ever apply to the fixes the
 *   accuracy gate already accepted, and a fully trusted fix is never altered.
 * @property idleLocationIntervalMillis Sampling interval used when NO trip is
 *   running and the app is not on screen: the only thing left to watch is the
 *   POI geofence ([poiVisitRadiusMeters]), which needs no second-by-second
 *   position.
 * @property idleLocationMinDisplacementMeters Displacement filter of the idle
 *   sampling: far below the geofence radius, so a notification is never missed,
 *   yet coarse enough to keep the radio asleep.
 * @property idleLocationMaxUpdateDelayMillis Batching window of the idle
 *   sampling: the provider may hold the fixes and deliver them together
 *   instead of waking the device for each one. Must stay greater than or equal
 *   to [idleLocationIntervalMillis] (fused-provider requirement).
 */
data class TravelConfig(
    val minDistanceMeters: Double = 5.0,
    val minElapsedMillis: Long = 5_000L,
    val maxAccuracyMeters: Float = 25f,
    val maxPlausibleSpeedKmh: Double = 300.0,
    val pathSmoothingReferenceMeters: Double = 15.0,
    val autoStartMovementMeters: Double = 30.0,
    val tripEndDistanceMeters: Double = 50_000.0,
    val tripEndDurationMillis: Long = 24L * 60L * 60L * 1_000L,
    val poiVisitRadiusMeters: Double = 150.0,
    val ghostVisibilityRadiusMeters: Double = 10_000.0,
    val maxGhostsShown: Int = 60,
    val locationIntervalMillis: Long = 5_000L,
    val locationMinDisplacementMeters: Float = 5f,
    val idleLocationIntervalMillis: Long = 60_000L,
    val idleLocationMinDisplacementMeters: Float = 100f,
    val idleLocationMaxUpdateDelayMillis: Long = 300_000L
)
