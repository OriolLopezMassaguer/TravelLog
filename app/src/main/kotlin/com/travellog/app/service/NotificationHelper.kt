package com.travellog.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.travellog.app.MainActivity
import com.travellog.app.R

object NotificationHelper {

    const val CHANNEL_ID = "travellog_gps_tracking"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_tracking),
            NotificationManager.IMPORTANCE_LOW   // silent but persistent
        ).apply {
            description = "Shown while TravelLog is recording your GPS track"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun buildTrackingNotification(
        context: Context,
        distanceKm: Double,
        pointCount: Int
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val distanceText = if (distanceKm < 1.0)
            "${(distanceKm * 1000).toInt()} m recorded"
        else
            "${"%.1f".format(distanceKm)} km recorded"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_tracking_title))
            .setContentText(distanceText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
