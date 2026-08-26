package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity

/**
 * A distinct destination (e.g. a city) detected inside a travel path.
 *
 * @property lat centroid latitude of the visited area.
 * @property lon centroid longitude of the visited area.
 * @property arrivedAtUtcMillis timestamp of the first recorded point in this area.
 * @property pointCount how many recorded path points belong to this cluster.
 */
data class DetectedCity(
    val lat: Double,
    val lon: Double,
    val arrivedAtUtcMillis: Long,
    val pointCount: Int
)

/**
 * Groups the ordered path points of a travel into distinct destinations.
 *
 * How it works (deterministic, offline, no geocoding):
 * 1. The points are walked in time order keeping a current cluster.
 * 2. A new point joins the current cluster while it stays within
 *    [cityRadiusMeters] of the cluster's FIRST point (the area's anchor).
 * 3. When the user moves farther than that radius, the current cluster is
 *    finalized and a new one starts: that is a different place/city.
 * 4. A cluster is only kept as a destination when it holds at least
 *    [minPointsInCity] points; sparser clusters (a fast drive-by) are dropped
 *    as noise and their points merge into the following area.
 *
 * The algorithm never queries the network: it only clusters by haversine
 * distance, so it works fully offline and is unit-testable with deterministic
 * inputs (spec §4/§6).
 *
 * @param cityRadiusMeters Radius that groups points into the same destination.
 * @param minPointsInCity Minimum recorded points that make a cluster a real stop.
 */
class TravelCityDetector(
    private val cityRadiusMeters: Double = 6_000.0,
    private val minPointsInCity: Int = 3
) {

    /**
     * Detects the destinations present in [points] (must be time-ordered).
     *
     * @return Each detected city with its centroid, arrival time and point count.
     */
    fun detect(points: List<TravelPathPointEntity>): List<DetectedCity> {
        if (points.isEmpty()) return emptyList()
        return buildList {
            var cluster = mutableListOf<TravelPathPointEntity>()
            var anchor: TravelPathPointEntity? = null

            fun flush() {
                if (cluster.size >= minPointsInCity) {
                    val anchorPoint = cluster.first()
                    add(
                        DetectedCity(
                            lat = cluster.map { it.latitude }.average(),
                            lon = cluster.map { it.longitude }.average(),
                            arrivedAtUtcMillis = anchorPoint.recordedAtUtcMillis,
                            pointCount = cluster.size
                        )
                    )
                }
                cluster = mutableListOf()
                anchor = null
            }

            for (point in points) {
                val a = anchor
                if (a == null) {
                    cluster.add(point)
                    anchor = point
                    continue
                }
                val distance = GeoUtils.haversineMeters(
                    a.latitude, a.longitude, point.latitude, point.longitude
                )
                if (distance <= cityRadiusMeters) {
                    cluster.add(point)
                } else {
                    flush()
                    cluster.add(point)
                    anchor = point
                }
            }
            flush()
        }
    }
}