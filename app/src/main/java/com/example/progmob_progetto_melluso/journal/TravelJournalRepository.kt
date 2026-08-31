package com.example.progmob_progetto_melluso.journal

import kotlinx.coroutines.flow.Flow

/** A travel as shown in the journal list. */
data class JournalTravel(
    val travelId: Long,
    val title: String?,
    val startUtcMillis: Long,
    val endUtcMillis: Long?,
    val description: String?
)

/** A destination (region/city) detected inside a travel. */
data class JournalCity(
    val cityId: Long,
    val travelId: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val arrivedAtUtcMillis: Long
)

/** A single point of the recorded path, used to draw the route preview. */
data class JournalPathPoint(
    val lat: Double,
    val lon: Double
)

/** A photo or video attached to the journal. */
data class JournalMedia(
    val id: Long,
    val mediaType: String,
    val imageUri: String,
    val caption: String?,
    val capturedAtUtcMillis: Long
)

/** A recorded POI visit joined with the visited POI title and its votes. */
data class JournalVisit(
    val id: Long,
    val poiId: Long,
    val poiTitle: String?,
    val visitedAtUtcMillis: Long,
    val note: String?,
    // Cached community score of the POI (sum of all votes).
    val score: Int = 0,
    // The signed-in user's vote on this POI: 1 (up), -1 (down) or 0 (none).
    val myVote: Int = 0
)

/** The full editable state of a single travel in the journal. */
data class TravelJournal(
    val travel: JournalTravel,
    val media: List<JournalMedia>,
    val cities: List<JournalCity>,
    val visits: List<JournalVisit>,
    // The recorded path, drawn as a small route preview in the detail.
    val pathPoints: List<JournalPathPoint> = emptyList(),
    // The transport segments reported during the trip (spec §3.1).
    val transports: List<JournalTransport> = emptyList(),
    // Total steps counted by the pedometer during the trip (0 until the
    // sensor records; the trip may predate the feature).
    val steps: Long = 0,
    // Total distance in meters estimated from the steps during the trip.
    val distanceMeters: Double = 0.0
)

/**
 * Single source of truth for the travel journal ("libretto").
 *
 * Responsibilities (spec §7 + checklist §2/§4):
 * - expose the list of travels and the mutable detail of one travel as flows;
 * - start/stop the active travel (delegating to [com.example.progmob_progetto_melluso.travel.TravelRepository]);
 * - edit a travel after the trip (title + description "what I saw");
 * - attach photos/videos and remove them;
 * - detect the destinations (regions/cities) visited in a travel from its path
 *   points and let the user rename each one (the map/journal region filter);
 * - detect the POI visits from the recorded path (automatic, geofencing) and
 *   let the user remove a visit without deleting the POI;
 * - vote a POI up or down (one vote per user per POI, toggleable).
 */
interface TravelJournalRepository {

    /** Emits all travels (journal list), newest first. */
    fun observeJournal(): Flow<List<JournalTravel>>

    /** Emits the mutable detail of one travel, or null when it does not exist. */
    fun observeTravelDetails(travelId: Long): Flow<TravelJournal?>

    /**
     * Live total steps of one travel from the pedometer (0 before the feature
     * or without a step sensor). Used by the journal list to show the ongoing
     * trip's stats without opening the detail.
     */
    fun observeTravelSteps(travelId: Long): Flow<Long>

    /** Live total distance in meters of one travel (see [observeTravelSteps]). */
    fun observeTravelDistance(travelId: Long): Flow<Double>

    /** Starts a new travel (single active travel). @return the active travel id. */
    suspend fun startTravel(): Long

    /** Stops the active travel. @return true if a travel was closed. */
    suspend fun stopTravel(): Boolean

    /** Updates the title/description of a travel after the trip. */
    suspend fun updateTravelDetails(travelId: Long, title: String?, description: String?)

    /** Attaches a photo or video (mediaType: "photo"/"video") to a travel. */
    suspend fun addMedia(travelId: Long, uri: String, mediaType: String)

    /**
     * Reports a transport segment for a travel (simple flow: mode + optional
     * line/route number, stamped with the current time).
     */
    suspend fun addTransport(travelId: Long, mode: TransportMode, lineName: String?)

    /** Removes a reported transport segment from the journal. */
    suspend fun removeTransport(id: Long)

    /** Removes a photo/video from the journal (the underlying file is kept). */
    suspend fun removeMedia(id: Long)

    /** Renames a detected destination (region/city). */
    suspend fun renameCity(cityId: Long, name: String)

    /**
     * Detects the destinations visited in a travel from its path points and
     * persists them. Existing cities are kept (they may have been renamed);
     * newly found destinations are inserted. Safe to call on any open of the
     * travel detail or when the map filter needs the region list.
     */
    suspend fun detectCities(travelId: Long)

    /**
     * Backfills the POI visits of a travel from its path points (idempotent:
     * existing visits are kept). The live recording already detects visits, so
     * this is a safety net for travels recorded before the feature or closed
     * while the app was off.
     */
    suspend fun detectVisits(travelId: Long)

    /** Removes a recorded POI visit from the journal. */
    suspend fun removeVisit(visitId: Long)

    /**
     * Votes a POI up (+1) or down (-1). One vote per user per POI: voting the
     * same direction again removes the vote, the opposite direction replaces
     * it. The POI cached score is refreshed right after.
     */
    suspend fun votePoi(poiId: Long, voteValue: Int)

    /**
     * Deletes a travel and everything attached to it (path, photos, cities,
     * visits). If the travel is still active it is closed first, so tracking
     * does not keep appending to a deleted row.
     */
    suspend fun deleteTravel(travelId: Long)
}