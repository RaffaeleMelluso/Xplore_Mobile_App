package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the points of interest catalog.
 *
 * Why it exists (spec §3.3 "POI reporting"): the user must be able to insert
 * categorized, described points of interest that immediately join the same
 * pool the map, the ghosts and the trends page read from. Storing a POI here
 * is enough for the sync: every screen observes the same Room table through
 * reactive DAO flows, so a newly added POI appears everywhere without any
 * extra wiring.
 */
interface PoiRepository {

    /** Emits the whole POI catalog reactively (map markers, cards, trends). */
    fun observePois(): Flow<List<PoiEntity>>

    /** Emits only the POIs created by the signed-in user ("my POIs" screen). */
    fun observeMyPois(): Flow<List<PoiEntity>>

    /**
     * Inserts a user-created POI and returns its generated id.
     *
     * The signed-in user is stamped as the owner ([PoiEntity.createdByUserId])
     * unless the caller set one, so the "my POIs" screen and the removal rule
     * (only your own POIs) work. Demo note: no validation/moderation is
     * applied yet (per the project request), the row is stored as provided.
     * A new POI always starts with a zero vote counter; its score grows with
     * the real votes.
     */
    suspend fun addPoi(poi: PoiEntity): Long

    /**
     * Deletes a POI created by the signed-in user. The POI's votes go with it
     * (FK cascade). Returns false (and deletes nothing) when the POI does not
     * exist or belongs to somebody else: you can remove only YOUR POIs.
     */
    suspend fun removeMyPoi(poiId: Long): Boolean
}
