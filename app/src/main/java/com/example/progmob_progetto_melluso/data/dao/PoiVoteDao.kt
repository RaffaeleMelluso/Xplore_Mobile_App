package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * A POI joined with the votes it received since a given time (trend window).
 *
 * How it works: `pois.*` is embedded as the full POI row; the two extra
 * columns aggregate the votes that fell inside the requested window, so the
 * trends ranking can order POIs by real community votes.
 */
data class PoiTrendRow(
    @Embedded val poi: PoiEntity,
    // Sum of the vote values received inside the window.
    val windowScore: Long,
    // Number of votes received inside the window (all voters).
    val windowVotes: Long
)

/** A city joined with the votes its POIs received since a given time. */
data class CityTrendRow(
    // City name (the `city` column of the POIs).
    val cityName: String,
    // Sum of the vote values received by the city's POIs inside the window.
    val windowScore: Long,
    // Number of votes received inside the window.
    val windowVotes: Long
)

/**
 * Table `poi_votes`: a vote cast by a user on a POI.
 *
 * It links `pois` (FK `poiId`, CASCADE) to `user_profiles` (column
 * `voterUserId`). This is where the trends ranking is computed from: the
 * aggregating queries [poiTrends]/[cityTrends] join votes with POIs and group
 * them into POI rows / city aggregations.
 */
@Dao
interface PoiVoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun vote(vote: PoiVoteEntity): Long

    @Query("SELECT * FROM poi_votes WHERE poiId = :poiId ORDER BY votedAtUtcMillis DESC")
    fun votesForPoi(poiId: Long): Flow<List<PoiVoteEntity>>

    @Query("SELECT COALESCE(SUM(voteValue), 0) FROM poi_votes WHERE poiId = :poiId")
    fun voteScoreForPoi(poiId: Long): Flow<Long>

    /**
     * All the votes of the signed-in user, so the journal can highlight the
     * up/down state of every visited POI reactively.
     */
    @Query("SELECT * FROM poi_votes WHERE voterUserId = :voterId")
    fun myVotes(voterId: String): Flow<List<PoiVoteEntity>>

    /** The current vote of the user on a POI (null when never voted). */
    @Query("SELECT voteValue FROM poi_votes WHERE poiId = :poiId AND voterUserId = :voterId LIMIT 1")
    suspend fun myVoteOnce(poiId: Long, voterId: String): Int?

    /** One-shot score used to refresh the cached POI counter after voting. */
    @Query("SELECT COALESCE(SUM(voteValue), 0) FROM poi_votes WHERE poiId = :poiId")
    suspend fun voteScoreForPoiOnce(poiId: Long): Long

    /** Removes the user's vote (toggling the same vote off again). */
    @Query("DELETE FROM poi_votes WHERE poiId = :poiId AND voterUserId = :voterId")
    suspend fun removeVote(poiId: Long, voterId: String)

    /** Total votes stored, used to decide whether the demo seed is needed. */
    @Query("SELECT COUNT(*) FROM poi_votes")
    suspend fun count(): Long

    /**
     * POIs ranked by the votes they received since [startMillis] (no upper
     * bound: the window slides forward and new votes appear immediately).
     *
     * How it works: a LEFT JOIN keeps every POI, but the HAVING clause keeps
     * only the ones that actually received votes inside the window, so the
     * trends list shows what is trending in that period and nothing else.
     * Only POIs that received votes are included.
     */
    @Query(
        """
        SELECT pois.*, COALESCE(SUM(pv.voteValue), 0) AS windowScore, COUNT(pv.id) AS windowVotes
        FROM pois
        LEFT JOIN poi_votes pv ON pv.poiId = pois.id AND pv.votedAtUtcMillis >= :startMillis
        GROUP BY pois.id
        HAVING windowVotes > 0
        ORDER BY windowScore DESC, windowVotes DESC, pois.title ASC
        """
    )
    fun poiTrends(startMillis: Long): Flow<List<PoiTrendRow>>

    /**
     * Cities ranked by the votes their POIs received since [startMillis].
     *
     * How it works: groups the POIs by their `city` column and sums the votes
     * of every POI of the city inside the window; cities without votes in the
     * window are excluded (HAVING), same semantics as [poiTrends].
     */
    @Query(
        """
        SELECT pois.city AS cityName, COALESCE(SUM(pv.voteValue), 0) AS windowScore, COUNT(pv.id) AS windowVotes
        FROM pois
        LEFT JOIN poi_votes pv ON pv.poiId = pois.id AND pv.votedAtUtcMillis >= :startMillis
        WHERE pois.city IS NOT NULL AND pois.city != ''
        GROUP BY pois.city
        HAVING windowVotes > 0
        ORDER BY windowScore DESC, windowVotes DESC, pois.city ASC
        """
    )
    fun cityTrends(startMillis: Long): Flow<List<CityTrendRow>>
}
