package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simulates other travelers ("ghosts") moving around the user.
 *
 * How it works: there is no backend yet (spec §6), so this class generates a
 * small number of ghosts that orbit the current user position on circular
 * trajectories. The position of each ghost depends only on the current time
 * and the center: given the same inputs the output is identical, which makes
 * the simulator fully deterministic and unit-testable. When a real backend is
 * added, only the ghost data source changes; the rendering pipeline stays.
 *
 * @param ghostCount number of simulated travelers to generate.
 * @param radiusMeters orbit radius around the user position.
 * @param speedDegreesPerSecond angular speed of the orbit in degrees/second.
 */
class GhostSimulator(
    private val ghostCount: Int = 3,
    private val radiusMeters: Double = 1_500.0,
    private val speedDegreesPerSecond: Double = 2.0
) {

    /**
     * Computes the current ghost positions around a center.
     *
     * @param centerLat latitude of the center (user position) in degrees.
     * @param centerLon longitude of the center (user position) in degrees.
     * @param nowMillis current time in UTC millis, used as the animation clock.
     * @return the list of ghosts, ordered by id; their `userId` is stable so the
     *   UI can update markers in place instead of recreating them.
     */
    fun tick(centerLat: Double, centerLon: Double, nowMillis: Long): List<GhostEntity> {
        require(ghostCount > 0) { "ghostCount must be positive" }
        val seconds = nowMillis / 1000.0
        return (0 until ghostCount).map { index ->
            // Evenly space the ghosts on the orbit and rotate them over time.
            val phaseDegrees = seconds * speedDegreesPerSecond + index * (360.0 / ghostCount)
            val phaseRadians = Math.toRadians(phaseDegrees % 360.0)
            val deltaLat = radiusMeters / METERS_PER_DEGREE_LAT * cos(phaseRadians)
            // Longitude degrees shrink as latitude grows, so scale the offset.
            val metersPerDegreeLon = METERS_PER_DEGREE_LAT * cos(Math.toRadians(centerLat))
            val deltaLon = radiusMeters / metersPerDegreeLon * sin(phaseRadians)
            GhostEntity(
                userId = "ghost-$index",
                lat = centerLat + deltaLat,
                lon = centerLon + deltaLon,
                timestamp = nowMillis
            )
        }
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
