package com.example.progmob_progetto_melluso.travel

/**
 * Outcome of feeding one [LocationSample] to [TravelRepository.recordLocation].
 */
sealed interface LocationRecordResult {

    /** The sample was filtered out by the spatial/temporal thresholds and not stored. */
    data object Skipped : LocationRecordResult

    /**
     * The sample was stored as a path point.
     *
     * @property travelId Travel that owns the new path point.
     * @property openedNewTravel True when recording triggered a distance-based
     *   segmentation: the previous travel was closed and a new one opened.
     */
    data class Recorded(
        val travelId: Long,
        val openedNewTravel: Boolean
    ) : LocationRecordResult
}
