package com.example.progmob_progetto_melluso.ghost

import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.map.DemoPoiSeed
import com.example.progmob_progetto_melluso.travel.TravelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the deterministic ghost generation of [GhostFactory].
 *
 * The factory simulates other travelers along realistic routes in two demo
 * zones (Turin / Cerignola). These tests verify determinism, zone selection,
 * the bounded visibility (radius filter + marker cap, the map visibility limits) and the
 * motion of the online ghosts.
 */
class GhostFactoryTest {

    @Test
    fun tick_isDeterministic() {
        val factory = GhostFactory()
        val first = factory.tick(45.09, 7.66, 1_000L)
        val second = factory.tick(45.09, 7.66, 1_000L)
        assertEquals("same inputs must give the same outputs", first, second)
    }

    @Test
    fun tick_nearTurinReturnsTwentyTurinGhosts() {
        val factory = GhostFactory()
        val ghosts = factory.tick(45.0905, 7.6598, 1_000L) // C.so Svizzera / Dip. Informatica
        assertEquals("Turin is populated with 20 travelers", 20, ghosts.size)
        assertTrue(
            "all returned ghosts must belong to the Turin zone",
            ghosts.all { it.marker.userId?.startsWith("ghost-turin") == true }
        )
    }

    @Test
    fun tick_nearCerignolaReturnsFiveCerignolaGhosts() {
        val factory = GhostFactory()
        val ghosts = factory.tick(41.2642, 15.8995, 1_000L) // Cattedrale, centro Cerignola
        assertEquals("Cerignola is populated with 5 travelers", 5, ghosts.size)
        assertTrue(
            "all returned ghosts must belong to the Cerignola zone",
            ghosts.all { it.marker.userId?.startsWith("ghost-cerignola") == true }
        )
    }

    @Test
    fun tick_featuresUniformIds() {
        val factory = GhostFactory()
        val ids = factory.tick(45.09, 7.66, 1_000L).map { it.marker.userId }
        assertEquals("user ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun tick_filtersFarGhostsByVisibilityRadius() {
        // Ghosts whose marker is more than the visibility radius away from the
        // user are dropped before returning (proximity filtering, the map visibility limits).
        val factory = GhostFactory(
            config = TravelConfig(ghostVisibilityRadiusMeters = 1.0) // tiny radius
        )
        val ghosts = factory.tick(0.0, 0.0, 1_000L) // far from both demo zones
        assertTrue("no ghost is within 1 m of an arbitrary point", ghosts.isEmpty())
    }

    @Test
    fun tick_keepsAtMostMaxGhostsShown() {
        // Cap: even when several ghosts are in range, only the closest
        // `maxGhostsShown` are returned (bounded marker load).
        val factory = GhostFactory(
            config = TravelConfig(maxGhostsShown = 1)
        )
        val ghosts = factory.tick(45.0905, 7.6598, 1_000L)
        assertTrue("at most maxGhostsShown ghosts are returned", ghosts.size <= 1)
    }

    @Test
    fun tick_onlineGhostsMoveOverTime() {
        // An online ghost advances along its route only after the fixed motion
        // epoch, so use timestamps AFTER it — otherwise both inputs sit before
        // the zero-base and the ghost would still be at its start.
        val epoch = 1_777_363_200_000L // fixed ONLINE_EPOCH_MILLIS in GhostFactory
        val factory = GhostFactory()
        val atStart = factory.tick(45.09, 7.66, epoch).first { it.isOnlineNow }
        val later = factory.tick(45.09, 7.66, epoch + 60_000L).first { it.isOnlineNow }
        assertNotEquals(atStart.marker.lat, later.marker.lat)
    }

    @Test
    fun tick_onlineGhostsStayOnTheirRoute() {
        // The current position of an online ghost must lie on (or very close
        // to) its own route waypoints — real motion, not a jump.
        val factory = GhostFactory()
        val ghost = factory.tick(45.09, 7.66, 30_000L).first { it.isOnlineNow }
        val routeMinDistance = ghost.path.minOf { (lat, lon) ->
            haversineMeters(lat, lon, ghost.marker.lat, ghost.marker.lon)
        }
        assertTrue("ghost must be near its route", routeMinDistance < 50.0)
    }

    @Test
    fun tick_visitsUseRealPoiIdsAndScores() {
        // The ghost cards must show the REAL community score (and the real POI
        // id), not a hardcoded value: this is how they stay in sync with the
        // votes the trends page aggregates.
        val factory = GhostFactory()
        val poi = PoiEntity(
            id = 42L,
            lat = 45.0,
            lon = 7.0,
            title = "Mole Antonelliana",
            description = null,
            category = null,
            createdByUserId = null,
            city = "Torino",
            votes = 5
        )
        val ghosts = factory.tick(45.09, 7.66, 1_000L, mapOf("Mole Antonelliana" to poi))
        val visit = ghosts.flatMap { it.visits }.firstOrNull { it.poiTitle == "Mole Antonelliana" }
        assertNotNull("a ghost must visit the Mole Antonelliana", visit)
        assertEquals("the visit carries the real POI id", 42L, visit!!.poiId)
        assertEquals("the visit carries the real community score", 5, visit.score)
    }

    @Test
    fun demoGhostVisits_resolveToSeededPoiTitles() {
        // Every place the ghosts visited must exist as a seeded POI, so each
        // ghost vote can become a real poi_votes row and trends/ghosts agree.
        val titles = (DemoPoiSeed.turin + DemoPoiSeed.cerignola).map { it.title }.toSet()
        val allVisits = DemoGhostVisits.visitsByUser.values.flatten()
        assertTrue(
            "every ghost visit must reference a seeded POI",
            allVisits.all { it.poiTitle in titles }
        )
    }

    @Test
    fun tick_routesFollowStreetsWithDenseWaypoints() {
        // The routes come from real street geometry (see GhostRoutes.kt): the
        // waypoints must be dense enough that the drawn polyline follows the
        // roads instead of a few straight lines through buildings.
        val factory = GhostFactory()
        val ghosts = factory.tick(45.09, 7.66, 1_000L) +
            factory.tick(41.26, 15.90, 1_000L)
        assertTrue("expected ghosts from both demo zones", ghosts.isNotEmpty())
        ghosts.forEach { ghost ->
            assertTrue("path must have waypoints", ghost.path.size >= 2)
            val segments = ghost.path.zipWithNext().map { (a, b) ->
                haversineMeters(a.first, a.second, b.first, b.second)
            }
            val mean = segments.average()
            val max = segments.max()
            assertTrue(
                "mean segment ${mean}m is too sparse (straight lines through blocks)",
                mean < 100.0
            )
            assertTrue(
                "max segment ${max}m is too long (the longest legit straights are the SS16 to Ripalta and Corso Regio Parco)",
                max < 600.0
            )
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
