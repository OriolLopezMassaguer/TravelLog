package com.travellog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.travellog.app.data.db.dao.*
import com.travellog.app.data.db.entity.*

@Database(
    entities = [
        TravelDay::class,
        TrackPoint::class,
        PointOfInterest::class,
        CheckIn::class,
        MediaItem::class,
        VoiceNote::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class TravelLogDatabase : RoomDatabase() {
    abstract fun travelDayDao(): TravelDayDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun poiDao(): PoiDao
    abstract fun checkInDao(): CheckInDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove duplicate (external_id, day_id) rows that would block unique index
                // creation. Keep the checked-in row; fall back to highest id.
                database.execSQL("""
                    DELETE FROM points_of_interest
                    WHERE external_id IS NOT NULL
                      AND id NOT IN (
                        SELECT COALESCE(
                            MAX(CASE WHEN checked_in = 1 THEN id ELSE NULL END),
                            MAX(id)
                        )
                        FROM points_of_interest
                        WHERE external_id IS NOT NULL
                        GROUP BY external_id, day_id
                      )
                """.trimIndent())
                database.execSQL("DROP INDEX IF EXISTS index_points_of_interest_external_id")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_points_of_interest_external_id_day_id " +
                    "ON points_of_interest(external_id, day_id)"
                )
            }
        }
    }
}
