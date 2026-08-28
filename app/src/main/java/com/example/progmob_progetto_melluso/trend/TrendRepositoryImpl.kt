package com.example.progmob_progetto_melluso.trend

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TrendRepository].
 *
 * How it works: the rankings come straight from the votes table through the
 * [PoiVoteDao] aggregation queries, so they are always in sync with what the
 * community actually voted (real votes, not a cached snapshot). The signed-in
 * user's own vote is attached from the shared [PoiVoteRepository] so the rows
 * show the same up/down state as the journal and the map; voting delegates to
 * that same repository, keeping one single vote rule across the app.
 *
 * @param poiVoteDao DAO for the votes (aggregation queries).
 * @param poiDao DAO for the POIs (demo seed + cached score refresh).
 * @param poiVoteRepository the shared vote engine (my votes + votePoi).
 * @param seedPois demo attractions inserted on first launch (same as the map).
 * @param clock provides "now" in UTC millis; injectable for deterministic tests.
 */
class TrendRepositoryImpl(
    private val poiVoteDao: PoiVoteDao,
    private val poiDao: PoiDao,
    private val poiVoteRepository: PoiVoteRepository,
    private val seedPois: List<PoiEntity> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis
) : TrendRepository {

    override fun observePoiTrends(period: TrendPeriod): Flow<List<PoiTrendItem>> =
        combine(
            poiVoteDao.poiTrends(period.startUtcMillis(clock())),
            poiVoteRepository.observeMyVotes()
        ) { rows, myVotes ->
            val myVoteByPoi = myVotes.associateBy { it.poiId }
            rows.map { row ->
                PoiTrendItem(
                    poiId = row.poi.id,
                    title = row.poi.title,
                    category = row.poi.category,
                    city = row.poi.city,
                    description = row.poi.description,
                    windowScore = row.windowScore,
                    windowVotes = row.windowVotes,
                    myVote = myVoteByPoi[row.poi.id]?.voteValue ?: 0
                )
            }
        }

    override fun observeCityTrends(period: TrendPeriod): Flow<List<CityTrendItem>> =
        poiVoteDao.cityTrends(period.startUtcMillis(clock())).map { rows ->
            rows.map { row -> CityTrendItem(row.cityName, row.windowScore, row.windowVotes) }
        }

    override suspend fun votePoi(poiId: Long, voteValue: Int) =
        poiVoteRepository.votePoi(poiId, voteValue)

    override suspend fun ensureTrendSeed() {
        // The trends page must work even when the Map tab was never opened:
        // insert the demo POIs when the table is empty.
        if (poiDao.count() == 0 && seedPois.isNotEmpty()) {
            seedPois.forEach { poiDao.insert(it) }
        }
        // The community votes come from the SAME data the ghosts show in their
        // cards (shared seed, idempotent): trends and ghosts always agree.
        poiVoteRepository.ensureDemoVotes()
    }
}
