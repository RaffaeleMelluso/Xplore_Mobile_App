package com.example.progmob_progetto_melluso.travel

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the relationships the [TravelConfig] defaults must respect.
 *
 * The thresholds are tunable, but not independently: some combinations make the
 * app waste energy (asking for fixes the repository discards), and one of them
 * is an API requirement that would abort the location request at runtime. The
 * tests pin those relationships instead of the numbers, so the values stay
 * free to change while the constraints cannot be broken by accident.
 */
class TravelConfigTest {

    private val config = TravelConfig()

    /**
     * The fused provider requires the batching window to be at least the
     * sampling interval: a smaller one is rejected when the request is built.
     */
    @Test
    fun idleBatchingWindow_coversTheIdleInterval() {
        assertTrue(config.idleLocationMaxUpdateDelayMillis >= config.idleLocationIntervalMillis)
    }

    /**
     * The sampling interval must not be shorter than the path's temporal filter:
     * every fix in between would be discarded by the repository, so the radio
     * would pay for positions the app never stores.
     */
    @Test
    fun activeSampling_isNotFasterThanThePathFilter() {
        assertTrue(config.locationIntervalMillis >= config.minElapsedMillis)
    }

    /**
     * The provider must not be looser than the path's own spatial filter,
     * otherwise the repository drops fixes the radio already brought home.
     */
    @Test
    fun activeDisplacement_isNotSmallerThanThePathFilter() {
        assertTrue(config.locationMinDisplacementMeters >= config.minDistanceMeters.toFloat())
    }

    /** The idle profile is the cheap one: rarer and coarser than the active one. */
    @Test
    fun idleProfile_isCheaperThanTheActiveOne() {
        assertTrue(config.idleLocationIntervalMillis > config.locationIntervalMillis)
        assertTrue(config.idleLocationMinDisplacementMeters > config.locationMinDisplacementMeters)
    }

    /**
     * The idle displacement stays well below the geofence radius, so the cheap
     * profile never misses an entry notification.
     */
    @Test
    fun idleDisplacement_staysBelowTheGeofenceRadius() {
        assertTrue(config.idleLocationMinDisplacementMeters < config.poiVisitRadiusMeters)
    }

    /**
     * The smoothing only ever applies to fixes the accuracy gate already
     * accepted: a reference above the accepted error would leave a part of the
     * path that is never cleaned, and one at or below it would blend fixes that
     * are already precise enough to be trusted as they are.
     */
    @Test
    fun smoothingReference_staysInsideTheAcceptedAccuracy() {
        assertTrue(config.pathSmoothingReferenceMeters <= config.maxAccuracyMeters)
        assertTrue(config.pathSmoothingReferenceMeters > 0.0)
    }

    /**
     * The outlier gate must be far above anything the user can do on foot or by
     * car, otherwise it would cut real movements (a fast train ride, a plane).
     */
    @Test
    fun outlierGate_leavesRealTravelersAlone() {
        assertTrue(config.maxPlausibleSpeedKmh >= 200.0)
    }
}
