package com.example.progmob_progetto_melluso.travel

/**
 * Pure cleaning rules applied to the recorded path before a point is stored.
 *
 * Why it exists: locations are never precise. The provider occasionally
 * reports a fix that is not merely noisy but *impossible* — a "teleport" of
 * hundreds of meters or a fix dated in the past — and a single one of them
 * draws a straight line across the map that destroys the shape of the whole
 * trip. The sensing lecture states the rule as "paths need cleaning" and lists
 * smoothing (e.g. Kalman filters) as the general answer, while warning that
 * smoothing is computationally expensive. The two rules here are the cheap,
 * bounded version of that idea, applied where the path is built:
 *
 * 1. [isPlausibleMovement] drops a fix implying a speed no traveler reaches
 *    (a GPS teleport), so no outlier can ever enter the path;
 * 2. [smooth] blends a LOW-CONFIDENCE fix toward the last known point instead
 *    of trusting it fully, damping the zigzag drawn while standing still or
 *    walking between buildings. A confident fix is stored verbatim: smoothing
 *    a good measurement would only make the path lag behind the user.
 *
 * Both are pure functions so the rules can be unit-tested with plain numbers,
 * without a device, a provider or a database.
 */
object PathCleaning {

    /**
     * Whether two consecutive fixes describe a movement a traveler could have
     * made, given the time between them.
     *
     * How it works: the implied speed is compared with [maxSpeedMetersPerSecond].
     * Two degenerate cases are handled explicitly: no displacement is always
     * plausible (standing still tells nothing about speed), and a positive
     * displacement with no elapsed time is NOT (an infinite speed means the two
     * fixes cannot belong to the same trajectory, e.g. a stale cached fix
     * delivered with a newer timestamp).
     *
     * @param distanceMeters straight-line distance between the two fixes.
     * @param elapsedMillis time between the two fixes.
     * @param maxSpeedMetersPerSecond fastest movement still considered real.
     * @return true when the step is physically possible.
     */
    fun isPlausibleMovement(
        distanceMeters: Double,
        elapsedMillis: Long,
        maxSpeedMetersPerSecond: Double
    ): Boolean {
        val elapsed = elapsedMillis / 1_000.0
        return when {
            distanceMeters <= 0.0 -> true
            elapsed <= 0.0 -> false
            else -> distanceMeters / elapsed <= maxSpeedMetersPerSecond
        }
    }

    /**
     * Blends a fix toward the previous recorded point by how much it can be
     * trusted, returning the coordinates to store.
     *
     * How it works: the horizontal accuracy reported by the provider is the
     * confidence. A fix at least as precise as [referenceMeters] is taken as it
     * is (weight 1: it is a measurement, not noise). A worse one is weighted by
     * `referenceMeters / accuracy`, so it is pulled along the segment toward the
     * last known point — a fix with twice the reference error keeps 50% of its
     * movement, a fix at the reference keeps all of it. The weight stays inside
     * (0, 1), so the smoothed point can never overshoot the movement nor end up
     * behind the previous point: the correction is bounded by construction, no
     * filter to tune and no Kalman machinery to run.
     *
     * A fix carrying no accuracy information is trusted as it is: inventing a
     * confidence for it would be worse than using the measurement.
     *
     * @param previousLatitude latitude of the last recorded point.
     * @param previousLongitude longitude of the last recorded point.
     * @param nextLatitude latitude of the new fix (raw).
     * @param nextLongitude longitude of the new fix (raw).
     * @param nextAccuracyMeters accuracy reported by the provider, when present.
     * @param referenceMeters accuracy up to which a fix is considered fully
     *   trustworthy; it is also the minimum weight denominator.
     * @return the latitude/longitude pair to store.
     */
    fun smooth(
        previousLatitude: Double,
        previousLongitude: Double,
        nextLatitude: Double,
        nextLongitude: Double,
        nextAccuracyMeters: Double?,
        referenceMeters: Double
    ): Pair<Double, Double> {
        val accuracy = nextAccuracyMeters
        if (accuracy == null || referenceMeters <= 0.0 || accuracy <= referenceMeters) {
            return nextLatitude to nextLongitude
        }
        val weight = referenceMeters / accuracy
        return (previousLatitude + (nextLatitude - previousLatitude) * weight) to
            (previousLongitude + (nextLongitude - previousLongitude) * weight)
    }
}
