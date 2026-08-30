package com.example.progmob_progetto_melluso.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Table `user_profiles`: one row per signed-in user, keyed by
 * `firebaseUserId`.
 *
 * It is the owning side that other tables point to: `pois.createdByUserId`
 * (who added the POI) and `poi_votes.voterUserId` (who voted).
 */
@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity): Long

    @Query("SELECT * FROM user_profiles WHERE firebaseUserId = :firebaseUserId LIMIT 1")
    fun profileByFirebaseUserId(firebaseUserId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE firebaseUserId = :firebaseUserId LIMIT 1")
    suspend fun profileByFirebaseUserIdOnce(firebaseUserId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY createdAtUtcMillis DESC")
    fun allProfiles(): Flow<List<UserProfileEntity>>
}
