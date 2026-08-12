package com.example.progmob_progetto_melluso.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a community vote for a point of interest.
 */
@Entity(
    tableName = "poi_votes",
    foreignKeys = [
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["poiId", "voterUserId"], unique = true)]
)
data class PoiVoteEntity(
    // Local database identifier for the vote.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Point of interest that received the vote.
    val poiId: Long,
    // Firebase or app user identifier of the voter.
    val voterUserId: String,
    // Vote value, for example +1 or -1 depending on the app rules.
    val voteValue: Int,
    // Time of the vote stored in UTC.
    val votedAtUtcMillis: Long
)
