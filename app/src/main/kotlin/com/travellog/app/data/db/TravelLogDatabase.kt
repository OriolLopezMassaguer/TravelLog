package com.travellog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true
)
abstract class TravelLogDatabase : RoomDatabase() {
    abstract fun travelDayDao(): TravelDayDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun poiDao(): PoiDao
    abstract fun checkInDao(): CheckInDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun voiceNoteDao(): VoiceNoteDao
}
