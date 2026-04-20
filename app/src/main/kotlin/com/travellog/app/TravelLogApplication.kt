package com.travellog.app

import android.app.Application
import com.travellog.app.data.gpx.GpxWriter
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TravelLogApplication : Application() {

    @Inject lateinit var travelDayRepository: TravelDayRepository
    @Inject lateinit var gpxWriter: GpxWriter

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        appScope.launch { repairOrphanedGpxFiles() }
    }

    private suspend fun repairOrphanedGpxFiles() {
        val dates = travelDayRepository.getAllDays().first()
            .filter { it.gpxTrackPath != null }
            .map { it.date }
        gpxWriter.recoverOrphanedDays(dates)
    }
}
