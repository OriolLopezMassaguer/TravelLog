package com.travellog.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.*
import com.travellog.app.data.db.entity.TrackPoint
import com.travellog.app.data.gpx.GpxWriter
import com.travellog.app.data.repository.TravelDayRepository
import com.travellog.app.data.repository.TrackingRepository
import com.travellog.app.data.settings.AppSettings
import com.travellog.app.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Foreground service that records GPS track continuously.
 *
 * Adaptive GPS: after STATIONARY_COUNT_THRESHOLD consecutive readings below
 * STATIONARY_SPEED_MS the service switches to a low-power balanced request.
 * One reading above the threshold immediately restores high-accuracy mode.
 */
@AndroidEntryPoint
class GpsTrackingService : Service() {

    @Inject lateinit var travelDayRepository: TravelDayRepository
    @Inject lateinit var trackingRepository: TrackingRepository
    @Inject lateinit var gpxWriter: GpxWriter
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentDayId: Long = -1
    private val pointBuffer = mutableListOf<TrackPoint>()
    private var lastFlushMs = 0L
    private var totalDistanceMeters = 0.0
    private var lastFlushedPoint: TrackPoint? = null

    // Adaptive GPS state
    private var currentSettings = AppSettings()
    private var stationaryCount = 0
    private var isLowPowerMode  = false

    companion object {
        const val ACTION_START = "com.travellog.app.ACTION_START_TRACKING"
        const val ACTION_STOP  = "com.travellog.app.ACTION_STOP_TRACKING"

        private const val BUFFER_MAX_SIZE = 10
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val LOCATION_MIN_DISTANCE_M = 5f

        private const val STATIONARY_SPEED_MS        = 0.5f   // m/s
        private const val STATIONARY_COUNT_THRESHOLD = 3      // consecutive slow readings
        private const val LOW_POWER_INTERVAL_MS      = 60_000L

        fun startIntent(context: Context) =
            Intent(context, GpsTrackingService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, GpsTrackingService::class.java).apply { action = ACTION_STOP }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = buildLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP  -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Start / stop ─────────────────────────────────────────────────────────

    private fun startTracking() {
        serviceScope.launch {
            currentSettings = settingsRepository.settings.first()

            val day = travelDayRepository.getOrCreateToday()
            currentDayId = day.id

            val trackFile = gpxWriter.initDay(day.date)
            travelDayRepository.setGpxTrackPath(day.id, trackFile.absolutePath)

            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.buildTrackingNotification(this@GpsTrackingService, 0.0, 0)
            )

            requestLocationUpdates(lowPower = false)
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        serviceScope.launch {
            flushBuffer()
            if (currentDayId != -1L) {
                gpxWriter.finalizeDay()
                travelDayRepository.closeDay(currentDayId)
                travelDayRepository.updateDistance(currentDayId, totalDistanceMeters)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Location updates ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(lowPower: Boolean) {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val intervalMs = if (lowPower) LOW_POWER_INTERVAL_MS
                         else currentSettings.gpsIntervalSeconds * 1_000L
        val priority   = if (lowPower) Priority.PRIORITY_BALANCED_POWER_ACCURACY
                         else Priority.PRIORITY_HIGH_ACCURACY

        val request = LocationRequest.Builder(priority, intervalMs).apply {
            if (!lowPower) setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun buildLocationCallback() = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                serviceScope.launch { handleLocation(location) }
            }
        }
    }

    private suspend fun handleLocation(location: android.location.Location) {
        if (currentDayId == -1L) return

        val point = TrackPoint(
            dayId      = currentDayId,
            latitude   = location.latitude,
            longitude  = location.longitude,
            altitude   = location.altitude,
            accuracy   = location.accuracy,
            speed      = if (location.hasSpeed()) location.speed else 0f,
            heading    = if (location.hasBearing()) location.bearing else 0f,
            recordedAt = location.time
        )

        synchronized(pointBuffer) { pointBuffer.add(point) }

        lastFlushedPoint?.let { prev ->
            totalDistanceMeters += trackingRepository.distanceBetween(prev, point)
        }
        lastFlushedPoint = point

        // Adaptive GPS mode switching
        if (currentSettings.adaptiveGps) {
            val isStationary = location.hasSpeed() && location.speed < STATIONARY_SPEED_MS
            if (isStationary) {
                stationaryCount++
                if (!isLowPowerMode && stationaryCount >= STATIONARY_COUNT_THRESHOLD) {
                    isLowPowerMode = true
                    requestLocationUpdates(lowPower = true)
                }
            } else {
                stationaryCount = 0
                if (isLowPowerMode) {
                    isLowPowerMode = false
                    requestLocationUpdates(lowPower = false)
                }
            }
        }

        val now = System.currentTimeMillis()
        val shouldFlush = synchronized(pointBuffer) { pointBuffer.size >= BUFFER_MAX_SIZE } ||
                          (now - lastFlushMs) >= FLUSH_INTERVAL_MS

        if (shouldFlush) flushBuffer()

        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildTrackingNotification(
                this,
                totalDistanceMeters / 1000.0,
                synchronized(pointBuffer) { pointBuffer.size }
            )
        )
    }

    // ── Flush ─────────────────────────────────────────────────────────────────

    private suspend fun flushBuffer() {
        val batch = synchronized(pointBuffer) {
            if (pointBuffer.isEmpty()) return
            val copy = pointBuffer.toList()
            pointBuffer.clear()
            copy
        }

        trackingRepository.insertBatch(batch)
        gpxWriter.appendPoints(batch)
        lastFlushMs = System.currentTimeMillis()
    }
}
