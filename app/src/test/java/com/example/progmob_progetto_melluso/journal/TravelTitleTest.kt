package com.example.progmob_progetto_melluso.journal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the auto-title generator (the trip-name bug fix): "Trip N" with
 * the LOWEST free number, so duplicates can never appear even when the used
 * numbers have holes (renamed/deleted trips) or trips were closed out of
 * order while an older one is still open.
 */
class TravelTitleTest {

    @Test
    fun nextAutoTitle_firstTripIsTrip1() {
        assertEquals("Trip 1", TravelTitle.nextAutoTitle(emptyList()))
    }

    @Test
    fun nextAutoTitle_skipsUsedNumbers() {
        assertEquals(
            "Trip 2",
            TravelTitle.nextAutoTitle(listOf("Trip 1"))
        )
        assertEquals(
            "Trip 3",
            TravelTitle.nextAutoTitle(listOf("Trip 1", "Trip 2"))
        )
    }

    @Test
    fun nextAutoTitle_fillsHolesLeftByRenamedOrDeletedTrips() {
        // "Trip 2" was renamed by the user: the number is free again.
        assertEquals(
            "Trip 2",
            TravelTitle.nextAutoTitle(listOf("Trip 1", "My vacation", "Trip 3"))
        )
    }

    @Test
    fun nextAutoTitle_ignoresUserTitlesThatOnlyLookSimilar() {
        // A user title like "Trips" or "trip 4" is NOT a generated title.
        assertEquals(
            "Trip 1",
            TravelTitle.nextAutoTitle(listOf("Trips", "trip 4", "Trip to Rome"))
        )
    }

    @Test
    fun nextAutoTitle_nullTitlesAreIgnored() {
        assertEquals(
            "Trip 1",
            TravelTitle.nextAutoTitle(listOf(null, null))
        )
    }

    @Test
    fun nextAutoTitle_zeroAndNegativeNumbersDoNotCollide() {
        // Only positive numbers are generated: "Trip 0" or "Trip -1" (a user
        // title) never block the generator.
        assertEquals(
            "Trip 1",
            TravelTitle.nextAutoTitle(listOf("Trip 0", "Trip -1"))
        )
    }
}
