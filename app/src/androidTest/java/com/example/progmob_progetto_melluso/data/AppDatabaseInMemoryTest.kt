package com.example.progmob_progetto_melluso.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.progmob_progetto_melluso.data.database.AppDatabase
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelMediaEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationTimeSpanEntity
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the real Room schema.
 *
 * These tests intentionally use an in-memory database and no repository or
 * remote data source: each test exercises the generated DAO implementation,
 * SQLite constraints, cascades, and Flow invalidation behavior directly.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseInMemoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Verifies the basic write/read path for the travel DAO.
     *
     * A travel belonging to `user-1` is inserted into the in-memory database
     * and then read through the DAO's user-scoped Flow. The assertions verify
     * that Room generated a valid identifier, that exactly one row is exposed,
     * and that the user ownership value is persisted without being changed.
     * Reading the first Flow emission also verifies that the observable query
     * is immediately populated with the inserted data.
     */
    @Test
    fun travelFlow_emitsInsertedTravel() = runBlocking {
        val travelId = database.travelDao().insert(travel())

        // The DAO query must return the row through its reactive Flow.
        val stored = database.travelDao().allTravelsForUser("user-1").first()

        assertEquals(1, stored.size)
        assertEquals(travelId, stored.single().id)
        assertEquals("user-1", stored.single().userId)
    }

    /**
     * Verifies idempotent POI visit insertion for one travel.
     *
     * The schema has a unique index on the pair `(travelId, poiId)`, because a
     * travel should record at most one visit to the same POI. The first insert
     * must succeed, while a second insert for the same pair must be ignored by
     * the DAO conflict strategy. The test checks both the returned insert
     * values and the final table contents, including preservation of the first
     * visit timestamp.
     */
    @Test
    fun visitUniqueConstraint_ignoresDuplicateVisitForSameTravelAndPoi() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val poiId = database.poiDao().insert(poi())
        val firstId = database.travelPoiVisitDao().insert(visit(travelId, poiId, 100L))
        val duplicateId = database.travelPoiVisitDao().insert(visit(travelId, poiId, 200L))

        // A duplicate must not create a second row or overwrite the original.
        val visits = database.travelPoiVisitDao().visitsForTravel(travelId).first()

        assertNotEquals(0L, firstId)
        assertEquals(-1L, duplicateId)
        assertEquals(1, visits.size)
        assertEquals(100L, visits.single().visitedAtUtcMillis)
    }

    /**
     * Verifies referential integrity when a travel is deleted.
     *
     * The test creates one travel and one independent POI, then inserts every
     * travel-owned child currently represented in the Room schema: path points,
     * POI visits, media, transport segments, detected cities, daily step
     * summaries, and the step checkpoint. Deleting the parent travel must
     * cascade to all those child rows because their foreign keys use
     * `ON DELETE CASCADE`. The POI itself is not owned by the travel and must
     * therefore remain in the database.
     */
    @Test
    fun deletingTravel_cascadesToAllTravelChildren_butKeepsPoi() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val poiId = database.poiDao().insert(poi())

        database.travelPathPointDao().insert(
            TravelPathPointEntity(
                travelId = travelId,
                latitude = 45.07,
                longitude = 7.69,
                recordedAtUtcMillis = 100L
            )
        )
        database.travelPoiVisitDao().insert(visit(travelId, poiId, 100L))
        database.travelMediaDao().insert(
            TravelMediaEntity(
                travelId = travelId,
                imageUri = "file:///private/photo.jpg",
                caption = "A day out",
                capturedAtUtcMillis = 100L,
                createdByUserId = "user-1"
            )
        )
        database.travelTransportDao().insert(
            TravelTransportEntity(
                travelId = travelId,
                transportType = "tram",
                lineName = "4",
                departureName = "Porta Nuova",
                arrivalName = "Castello",
                startUtcMillis = 100L,
                endUtcMillis = 200L
            )
        )
        database.travelCityDao().insert(
            TravelCityEntity(
                travelId = travelId,
                name = "Turin",
                lat = 45.07,
                lon = 7.69,
                arrivedAtUtcMillis = 100L
            )
        )
        database.stepDailySummaryDao().upsert(
            StepDailySummaryEntity(
                travelId = travelId,
                localDate = "2026-09-01",
                timeZoneId = "Europe/Rome",
                steps = 1200L,
                distanceMeters = 850.0,
                lastSensorValue = 1200L,
                updatedAtUtcMillis = 100L
            )
        )
        database.stepCheckpointDao().upsert(
            StepCheckpointEntity(
                travelId = travelId,
                lastSensorValue = 1200L,
                lastCheckpointUtcMillis = 100L,
                lastKnownLocalDate = "2026-09-01",
                lastKnownTimeZoneId = "Europe/Rome"
            )
        )

        // Delete only the parent; Room/SQLite must remove its child rows.
        database.travelDao().deleteById(travelId)

        assertEquals(emptyList<TravelPathPointEntity>(), database.travelPathPointDao().pointsForTravel(travelId).first())
        assertEquals(emptyList<TravelPoiVisitEntity>(), database.travelPoiVisitDao().visitsForTravel(travelId).first())
        assertEquals(emptyList<TravelMediaEntity>(), database.travelMediaDao().mediaForTravel(travelId).first())
        assertEquals(emptyList<TravelTransportEntity>(), database.travelTransportDao().transportsForTravel(travelId).first())
        assertEquals(emptyList<TravelCityEntity>(), database.travelCityDao().citiesForTravel(travelId).first())
        assertEquals(emptyList<StepDailySummaryEntity>(), database.stepDailySummaryDao().summariesForTravel(travelId).first())
        assertNull(database.stepCheckpointDao().checkpointForTravel(travelId).first())
        // The POI is a separate aggregate and must not be deleted with a travel.
        assertNotNull(database.poiDao().byIdOnce(poiId))
    }

    /**
     * Verifies that profile statistics are isolated by Firebase user.
     *
     * Two users receive travels together with path points, daily steps, a
     * city, and visits to the same POI. Every StatsDao query must
     * count only rows reached through travels owned by the requested user:
     * totals, timestamps, travel count, distinct visited POIs, and cities.
     * This protects the privacy requirement that changing account must never
     * expose another user's journal or statistics.
     */
    @Test
    fun statsQueries_areScopedToTheRequestedUser() = runBlocking {
        val firstTravelId = database.travelDao().insert(travel(userId = "user-1", startUtcMillis = 1L))
        val repeatedVisitTravelId = database.travelDao().insert(travel(userId = "user-1", startUtcMillis = 2L))
        val secondTravelId = database.travelDao().insert(travel(userId = "user-2", startUtcMillis = 3L))
        val poiId = database.poiDao().insert(poi())

        database.travelPathPointDao().insert(pathPoint(firstTravelId, 101L))
        database.travelPathPointDao().insert(pathPoint(repeatedVisitTravelId, 102L))
        database.travelPathPointDao().insert(pathPoint(secondTravelId, 202L))
        database.travelPoiVisitDao().insert(visit(firstTravelId, poiId, 100L))
        database.travelPoiVisitDao().insert(visit(repeatedVisitTravelId, poiId, 150L))
        database.travelPoiVisitDao().insert(visit(secondTravelId, poiId, 200L))
        database.travelCityDao().insert(city(firstTravelId, "Turin", 100L))
        database.travelCityDao().insert(city(secondTravelId, "Paris", 200L))
        database.stepDailySummaryDao().upsert(summary(firstTravelId, "2026-09-01", 10L, 25.5))
        database.stepDailySummaryDao().upsert(summary(secondTravelId, "2026-09-02", 20L, 50.5))

        val stats = database.statsDao()

        assertEquals(10L, stats.totalSteps("user-1").first())
        assertEquals(25.5, stats.totalDistanceMeters("user-1").first(), 0.001)
        // The DAO query has no ORDER BY, so verify membership rather than order.
        assertEquals(setOf(101L, 102L), stats.pointTimestamps("user-1").first().toSet())
        assertEquals(2, stats.travelsCount("user-1").first())
        // The same POI was visited in two travels, but counts only once.
        assertEquals(1, stats.poisVisited("user-1").first())
        assertEquals(1, stats.citiesVisited("user-1").first())
        assertEquals(0L, stats.totalSteps("unknown-user").first())
        assertEquals(0, stats.travelsCount("unknown-user").first())
    }

    /**
     * Verifies the migration-compatibility behavior of legacy travel rows.
     *
     * Rows created before per-user isolation have a null `userId`. Calling
     * `claimLegacyTravels` must assign only those rows to the first user and
     * must not steal rows already assigned to another account.
     */
    @Test
    fun claimLegacyTravels_assignsOnlyUnownedRows() = runBlocking {
        val legacyId = database.travelDao().insert(travel(userId = null, startUtcMillis = 1L))
        val ownedId = database.travelDao().insert(travel(userId = "user-2", startUtcMillis = 2L))

        database.travelDao().claimLegacyTravels("user-1")

        assertEquals("user-1", database.travelDao().travelByIdOnce(legacyId)?.userId)
        assertEquals("user-2", database.travelDao().travelByIdOnce(ownedId)?.userId)
        assertEquals(1, database.travelDao().allTravelsForUser("user-1").first().size)
        assertEquals(1, database.travelDao().allTravelsForUser("user-2").first().size)
    }

    /**
     * Verifies replacement semantics for the daily step bucket.
     *
     * The unique key `(travelId, localDate, timeZoneId)` identifies one day
     * inside one travel. Writing a newer summary for that same key must replace
     * the previous values rather than create a second row, while a different
     * local date must remain a separate bucket. The aggregate DAO queries must
     * then sum the resulting rows correctly.
     */
    @Test
    fun stepDailySummary_upsertReplacesSameDayAndKeepsOtherDays() = runBlocking {
        val travelId = database.travelDao().insert(travel())

        database.stepDailySummaryDao().upsert(summary(travelId, "2026-09-01", 10L, 25.0))
        database.stepDailySummaryDao().upsert(summary(travelId, "2026-09-01", 30L, 75.0))
        database.stepDailySummaryDao().upsert(summary(travelId, "2026-09-02", 20L, 50.0))

        val summaries = database.stepDailySummaryDao().summariesForTravel(travelId).first()

        assertEquals(2, summaries.size)
        assertEquals(30L, summaries.first().steps)
        assertEquals(75.0, summaries.first().distanceMeters, 0.001)
        // The aggregate includes both retained days: 75.0 + 50.0 meters.
        assertEquals(125.0, database.stepDailySummaryDao().totalDistanceForTravel(travelId).first(), 0.001)
        assertEquals(50L, database.stepDailySummaryDao().totalStepsForTravel(travelId).first())
    }

    /**
     * Verifies the journal projection that joins visits with POI titles.
     *
     * The visit table stores only foreign keys, while the journal UI needs the
     * human-readable POI title and optional note. The query must perform the
     * join and return visits in reverse chronological order.
     */
    @Test
    fun visitViewsForTravel_joinsPoiTitlesAndOrdersNewestFirst() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val olderPoiId = database.poiDao().insert(poi(title = "Mole"))
        val newerPoiId = database.poiDao().insert(poi(title = "Louvre", city = "Paris"))

        database.travelPoiVisitDao().insert(
            TravelPoiVisitEntity(
                travelId = travelId,
                poiId = olderPoiId,
                visitedAtUtcMillis = 100L,
                note = "Morning"
            )
        )
        database.travelPoiVisitDao().insert(
            TravelPoiVisitEntity(
                travelId = travelId,
                poiId = newerPoiId,
                visitedAtUtcMillis = 200L,
                note = null
            )
        )

        val views = database.travelPoiVisitDao().visitViewsForTravel(travelId).first()

        assertEquals(listOf("Louvre", "Mole"), views.map { it.poiTitle })
        assertEquals(listOf(200L, 100L), views.map { it.visitedAtUtcMillis })
        assertEquals("Morning", views.last().note)
    }

    /**
     * Verifies POI referential integrity.
     *
     * Votes and travel visits reference a POI with `ON DELETE CASCADE`. Once
     * the POI is removed, both dependent rows must disappear, while the
     * parent travel remains intact. This prevents orphaned community votes or
     * broken journal references.
     */
    @Test
    fun deletingPoi_cascadesToVotesAndVisits_butKeepsTravel() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val poiId = database.poiDao().insert(poi())
        database.poiVoteDao().vote(
            PoiVoteEntity(
                poiId = poiId,
                voterUserId = "voter-1",
                voteValue = 1,
                votedAtUtcMillis = 100L
            )
        )
        database.travelPoiVisitDao().insert(visit(travelId, poiId, 100L))

        database.poiDao().delete(poiId)

        assertNull(database.poiDao().byIdOnce(poiId))
        assertEquals(0L, database.poiVoteDao().voteScoreForPoiOnce(poiId))
        assertEquals(emptyList<TravelPoiVisitEntity>(), database.travelPoiVisitDao().visitsForTravel(travelId).first())
        assertNotNull(database.travelDao().travelByIdOnce(travelId))
    }

    /**
     * Verifies the user-profile upsert contract.
     *
     * Firebase UID is unique, so saving a profile again for the same UID must
     * update the existing logical profile instead of exposing duplicates in
     * the profile list. A different UID must still produce a separate row.
     */
    @Test
    fun userProfileUpsert_replacesByFirebaseUserId() = runBlocking {
        database.userProfileDao().upsert(profile("user-1", "First name", 1L))
        database.userProfileDao().upsert(profile("user-1", "Updated name", 2L))
        database.userProfileDao().upsert(profile("user-2", "Second user", 3L))

        val profiles = database.userProfileDao().allProfiles().first()

        assertEquals(2, profiles.size)
        assertEquals("Updated name", database.userProfileDao().profileByFirebaseUserIdOnce("user-1")?.displayName)
        assertEquals("Second user", database.userProfileDao().profileByFirebaseUserIdOnce("user-2")?.displayName)
    }

    /**
     * Verifies user-scoped travel ordering.
     *
     * The journal query must exclude another user's travel and sort the
     * remaining rows from newest to oldest by `startUtcMillis`. This is a
     * database-level check of the account isolation and ordering contract used
     * by the travel list UI.
     */
    @Test
    fun travelsForUser_areFilteredAndOrderedNewestFirst() = runBlocking {
        database.travelDao().insert(travel(userId = "user-1", startUtcMillis = 100L))
        database.travelDao().insert(travel(userId = "user-1", startUtcMillis = 300L))
        database.travelDao().insert(travel(userId = "user-2", startUtcMillis = 500L))

        val travels = database.travelDao().allTravelsForUser("user-1").first()

        assertEquals(2, travels.size)
        assertEquals(300L, travels[0].startUtcMillis)
        assertEquals(100L, travels[1].startUtcMillis)
        assertEquals(listOf("user-1", "user-1"), travels.map { it.userId })
    }

    /**
     * Verifies that a user's vote is replaced rather than duplicated.
     *
     * The `(poiId, voterUserId)` unique index models one current vote per user
     * and POI. Inserting a new direction for the same pair must update the
     * stored vote and therefore change the score from +1 to -1 while keeping
     * one database row. This validates the real SQLite behavior behind the
     * repository's vote replacement operation.
     */
    @Test
    fun poiVote_upsertReplacesTheSameUsersVote() = runBlocking {
        val poiId = database.poiDao().insert(poi())
        database.poiVoteDao().vote(
            PoiVoteEntity(poiId = poiId, voterUserId = "user-1", voteValue = 1, votedAtUtcMillis = 100L)
        )
        database.poiDao().updateVotes(poiId, 1)
        assertEquals(1, database.poiDao().byIdOnce(poiId)?.votes)

        database.poiVoteDao().vote(
            PoiVoteEntity(poiId = poiId, voterUserId = "user-1", voteValue = -1, votedAtUtcMillis = 200L)
        )

        val votes = database.poiVoteDao().votesForPoi(poiId).first()

        assertEquals(1, votes.size)
        assertEquals(-1, votes.single().voteValue)
        assertEquals(200L, votes.single().votedAtUtcMillis)
        assertEquals(-1L, database.poiVoteDao().voteScoreForPoiOnce(poiId))
    }

    /**
     * Verifies chronological ordering of persisted GPS points.
     *
     * Points are intentionally inserted out of order because a background
     * location callback can complete after another callback. The DAO query is
     * responsible for returning the path in ascending recording order, which
     * is required by the map renderer and path segmenter.
     */
    @Test
    fun pathPoints_areReturnedInChronologicalOrder() = runBlocking {
        val travelId = database.travelDao().insert(travel())

        database.travelPathPointDao().insert(pathPoint(travelId, 300L))
        database.travelPathPointDao().insert(pathPoint(travelId, 100L))
        database.travelPathPointDao().insert(pathPoint(travelId, 200L))

        val points = database.travelPathPointDao().pointsForTravel(travelId).first()

        assertEquals(listOf(100L, 200L, 300L), points.map { it.recordedAtUtcMillis })
    }

    /**
     * Verifies active and closed travel lookups after an update.
     *
     * A newly inserted travel has no end timestamp and must be returned by the
     * active-travel queries. Updating the same row with an end timestamp must
     * remove it from the active result and make it eligible for the most-recent
     * closed-travel query. This exercises the real SQL predicates used by the
     * tracking lifecycle rather than only testing repository decisions.
     */
    @Test
    fun travelQueries_updateActiveTravelAndFindMostRecentClosedTravel() = runBlocking {
        val travelId = database.travelDao().insert(travel(startUtcMillis = 100L))
        val initial = database.travelDao().activeTravelOnce()

        assertEquals(travelId, initial?.id)
        assertEquals(travelId, database.travelDao().activeTravelForUserOnce("user-1")?.id)

        database.travelDao().update(
            travel(userId = "user-1", startUtcMillis = 100L).copy(
                id = travelId,
                endUtcMillis = 200L
            )
        )

        assertNull(database.travelDao().activeTravelOnce())
        assertEquals(travelId, database.travelDao().mostRecentClosedTravelOnce()?.id)
        assertEquals(travelId, database.travelDao().mostRecentClosedTravelOnceForUser("user-1")?.id)
    }

    /**
     * Verifies checkpoint replacement for one travel.
     *
     * A checkpoint is keyed directly by `travelId`, so a new sensor reading
     * must replace the previous reading instead of creating another row. The
     * one-shot and reactive DAO methods must both expose the latest state,
     * which is what allows tracking to resume safely after a restart.
     */
    @Test
    fun stepCheckpoint_upsertReplacesPreviousSensorState() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        database.stepCheckpointDao().upsert(
            StepCheckpointEntity(
                travelId = travelId,
                lastSensorValue = 100L,
                lastCheckpointUtcMillis = 1_000L,
                lastKnownLocalDate = "2026-09-01",
                lastKnownTimeZoneId = "Europe/Rome"
            )
        )
        database.stepCheckpointDao().upsert(
            StepCheckpointEntity(
                travelId = travelId,
                lastSensorValue = 175L,
                lastCheckpointUtcMillis = 2_000L,
                lastKnownLocalDate = "2026-09-01",
                lastKnownTimeZoneId = "Europe/Rome"
            )
        )

        val checkpoint = database.stepCheckpointDao().checkpointForTravelOnce(travelId)

        assertEquals(175L, checkpoint?.lastSensorValue)
        assertEquals(2_000L, database.stepCheckpointDao().checkpointForTravel(travelId).first()?.lastCheckpointUtcMillis)
    }

    /**
     * Verifies that the "my POIs" query filters by creator and preserves its
     * newest-first ordering.
     *
     * Demo POIs have no owner and must not appear in a user's personal list.
     * Two POIs created by `user-1` are returned, while a POI created by
     * `user-2` is excluded. Ordering is checked using the auto-generated row
     * identifier, matching the DAO contract used by the My POIs screen.
     */
    @Test
    fun myPois_returnsOnlyTheRequestedUsersPois() = runBlocking {
        database.poiDao().insert(poi(title = "Older", createdByUserId = "user-1"))
        database.poiDao().insert(poi(title = "Other user", createdByUserId = "user-2"))
        database.poiDao().insert(poi(title = "Newer", createdByUserId = "user-1"))

        val myPois = database.poiDao().myPois("user-1").first()

        assertEquals(listOf("Newer", "Older"), myPois.map { it.title })
        assertTrue(myPois.all { it.createdByUserId == "user-1" })
    }

    /**
     * Verifies seed-image healing without overwriting existing image data.
     *
     * A legacy demo POI with a blank `imageResName` receives the bundled
     * drawable name. A second POI with the same title but an existing image is
     * intentionally left unchanged, because the DAO's WHERE clause must only
     * repair missing values.
     */
    @Test
    fun updateSeedImageResName_fillsOnlyMissingImageNames() = runBlocking {
        val missingId = database.poiDao().insert(poi(title = "Mole", city = "Turin").copy(imageResName = null))
        val existingId = database.poiDao().insert(
            poi(title = "Mole", city = "Turin").copy(imageResName = "existing_photo")
        )

        database.poiDao().updateSeedImageResName("Mole", "poi_mole_antonelliana")

        assertEquals("poi_mole_antonelliana", database.poiDao().byIdOnce(missingId)?.imageResName)
        assertEquals("existing_photo", database.poiDao().byIdOnce(existingId)?.imageResName)
    }

    /**
     * Verifies vote lookup, removal, and the empty-score fallback.
     *
     * The DAO exposes both the signed-in user's current vote and the aggregate
     * score. After removing that vote, the user lookup must return null, the
     * vote stream must be empty, and the score must be zero rather than null.
     * This covers the toggle-off path at the actual database layer.
     */
    @Test
    fun poiVote_removeVoteClearsUserVoteAndReturnsZeroScore() = runBlocking {
        val poiId = database.poiDao().insert(poi())
        database.poiVoteDao().vote(
            PoiVoteEntity(poiId = poiId, voterUserId = "user-1", voteValue = 1, votedAtUtcMillis = 100L)
        )

        assertEquals(1, database.poiVoteDao().myVoteOnce(poiId, "user-1"))
        database.poiVoteDao().removeVote(poiId, "user-1")

        assertNull(database.poiVoteDao().myVoteOnce(poiId, "user-1"))
        assertEquals(emptyList<PoiVoteEntity>(), database.poiVoteDao().myVotes("user-1").first())
        assertEquals(0L, database.poiVoteDao().voteScoreForPoiOnce(poiId))
        assertEquals(0L, database.poiVoteDao().count())
    }

    /**
     * Verifies direct lookup and deletion of a media row.
     *
     * Removing a journal media item deletes only its database record. The DAO
     * must find the inserted row by id, remove it, and leave the parent travel
     * available. The associated file URI is merely persisted data and is not
     * accessed by this database test.
     */
    @Test
    fun travelMedia_lookupAndDeleteRemoveOnlyTheDatabaseRow() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val media = TravelMediaEntity(
            travelId = travelId,
            mediaType = "photo",
            imageUri = "file:///private/photo.jpg",
            caption = null,
            capturedAtUtcMillis = 100L,
            createdByUserId = "user-1"
        )
        val mediaId = database.travelMediaDao().insert(media)
        val stored = database.travelMediaDao().mediaById(mediaId)

        assertEquals(mediaId, stored?.id)
        database.travelMediaDao().delete(stored!!)

        assertNull(database.travelMediaDao().mediaById(mediaId))
        assertNotNull(database.travelDao().travelByIdOnce(travelId))
    }

    /**
     * Verifies direct lookup and deletion of a transport segment.
     *
     * A transport row belongs to a travel but is independently removable from
     * the journal. The test checks persistence of nullable stop data, lookup by
     * id, deletion by id, and preservation of the parent travel.
     */
    @Test
    fun travelTransport_lookupAndDeleteRemoveTheSelectedSegment() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val transportId = database.travelTransportDao().insert(
            TravelTransportEntity(
                travelId = travelId,
                transportType = "train",
                lineName = null,
                departureName = "Turin",
                arrivalName = null,
                startUtcMillis = 100L,
                endUtcMillis = null
            )
        )

        assertEquals(transportId, database.travelTransportDao().transportById(transportId)?.id)
        assertNull(database.travelTransportDao().transportById(transportId)?.lineName)
        database.travelTransportDao().deleteById(transportId)

        assertNull(database.travelTransportDao().transportById(transportId))
        assertNotNull(database.travelDao().travelByIdOnce(travelId))
    }

    /**
     * Verifies direct visit lookup and deletion without deleting its POI.
     *
     * The journal can remove a recorded visit while the POI remains available
     * to the catalog. This test covers the composite lookup, the id lookup, the
     * delete operation, and the important distinction between bridge-row data
     * and the referenced POI aggregate.
     */
    @Test
    fun travelPoiVisit_lookupAndDeleteKeepsThePoi() = runBlocking {
        val travelId = database.travelDao().insert(travel())
        val poiId = database.poiDao().insert(poi())
        val visitId = database.travelPoiVisitDao().insert(visit(travelId, poiId, 100L))

        assertEquals(visitId, database.travelPoiVisitDao().visitForTravelAndPoi(travelId, poiId)?.id)
        val stored = database.travelPoiVisitDao().visitByIdOnce(visitId)
        database.travelPoiVisitDao().delete(stored!!)

        assertNull(database.travelPoiVisitDao().visitByIdOnce(visitId))
        assertNotNull(database.poiDao().byIdOnce(poiId))
    }

    /**
     * Verifies reactive profile lookup and the empty-state behavior.
     *
     * Before insertion, a profile query for an unknown Firebase UID emits
     * null. After upserting the profile, the same Flow emits the stored row,
     * confirming that Room invalidates the query and that the reactive profile
     * screen can observe account data without polling.
     */
    @Test
    fun profileFlow_emitsNullThenTheInsertedProfile() = runBlocking {
        val profileFlow = database.userProfileDao().profileByFirebaseUserId("user-1")

        assertNull(profileFlow.first())
        database.userProfileDao().upsert(profile("user-1", "Traveler", 1L))

        assertEquals("Traveler", profileFlow.first()?.displayName)
    }

    /**
     * Verifies recent ghost ordering and optional user identifiers.
     *
     * Ghost snapshots are independent rows, so the DAO only needs to preserve
     * their values and order them from newest to oldest by timestamp. The test
     * also covers the valid anonymous/null user id case.
     */
    @Test
    fun ghostDao_returnsRecentGhostsNewestFirst() = runBlocking {
        database.ghostDao().insert(GhostEntity(userId = null, lat = 45.0, lon = 7.0, timestamp = 100L))
        database.ghostDao().insert(GhostEntity(userId = "ghost-user", lat = 46.0, lon = 8.0, timestamp = 300L))
        database.ghostDao().insert(GhostEntity(userId = "another", lat = 47.0, lon = 9.0, timestamp = 200L))

        val ghosts = database.ghostDao().recentGhosts().first()

        assertEquals(listOf(300L, 200L, 100L), ghosts.map { it.timestamp })
        assertNull(ghosts.last().userId)
    }

    /**
     * Verifies trending snapshot selection and rank ordering.
     *
     * Two snapshots of the same period are inserted at different generation
     * times. `latestTimeSpan` must select the newest one, while destination rows
     * for that snapshot must be ordered by ascending rank position. A separate
     * period remains queryable independently.
     */
    @Test
    fun trendingDao_selectsLatestPeriodAndOrdersRanks() = runBlocking {
        val oldWeekId = database.trendingDestinationDao().upsertTimeSpan(
            TrendingDestinationTimeSpanEntity(
                periodType = "week",
                periodStartUtcMillis = 1L,
                periodEndUtcMillis = 2L,
                generatedAtUtcMillis = 10L
            )
        )
        val latestWeekId = database.trendingDestinationDao().upsertTimeSpan(
            TrendingDestinationTimeSpanEntity(
                periodType = "week",
                periodStartUtcMillis = 3L,
                periodEndUtcMillis = 4L,
                generatedAtUtcMillis = 20L
            )
        )
        val monthId = database.trendingDestinationDao().upsertTimeSpan(
            TrendingDestinationTimeSpanEntity(
                periodType = "month",
                periodStartUtcMillis = 5L,
                periodEndUtcMillis = 6L,
                generatedAtUtcMillis = 15L
            )
        )
        database.trendingDestinationDao().upsertDestinations(
            listOf(
                trendingDestination(latestWeekId, 2, "Second"),
                trendingDestination(latestWeekId, 1, "First"),
                trendingDestination(oldWeekId, 1, "Old"),
                trendingDestination(monthId, 1, "Month")
            )
        )

        assertEquals(latestWeekId, database.trendingDestinationDao().latestTimeSpan("week").first()?.id)
        assertEquals(
            listOf("First", "Second"),
            database.trendingDestinationDao().destinationsForTimeSpan(latestWeekId).first().map { it.destinationName }
        )
        assertEquals("Month", database.trendingDestinationDao().destinationsForTimeSpan(monthId).first().single().destinationName)
    }

    /**
     * Verifies the safe empty-database defaults and nullable entity fields.
     *
     * A fresh in-memory database must expose empty collections and zero
     * aggregates instead of throwing or returning null for numeric totals. The
     * test also inserts a travel with optional title/description values absent,
     * ensuring the schema preserves the nullable fields used by the journal.
     */
    @Test
    fun emptyDatabase_returnsSafeDefaultsAndPersistsNullableTravelFields() = runBlocking {
        assertEquals(emptyList<TravelEntity>(), database.travelDao().allTravelsForUser("user-1").first())
        assertEquals(0L, database.statsDao().totalSteps("user-1").first())
        assertEquals(0.0, database.statsDao().totalDistanceMeters("user-1").first(), 0.001)
        assertEquals(0L, database.poiVoteDao().count())

        val travelId = database.travelDao().insert(
            TravelEntity(
                startUtcMillis = 0L,
                endUtcMillis = null,
                travelTimeZoneId = "Europe/Rome",
                title = null,
                description = null,
                isAnonymous = true,
                userId = "user-1"
            )
        )

        val stored = database.travelDao().travelByIdOnce(travelId)
        assertNull(stored?.title)
        assertNull(stored?.description)
        assertTrue(stored?.isAnonymous == true)
    }

    /**
     * Verifies time-windowed vote aggregation at POI and city level.
     *
     * Two POIs in different cities receive votes before and after the cutoff
     * timestamp. Only votes at or after the cutoff belong to the trend window.
     * The test checks that the DAO returns both POIs, orders them by their
     * window score, and reports the corresponding score and vote count. It
     * repeats the same validation for the city aggregation query, ensuring the
     * join from POIs to cities groups the votes correctly.
     */
    @Test
    fun poiVoteQueries_aggregateScoresAndCountsByPoiAndCity() = runBlocking {
        val torinoPoiId = database.poiDao().insert(poi(title = "Mole", city = "Turin"))
        val parisPoiId = database.poiDao().insert(poi(title = "Louvre", city = "Paris"))

        database.poiVoteDao().vote(PoiVoteEntity(poiId = torinoPoiId, voterUserId = "a", voteValue = 1, votedAtUtcMillis = 100L))
        database.poiVoteDao().vote(PoiVoteEntity(poiId = torinoPoiId, voterUserId = "b", voteValue = -1, votedAtUtcMillis = 200L))
        database.poiVoteDao().vote(PoiVoteEntity(poiId = parisPoiId, voterUserId = "c", voteValue = 1, votedAtUtcMillis = 300L))

        // The vote at t=100 is outside the window; t=200 and t=300 are inside.
        val poiTrends = database.poiVoteDao().poiTrends(150L).first()
        val cityTrends = database.poiVoteDao().cityTrends(150L).first()

        assertEquals(2, poiTrends.size)
        assertEquals("Louvre", poiTrends.first().poi.title)
        assertEquals(1L, poiTrends.first().windowScore)
        assertEquals(1L, poiTrends.first().windowVotes)
        assertEquals(2, cityTrends.size)
        assertEquals("Paris", cityTrends.first().cityName)
        assertEquals(1L, cityTrends.first().windowScore)
        assertEquals(1L, cityTrends.first().windowVotes)
    }

    private fun travel(
        userId: String? = "user-1",
        startUtcMillis: Long = 0L
    ) = TravelEntity(
        startUtcMillis = startUtcMillis,
        endUtcMillis = null,
        travelTimeZoneId = "Europe/Rome",
        title = "Turin weekend",
        userId = userId
    )

    private fun pathPoint(travelId: Long, timestamp: Long) = TravelPathPointEntity(
        travelId = travelId,
        latitude = 45.07,
        longitude = 7.69,
        recordedAtUtcMillis = timestamp
    )

    private fun city(travelId: Long, name: String, arrivedAtUtcMillis: Long) = TravelCityEntity(
        travelId = travelId,
        name = name,
        lat = 45.07,
        lon = 7.69,
        arrivedAtUtcMillis = arrivedAtUtcMillis
    )

    private fun summary(
        travelId: Long,
        localDate: String,
        steps: Long,
        distanceMeters: Double
    ) = StepDailySummaryEntity(
        travelId = travelId,
        localDate = localDate,
        timeZoneId = "Europe/Rome",
        steps = steps,
        distanceMeters = distanceMeters,
        lastSensorValue = steps,
        updatedAtUtcMillis = 1_000L
    )

    private fun profile(userId: String, displayName: String, createdAtUtcMillis: Long) = UserProfileEntity(
        firebaseUserId = userId,
        displayName = displayName,
        photoUrl = null,
        createdAtUtcMillis = createdAtUtcMillis
    )

    private fun poi(
        title: String = "Mole Antonelliana",
        city: String = "Turin",
        createdByUserId: String? = null
    ) = PoiEntity(
        lat = 45.07,
        lon = 7.69,
        title = title,
        description = "A place to visit",
        category = "landmark",
        createdByUserId = createdByUserId,
        city = city
    )

    private fun trendingDestination(
        timeSpanId: Long,
        rankPosition: Int,
        destinationName: String
    ) = TrendingDestinationEntity(
        timeSpanId = timeSpanId,
        rankPosition = rankPosition,
        destinationName = destinationName,
        destinationCountry = null,
        travelCount = rankPosition.toLong(),
        score = rankPosition.toDouble()
    )

    private fun visit(travelId: Long, poiId: Long, timestamp: Long) = TravelPoiVisitEntity(
        travelId = travelId,
        poiId = poiId,
        visitedAtUtcMillis = timestamp
    )
}
