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
                database.execSQL("DROP INDEX IF EXISTS index_points_of_interest_external_id")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_points_of_interest_external_id_day_id " +
                    "ON points_of_interest(external_id, day_id)"
                )
            }
        }
    }
}
