package com.example.progmob_progetto_melluso.data.database


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.progmob_progetto_melluso.data.dao.PoiVoteDao
import com.example.progmob_progetto_melluso.data.dao.StatsDao
import com.example.progmob_progetto_melluso.data.dao.StepCheckpointDao
import com.example.progmob_progetto_melluso.data.dao.StepDailySummaryDao
import com.example.progmob_progetto_melluso.data.dao.GhostDao
import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TrendingDestinationDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelMediaDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.dao.TravelTransportDao
import com.example.progmob_progetto_melluso.data.dao.UserProfileDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelCityDao
import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiVoteEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import com.example.progmob_progetto_melluso.data.entities.StepDailySummaryEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelMediaEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import com.example.progmob_progetto_melluso.data.entities.TravelTransportEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationEntity
import com.example.progmob_progetto_melluso.data.entities.TrendingDestinationTimeSpanEntity
import com.example.progmob_progetto_melluso.data.entities.UserProfileEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelCityEntity

@Database(
    entities = [
        TravelEntity::class,
        UserProfileEntity::class,
        StepDailySummaryEntity::class,
        StepCheckpointEntity::class,
        TrendingDestinationTimeSpanEntity::class,
        TrendingDestinationEntity::class,
        TravelMediaEntity::class,
        TravelTransportEntity::class,
        TravelPoiVisitEntity::class,
        TravelPathPointEntity::class,
        PoiVoteEntity::class,
        PoiEntity::class,
        GhostEntity::class,
        TravelCityEntity::class
    ],
    version = 11
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun travelDao(): TravelDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun stepDailySummaryDao(): StepDailySummaryDao
    abstract fun stepCheckpointDao(): StepCheckpointDao
    abstract fun trendingDestinationDao(): TrendingDestinationDao
    abstract fun travelMediaDao(): TravelMediaDao
    abstract fun travelTransportDao(): TravelTransportDao
    abstract fun travelPoiVisitDao(): TravelPoiVisitDao
    abstract fun travelPathPointDao(): TravelPathPointDao
    abstract fun poiVoteDao(): PoiVoteDao
    abstract fun poiDao(): PoiDao
    abstract fun ghostDao(): GhostDao
    abstract fun travelCityDao(): TravelCityDao
    abstract fun statsDao(): StatsDao

    companion object {
        /**
         * v10 -> v11: per-user travel isolation.
         *
         * What it changes:
         * - `travels.userId` (Firebase uid of the owner, null = legacy rows)
         *   added as nullable text: journal, map and statistics are now scoped
         *   to the signed-in user, so switching account shows an empty journal
         *   and zeroed stats instead of the previous user's data. Legacy rows
         *   are claimed by the first user who opens the app (TravelDao.claim
         *   LegacyTravels), preserving old data instead of losing it.
         *
         * Purely additive: existing travels keep their identity.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE travels ADD COLUMN userId TEXT")
            }
        }

        /**
         * v5 -> v6: travel journal feature.
         *
         * What it changes:
         * - `travels.description` (user diary note) added as nullable text.
         * - `travel_photos.media_type` (photo/video) added, defaulting to photo.
         * - `travel_cities` table for the destinations detected inside a travel
         *   (used by the journal and by the map region filter).
         *
         * Votes are kept only on POIs (the existing `poi_votes` table): travel
         * and city vote tables introduced in a draft of v6 are dropped here so
         * the persisted schema always matches the entities list.
         *
         * Every pre-existing table/column keeps its identity, so this is a
         * purely additive migration and existing travels/paths are preserved.
         */
        /**
         * v7 -> v8: one visit per (travel, POI).
         *
         * What it changes:
         * - `travel_poi_visits` gets a UNIQUE index on (travelId, poiId): the
         *   journal detail must show each POI once per trip even when the user
         *   passed near it several times during the same travel.
         *
         * The migration first removes the duplicates that older builds may
         * have recorded (keeping the EARLIEST row per travel+POI), otherwise
         * creating the unique index would fail on existing databases.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM travel_poi_visits WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM travel_poi_visits GROUP BY travelId, poiId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_poi_visits_travelId_poiId` " +
                        "ON `travel_poi_visits` (`travelId`, `poiId`)"
                )
            }
        }

        /**
         * v8 -> v9: photos on the points of interest.
         *
         * What it changes:
         * - `pois.imageUrl` (URL of a real photo of the place, loaded with
         *   Coil) added as nullable text, so the map card and the trends
         *   ranking can show the actual attraction.
         *
         * Purely additive: existing POIs keep their identity.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pois ADD COLUMN imageUrl TEXT")
            }
        }

        /**
         * v9 -> v10: static photos on the points of interest.
         *
         * What it changes:
         * - `pois.imageResName` (name of the bundled drawable with the real
         *   photo, for the demo POIs) and `pois.imageUri` (local file URI of
         *   the gallery photo the user attached when adding a POI) replace
         *   the old `pois.imageUrl` (remote Coil URLs, abandoned in favour of
         *   bundled static images).
         *
         * SQLite versions shipped on older Android (minSdk 24) do not support
         * DROP COLUMN, so the table is rebuilt with the classic Room pattern:
         * create a new table without `imageUrl`, copy every row (IDs are
         * preserved, so `poi_votes` and `travel_poi_visits` keep pointing at
         * the same POIs), drop the old one and rename. Room runs migrations
         * with foreign keys disabled, so the drop cannot cascade into the
         * vote/visit rows.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pois_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`category` TEXT, " +
                        "`createdByUserId` TEXT, " +
                        "`city` TEXT, " +
                        "`imageResName` TEXT, " +
                        "`imageUri` TEXT, " +
                        "`votes` INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "INSERT INTO `pois_new` (id, lat, lon, title, description, " +
                        "category, createdByUserId, city, votes) " +
                        "SELECT id, lat, lon, title, description, category, " +
                        "createdByUserId, city, votes FROM `pois`"
                )
                db.execSQL("DROP TABLE `pois`")
                db.execSQL("ALTER TABLE `pois_new` RENAME TO `pois`")
            }
        }

        /**
         * v6 -> v7: trends scope.
         *
         * What it changes:
         * - `pois.city` (city the POI belongs to) added as nullable text: the
         *   trends page ranks cities by the votes their POIs receive, so each
         *   POI carries the city it lives in.
         *
         * Purely additive: existing POIs keep their identity, new ones (from
         * the demo seed) carry their city.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pois ADD COLUMN city TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE travels ADD COLUMN description TEXT")
                // NOTE: the column name must match the entity property exactly
                // (`mediaType`, no @ColumnInfo): Room validates the migrated
                // schema against the entity column names, so a snake_case
                // alias here would crash every existing install with
                // "Migration didn't properly handle: travel_photos".
                db.execSQL("ALTER TABLE travel_photos ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'photo'")

                // Drop the vote tables from the draft v6, in case a previous
                // dev build created them: votes live only on POIs.
                db.execSQL("DROP TABLE IF EXISTS `travel_votes`")
                db.execSQL("DROP TABLE IF EXISTS `city_votes`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `travel_cities` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`travelId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, " +
                        "`arrivedAtUtcMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`travelId`) REFERENCES `travels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_travel_cities_travelId` ON `travel_cities` (`travelId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_travel_cities_arrivedAtUtcMillis` " +
                        "ON `travel_cities` (`arrivedAtUtcMillis`)"
                )
            }
        }
    }
}
