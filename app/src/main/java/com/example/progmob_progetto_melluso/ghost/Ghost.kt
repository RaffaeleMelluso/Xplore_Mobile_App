package com.example.progmob_progetto_melluso.ghost

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalTransport
import com.example.progmob_progetto_melluso.journal.JournalVisit

/**
 * A simulated traveler ("ghost") with everything the map needs to render them
 * and the journal-like detail shown when the marker is tapped.
 *
 * How it works: there is no backend yet (spec §5.3bis), so each ghost is fully
 * described here. The marker ([marker]) carries the ghost's current position
 * on the map; [path] is the ordered list of (lat, lon) waypoints of its whole
 * route, drawn as a polyline so the user sees a realistic journey. The online
 * ghosts move along [path] over time; past visitors sit on their route at a
 * [marker.timestamp] in the past. The travel detail ([name], [description],
 * [media], [visits]) mirrors the journal data model, so the detail view reuses
 * exactly the same composables as a real travel.
 *
 * Privacy (hiding the path / invisible mode) is intentionally not modelled yet.
 */
data class Ghost(
    val marker: GhostEntity,
    /** Ordered (lat, lon) waypoints of the ghost's route. */
    val path: List<Pair<Double, Double>>,
    /** Display name of the simulated user. */
    val name: String,
    /** Short description of their trip / profile. */
    val description: String,
    /** True if the ghost is "online now" moving along the route. */
    val isOnlineNow: Boolean,
    /** City/area the ghost trip takes place in (displayed in the detail). */
    val city: String,
    /** Journal-like data shown in the ghost detail card. */
    val media: List<JournalMedia> = emptyList(),
    val visits: List<JournalVisit> = emptyList(),
    val transports: List<JournalTransport> = emptyList()
)
