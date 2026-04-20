package com.travellog.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.travellog.app.receiver.TrackingAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily GPS tracking window: start at 08:00, stop at 00:00 (midnight).
 *
 * Uses setExactAndAllowWhileIdle so alarms fire even in Doze mode.
 * Call schedule() once on first launch and after every device reboot.
 */
@Singleton
class TrackingScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(startHour: Int = 8) {
        scheduleStart(startHour)
        scheduleStop()
    }

    fun cancel() {
        alarmManager.cancel(startPendingIntent())
        alarmManager.cancel(stopPendingIntent())
    }

    // ── Start at 08:00 ───────────────────────────────────────────────────────

    private fun scheduleStart(startHour: Int = 8) {
        val triggerAt = nextOccurrence(hour = startHour, minute = 0)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            startPendingIntent()
        )
    }

    // ── Stop at 00:00 (next midnight) ────────────────────────────────────────

    private fun scheduleStop() {
        val triggerAt = nextMidnight()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            stopPendingIntent()
        )
    }

    // ── Reschedule both for the following day (called by alarm receivers) ────

    fun rescheduleAfterFire(action: String) {
        when (action) {
            TrackingAlarmReceiver.ACTION_START -> scheduleStart()
            TrackingAlarmReceiver.ACTION_STOP  -> scheduleStop()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns epoch millis of the next occurrence of [hour]:[minute].
     * If today's occurrence is in the past, returns tomorrow's.
     */
    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    /** Always the next calendar midnight (start of tomorrow). */
    private fun nextMidnight(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun startPendingIntent(): PendingIntent = pendingIntent(TrackingAlarmReceiver.ACTION_START, 100)
    private fun stopPendingIntent(): PendingIntent  = pendingIntent(TrackingAlarmReceiver.ACTION_STOP,  101)

    private fun pendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TrackingAlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
