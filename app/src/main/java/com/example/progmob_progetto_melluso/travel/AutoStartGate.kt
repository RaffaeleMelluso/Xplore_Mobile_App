package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils

/**
 * Pure decision gate for the MOVED-WHILE-APP-OPEN auto-start of a trip.
 *
 * Why it exists: the foreground tracking service keeps receiving fixes even
 * while the user sits still with the app open, and a single fix is not proof
 * of movement — GPS jitter, urban multipath or indoor Wi-Fi fixes routinely
 * displace the reported position by tens of meters. Opening a trip on the
 * first convenient fix would start vacations nobody asked for (the manual/
 * auto start requirement: auto only when the user actually moves with the app
 * open). The gate therefore demands a real DISPLACEMENT between consecutive
 * fixes: the user must be seen moving away by at least
 * [TravelConfig.autoStartMovementMeters] (a buffer of a few tens of meters)
 * before the auto-start fires.
 *
 * It is stateful on purpose: only the DISPLACEMENT BETWEEN FIXES matters, so
 * the previous fix must be kept. It is kept as a pure, clock-free class so
 * unit tests can inject the same samples the service sees, without the
 * service, the database or the clock.
 *
 * MANUAL starts (the map / journal buttons) bypass this gate completely:
 * deciding to travel standing still is the user's call, not the gate's.
 */
class AutoStartGate(
    private val thresholdMeters: Double = TravelConfig().autoStartMovementMeters
) {

    /** The previous accepted fix, kept only to measure displacement from it. */
    private var previousLatLon: Pair<Double, Double>? = null

    /**
     * Evaluates one foreground fix: true when the gate has SEEN the user
     * displace beyond the threshold between two consecutive fixes.
     *
     * How it works: every call updates the previous fix, so the gate compares
     * against the last fix it saw, not the first one (a user leaving a
     * building with a stale indoor anchor would otherwise have to cover the
     * whole drift twice). While the displacement stays under the threshold
     * the gate stays closed and returns false for every fix.
     *
     * @return true only when [distanceMetersFrom] between this and the
     *   previous fix is at least the threshold; false for the first fix ever
     *   (no displacement is observable yet) and while standing still.
     */
    fun onFix(latitude: Double, longitude: Double): Boolean {
        val previous = previousLatLon
        previousLatLon = latitude to longitude
        return previous != null &&
            GeoUtils.haversineMeters(previous.first, previous.second, latitude, longitude) >=
            thresholdMeters
    }

    /**
     * Forgets the previous fix, so the next one becomes a fresh reference.
     *
     * Why it is needed: the gate is fed only while the app is on screen. On
     * returning to the app, comparing a fresh fix with one taken before the
     * user left would measure the displacement accumulated with the app CLOSED
     * (a drive to the destination, a train ride) and would open a trip nobody
     * asked for. Called when the app leaves the screen, it keeps the rule
     * honest: an automatic trip can only open on a movement observed entirely
     * while the app was open.
     */
    fun reset() {
        previousLatLon = null
    }
}
