package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.core.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostSimulatorTest {

    @Test
    fun tick_generatesExpectedNumberOfGhosts() {
        val simulator = GhostSimulator(ghostCount = 3)
        val ghosts = simulator.tick(45.0, 7.0, 1_000L)
        assertEquals(3, ghosts.size)
    }

    @Test
    fun tick_isDeterministic() {
        val simulator = GhostSimulator(ghostCount = 3)
        val first = simulator.tick(45.0, 7.0, 1_000L)
        val second = simulator.tick(45.0, 7.0, 1_000L)
        assertEquals(first, second)
    }

    @Test
    fun tick_positionsStayWithinRadius() {
        val radius = 1_500.0
        val simulator = GhostSimulator(ghostCount = 4, radiusMeters = radius)
        val ghosts = simulator.tick(45.0, 7.0, 5_000L)
        ghosts.forEach { ghost ->
            val distance = GeoUtils.haversineMeters(45.0, 7.0, ghost.lat, ghost.lon)
            // Small tolerance for the haversine approximation.
            assertTrue("ghost at $distance m from center", distance <= radius + 50)
        }
    }

    @Test
    fun tick_hasStableUserIds() {
        val simulator = GhostSimulator(ghostCount = 3)
        val ids = simulator.tick(45.0, 7.0, 1_000L).map { it.userId }
        assertEquals(setOf("ghost-0", "ghost-1", "ghost-2"), ids.toSet())
    }

    @Test
    fun tick_positionsMoveOverTime() {
        val simulator = GhostSimulator(ghostCount = 1)
        val atStart = simulator.tick(45.0, 7.0, 0L)
        val later = simulator.tick(45.0, 7.0, 10_000L)
        assertNotEquals(atStart[0].lat, later[0].lat)
    }
}
