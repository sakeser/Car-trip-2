package com.cartrip.analyzer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        LocationSample::class,
        MotionSample::class,
        AnalysisPointEntity::class,
        DriveEventEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cartrip.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                    )
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_points` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` INTEGER NOT NULL,
                        `t` INTEGER NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `speedKmh` REAL NOT NULL,
                        `longAccel` REAL NOT NULL,
                        `latAccel` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_points_tripId` " +
                        "ON `analysis_points` (`tripId`)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` INTEGER NOT NULL,
                        `t` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `magnitude` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_events_tripId` " +
                        "ON `drive_events` (`tripId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `googleEtaTrafficS` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `googleEtaFreeFlowS` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `etaSource` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `etaFetchedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'completed'")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `endReason` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `lastCheckpointAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `lastLocationAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `lastMotionAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `locationSampleCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `motionSampleCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `gpsGapCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """
                    UPDATE `trips`
                    SET `status` = 'partial',
                        `endReason` = 'legacy_unfinished',
                        `endTime` = CASE
                            WHEN `lastCheckpointAt` > 0 THEN `lastCheckpointAt`
                            ELSE `startTime`
                        END
                    WHERE `endTime` = 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `hardBrakePct` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `aggressiveTurnPct` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `hardAccelPct` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `speedingPct` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `maxOverLimitKmh` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `limitCoverage` REAL NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `analysis_points` ADD COLUMN `speedLimitKmh` REAL NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `motions` ADD COLUMN `grx` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `motions` ADD COLUMN `gry` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `motions` ADD COLUMN `grz` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `maxJerk` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `jerkyPct` REAL NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `syncedAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `syncError` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `isSample` INTEGER NOT NULL DEFAULT 0")
                // Existing trips with no raw GPS track are demo trips; real recordings have locations.
                database.execSQL(
                    "UPDATE `trips` SET `isSample` = 1 WHERE `id` NOT IN (SELECT DISTINCT `tripId` FROM `locations`)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `roughRoadPct` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `potholeCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `harshStopCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `motionBrakeCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `motionAccelCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `motionTurnCount` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trips` ADD COLUMN `fusedConfidence` REAL NOT NULL DEFAULT 0")
            }
        }
    }
}
