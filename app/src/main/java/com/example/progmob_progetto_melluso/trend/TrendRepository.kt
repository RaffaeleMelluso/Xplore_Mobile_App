package com.example.progmob_progetto_melluso.trend

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the trends page (spec §3.4 "recommendations
 * based on the aggregation of other travelers' data").
 *
 * How it works: the rankings are computed from the REAL community votes
 * ([com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity]),
 * aggregated per period (day/week/month/year) and per scope (single
 * attractions or cities). Because the aggregation runs as DAO flows, a new
 * vote anywhere in the app immediately re-ranks the page. Voting from the
 * trends page goes through the same shared engine as the journal and the map,
 * so a vote is the same vote everywhere.
 */
interface TrendRepository {

    /** Attractions (POIs) ranked by the votes received in the period. */
    fun observePoiTrends(period: TrendPeriod): Flow<List<PoiTrendItem>>

    /** Cities ranked by the votes their POIs received in the period. */
    fun observeCityTrends(period: TrendPeriod): Flow<List<CityTrendItem>>

    /** Votes a POI up/down with the shared rules (same as journal and map). */
    suspend fun votePoi(poiId: Long, voteValue: Int)

    /**
     * Idempotent demo seed for the trends page: inserts the demo POIs when the
     * table is empty and, when no vote exists yet, seeds the community votes
     * (same simulated travelers as the ghosts) spread over the last months, so
     * every period has content from the first launch.
     */
    suspend fun ensureTrendSeed()
}
