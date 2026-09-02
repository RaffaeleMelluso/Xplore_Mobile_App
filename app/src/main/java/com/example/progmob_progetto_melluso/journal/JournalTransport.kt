package com.example.progmob_progetto_melluso.journal

/**
 * The transport modes the user can report during a trip (the transport-reporting requirement: the
 * travel journal documents the transport used).
 *
 * Kept intentionally simple: the "simple reporting" flow lets the user pick a
 * mode and (for the vehicles with a line) an optional line/route number.
 */
enum class TransportMode(val storageName: String) {
    /** Moving on foot. */
    WALK("walk"),
    /** Bus (with an optional line number). */
    BUS("bus"),
    /** Tram (with an optional line number). */
    TRAM("tram"),
    /** Metro / underground. */
    METRO("metro"),
    /** Train (with an optional line or route name). */
    TRAIN("train"),
    /** Ferry / boat. */
    FERRY("ferry");

    companion object {
        /** Maps the persisted type string back to the mode (unknown -> walk). */
        fun fromStorage(name: String?): TransportMode =
            entries.firstOrNull { it.storageName == name } ?: WALK
    }
}

/**
 * One transport segment reported for a travel, as shown in the journal detail.
 *
 * @param id local database id of the segment.
 * @param travelId the travel this segment belongs to.
 * @param mode the kind of transport used.
 * @param lineName optional line/route number (e.g. bus "4", tram "15").
 * @param startUtcMillis when the segment was reported (UTC).
 */
data class JournalTransport(
    val id: Long,
    val travelId: Long,
    val mode: TransportMode,
    val lineName: String?,
    val startUtcMillis: Long
)
