package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Aggregate read queries over several tables, used to show the user's
 * overall statistics on the Profile screen.
 *
 * How it works: each query reads a single number (or the raw timestamps needed
 * to bucket the activity days in the ViewModel) across all travels, exposed as
 * a [Flow] so the Profile screen re-renders as new data is recorded.
 * The `step_*` tables are still unused by the pedometer, so the step/distance
 * totals are 0 until that feature lands. Every query is scoped to ONE user
 * (joined through `travels.userId`), so the statistics follow the account:
 * switching user empties the tiles.
 */
@Dao
interface StatsDao {
    // Total steps of the user's trips (0 until the pedometer is implemented).
    @Query(
        "SELECT COALESCE(SUM(s.steps), 0) FROM step_daily_summaries s " +
            "JOIN travels t ON t.id = s.travelId WHERE t.userId = :userId"
    )
    fun totalSteps(userId: String): Flow<Long>

    // Total distance in meters of the user's trips (0 until the pedometer).
    @Query(
        "SELECT COALESCE(SUM(s.distanceMeters), 0) FROM step_daily_summaries s " +
            "JOIN travels t ON t.id = s.travelId WHERE t.userId = :userId"
    )
    fun totalDistanceMeters(userId: String): Flow<Double>

    // Raw UTC timestamps of the user's GPS points, so the ViewModel can bucket
    // them into distinct local dates ("activity days").
    @Query(
        "SELECT p.recordedAtUtcMillis FROM travel_path_points p " +
            "JOIN travels t ON t.id = p.travelId WHERE t.userId = :userId"
    )
    fun pointTimestamps(userId: String): Flow<List<Long>>

    /** Number of the user's travels in the journal. */
    @Query("SELECT COUNT(*) FROM travels WHERE userId = :userId")
    fun travelsCount(userId: String): Flow<Int>

    /** Number of distinct POIs the user visited across all their trips. */
    @Query(
        "SELECT COUNT(DISTINCT v.poiId) FROM travel_poi_visits v " +
            "JOIN travels t ON t.id = v.travelId WHERE t.userId = :userId"
    )
    fun poisVisited(userId: String): Flow<Int>

    /** Number of destinations (cities) visited by the user. */
    @Query(
        "SELECT COUNT(*) FROM travel_cities c " +
            "JOIN travels t ON t.id = c.travelId WHERE t.userId = :userId"
    )
    fun citiesVisited(userId: String): Flow<Int>
}