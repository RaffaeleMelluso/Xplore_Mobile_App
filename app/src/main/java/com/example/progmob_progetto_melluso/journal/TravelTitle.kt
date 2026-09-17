package com.example.progmob_progetto_melluso.journal

import com.example.progmob_progetto_melluso.data.dao.TravelDao
import kotlinx.coroutines.flow.first

/**
 * Auto-title generator for trips: "Trip N" with the lowest positive number
 * not already used by an existing title on this device.
 *
 * Why the LOWEST free number and not "max + 1": titles are user-editable and
 * travels can be deleted, so the set of used numbers has holes ("Trip 1",
 * "Trip 3"). A max+1 scheme can even re-use the number of a still-running
 * trip when a later trip was already closed with a higher number — the
 * source of the "several trips all named the same" bug. Scanning the whole
 * set of used numbers instead guarantees the generated title is unique at
 * the moment it is written, whatever the trip order was.
 *
 * Pure and injectable (the titles come from the caller), so the collision
 * rule is unit-testable without Room.
 */
object TravelTitle {

    /** Prefix of every generated auto-title ("Trip 1", "Trip 2", ...). */
    const val AUTO_PREFIX = "Trip "

    /**
     * Returns the first free auto-title: "Trip 1" when nothing collides,
     * otherwise the lowest N whose "Trip N" is not among [usedTitles].
     * Titles chosen by the user (not matching the pattern) never collide.
     */
    fun nextAutoTitle(usedTitles: Collection<String?>): String {
        val used = usedTitles.filterNotNull()
            .mapNotNull { title -> autoTitleNumber(title) }
            .toSet()
        var n = 1
        while (n in used) n++
        return AUTO_PREFIX + n
    }

    /** suspend convenience: reads every stored title from [travelDao]. */
    suspend fun nextAutoTitle(travelDao: TravelDao): String =
        nextAutoTitle(travelDao.allTitlesOnce())

    /** Extracts N from a "Trip N" title; null for anything else. */
    private fun autoTitleNumber(title: String): Int? {
        if (!title.startsWith(AUTO_PREFIX)) return null
        val number = title.removePrefix(AUTO_PREFIX)
        return number.toIntOrNull()?.takeIf { it > 0 }
    }
}
