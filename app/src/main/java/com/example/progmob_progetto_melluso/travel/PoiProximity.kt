package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.core.GeoUtils
import com.example.progmob_progetto_melluso.data.entities.PoiEntity

/**
 * Pure proximity helper shared by the live visit detection (recording pipeline)
 * and the journal backfill: no storage, no side effects, deterministic inputs
 * so it is unit-testable (the POI proximity requirement).
 */
object PoiProximity {

    /**
     * Returns the POIs that lie within [radiusMeters] of the given point.
     *
     * @param lat latitude of the sample.
     * @param lon longitude of the sample.
     * @param pois the candidates to test.
     * @param radiusMeters the geofencing radius (configurable, not hardcoded).
     */
    fun near(lat: Double, lon: Double, pois: List<PoiEntity>, radiusMeters: Double): List<PoiEntity> =
        pois.filter { poi ->
            GeoUtils.haversineMeters(lat, lon, poi.lat, poi.lon) <= radiusMeters
        }
}
