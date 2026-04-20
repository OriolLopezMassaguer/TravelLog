package com.travellog.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.travellog.app.scheduling.TrackingScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: TrackingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule daily alarms — they are lost after reboot
            scheduler.schedule()
        }
    }
}
