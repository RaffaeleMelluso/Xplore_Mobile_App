package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.travel.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for everything the map screen renders.
 *
 * Responsibilities:
 * - expose the live user position coming from the sensor layer;
 * - expose the ordered path of the active travel (from [TravelRepository]);
 * - expose the points of interest cached in Room;
 * - expose the simulated ghosts (spec §6) anchored to the user position;
 * - seed the POI table on first launch.
 */
interface MapRepository {

    /**
     * Emits the continuous stream of location fixes, starting with the latest
     * one. Emits `null` immediately if no fix has ever been received (for
     * example when the app started with location services off), so the map
     * state does not stay stuck in "loading" waiting for the first fix.
     */
    fun observeCurrentLocation(): Flow<LocationSample?>

    /** Emits all points of interest, newest first. */
    fun observePois(): Flow<List<PoiEntity>>

    /** Emits the ordered path points of the active travel (empty when no travel). */
    fun observeActivePath(): Flow<List<TravelPathPointEntity>>

    /** Emits the ghosts that move around the current user position. */
    fun observeGhosts(): Flow<List<GhostEntity>>

    /**
     * Seeds the POI table with the demo attractions, only when it is empty.
     *
     * How it works: reads the POI count and, if zero, inserts the demo set.
     * Idempotent by construction, so it is safe to call on every launch.
     */
    suspend fun ensurePoiSeed()
}
