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
    version = 3,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
