package com.travellog.app.di

import com.travellog.app.data.db.dao.TravelDayDao
import com.travellog.app.data.db.dao.TrackPointDao
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.TrackingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTravelDayRepository(dao: TravelDayDao): TravelDayRepository =
        TravelDayRepository(dao)

    @Provides
    @Singleton
    fun provideTrackingRepository(dao: TrackPointDao): TrackingRepository =
        TrackingRepository(dao)
}
