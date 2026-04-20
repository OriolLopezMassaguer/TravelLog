package com.travellog.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.travellog.app.scheduling.TrackingScheduler
import com.travellog.app.service.GpsTrackingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: TrackingScheduler

    companion object {
        const val ACTION_START = "com.travellog.app.ACTION_START_TRACKING"
        const val ACTION_STOP  = "com.travellog.app.ACTION_STOP_TRACKING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                ContextCompat.startForegroundService(context, GpsTrackingService.startIntent(context))
                // Re-arm the start alarm for the next day
                scheduler.rescheduleAfterFire(ACTION_START)
            }
            ACTION_STOP -> {
                context.startService(GpsTrackingService.stopIntent(context))
                // Re-arm the stop alarm for the next midnight
                scheduler.rescheduleAfterFire(ACTION_STOP)
            }
        }
    }
}
