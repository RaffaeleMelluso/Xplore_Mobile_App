package com.example.progmob_progetto_melluso.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun haversineMeters_samePoint_isZero() {
        assertEquals(0.0, GeoUtils.haversineMeters(45.0, 7.0, 45.0, 7.0), 0.001)
    }

    @Test
    fun haversineMeters_oneDegreeLatitude_isAbout111Km() {
        val distance = GeoUtils.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, distance, 111_195.0 * 0.01)
    }

    @Test
    fun haversineMeters_oneDegreeLongitudeAtEquator_isAbout111Km() {
        val distance = GeoUtils.haversineMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, distance, 111_195.0 * 0.01)
    }

    @Test
    fun haversineMeters_isSymmetric() {
        val aToB = GeoUtils.haversineMeters(45.07, 7.68, 41.9, 12.5)
        val bToA = GeoUtils.haversineMeters(41.9, 12.5, 45.07, 7.68)
        assertEquals(aToB, bToA, 0.001)
    }
}
