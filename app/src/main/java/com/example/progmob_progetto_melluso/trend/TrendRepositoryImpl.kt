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
 * show the same up/down state as the journal and the map. The page is
 * read-only: voting happens only where the visit is real (map POI card,
 * journal visits) through that same shared engine.
 *
 * @param poiVoteDao DAO for the votes (aggregation queries).
 * @param poiDao DAO for the POIs (demo seed + cached score refresh).
 * @param poiVoteRepository the shared vote engine (my votes, read-only here).
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
                    imageResName = row.poi.imageResName,
                    imageUri = row.poi.imageUri,
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

    override fun observePoiStats(poiId: Long): Flow<PoiStats> =
        combine(
            poiVoteDao.voteScoreForPoi(poiId),
            poiVoteDao.votesForPoi(poiId)
        ) { score, votes -> PoiStats(totalScore = score, totalVotes = votes.size) }

    override suspend fun ensureTrendSeed() {
        // The trends page must work even when the Map tab was never opened:
        // insert the demo POIs that are missing (idempotent per title, the
        // SAME seed the map uses, so both screens always agree).
        seedPois.forEach { poi ->
            if (poiDao.countByTitle(poi.title) == 0) {
                poiDao.insert(poi)
            }
        }
        // The community votes come from the SAME data the ghosts show in their
        // cards plus the demo city spread (shared seed, idempotent per vote):
        // trends and ghosts always agree.
        poiVoteRepository.ensureDemoVotes()
    }
}
