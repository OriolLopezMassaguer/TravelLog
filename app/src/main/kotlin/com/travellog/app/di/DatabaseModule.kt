package com.travellog.app.di

import android.content.Context
import androidx.room.Room
import com.travellog.app.data.db.TravelLogDatabase
import com.travellog.app.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TravelLogDatabase =
        Room.databaseBuilder(context, TravelLogDatabase::class.java, "travellog.db")
            .addMigrations(TravelLogDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideTravelDayDao(db: TravelLogDatabase): TravelDayDao = db.travelDayDao()
    @Provides fun provideTrackPointDao(db: TravelLogDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun providePoiDao(db: TravelLogDatabase): PoiDao = db.poiDao()
    @Provides fun provideCheckInDao(db: TravelLogDatabase): CheckInDao = db.checkInDao()
    @Provides fun provideMediaItemDao(db: TravelLogDatabase): MediaItemDao = db.mediaItemDao()
    @Provides fun provideVoiceNoteDao(db: TravelLogDatabase): VoiceNoteDao = db.voiceNoteDao()
}
