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
 * @property tripEndDistanceMeters Straight-line distance from the trip's anchor
 *   point (its first recorded location) beyond which the trip is considered
 *   finished: leaving the vacation area by this distance means the vacation
 *   is over. The user is ASKED before the trip is closed (it is not closed
 *   silently in the background). Default 50 km.
 * @property tripEndDurationMillis Maximum age of the active trip (from its
 *   start) before the user is asked whether the vacation is still ongoing.
 *   Default 3 days: a trip started days ago that is still "active" is most
 *   likely a leftover of a past vacation.
 * @property poiVisitRadiusMeters Geofencing radius around a POI: when a
 *   recorded path point falls inside it, the visit is registered in the
 *   travel journal (spec §7.3). Configurable, not hardcoded.
 * @property ghostVisibilityRadiusMeters Maximum straight-line distance from
 *   the user position within which simulated travelers are considered visible
 *   (spec §5.3bis / checklist §6: proximity filtering before rendering). This
 *   keeps the number of ghosts to render bounded regardless of how many users
 *   exist in the wider world.
 * @property maxGhostsShown Maximum number of ghost markers the map is allowed
 *   to render at once (marker cap, checklist §6). When more ghosts are within
 *   the visibility radius, the closest ones win; the load stays constant even
 *   with tens of thousands of simulated users.
 */
data class TravelConfig(
    val minDistanceMeters: Double = 5.0,
    val minElapsedMillis: Long = 10_000L,
    val maxAccuracyMeters: Float = 25f,
    val tripEndDistanceMeters: Double = 50_000.0,
    val tripEndDurationMillis: Long = 2L * 24L * 60L * 60L * 1_000L,
    val poiVisitRadiusMeters: Double = 150.0,
    val ghostVisibilityRadiusMeters: Double = 10_000.0,
    val maxGhostsShown: Int = 60
)
