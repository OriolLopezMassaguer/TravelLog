package com.travellog.app.data.repository

import android.location.Location
import com.travellog.app.data.db.dao.CheckInDao
import com.travellog.app.data.db.dao.MediaItemDao
import com.travellog.app.data.db.dao.PoiDao
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.TravelDay
import com.travellog.app.media.ExifReader
import com.travellog.app.media.MediaScanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class MediaRepository @Inject constructor(
    private val mediaItemDao: MediaItemDao,
    private val poiDao: PoiDao,
    private val checkInDao: CheckInDao,
    private val trackingRepository: TrackingRepository,
    private val mediaScanner: MediaScanner,
    private val exifReader: ExifReader,
) {
    fun getMediaForDay(dayId: Long): Flow<List<MediaItem>> =
        mediaItemDao.getMediaForDay(dayId)

    fun getPhotoVideoForDay(dayId: Long): Flow<List<MediaItem>> =
        // Voice notes are excluded — they have their own screen
        mediaItemDao.getMediaForDay(dayId)

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Scans the device MediaStore for photos and videos taken during [day]'s
     * tracking window, associates each with the best POI, and inserts new records.
     * Returns the number of newly imported items.
     */
    suspend fun importMediaForDay(day: TravelDay): Int {
        val startMs = day.startedAt ?: defaultStart(day.date)
        val endMs   = day.endedAt   ?: System.currentTimeMillis()

        val deviceItems = mediaScanner.scanRange(startMs, endMs)
        var imported    = 0

        for (item in deviceItems) {
            if (mediaItemDao.getByFilePath(item.filePath) != null) continue

            // Step 1: EXIF GPS
            val exif = if (item.type == "photo") exifReader.read(item.uri) else null
            val lat  = exif?.latitude  ?: interpolateLat(day.id, item.dateTakenMs)
            val lon  = exif?.longitude ?: interpolateLon(day.id, item.dateTakenMs)
            val alt  = exif?.altitude  ?: 0.0

            val method = when {
                exif != null -> "exif"
                lat != null  -> "track_interpolation"
                else         -> null
            }

            // Step 2 & 3: associate to POI
            val poiId = if (lat != null && lon != null)
                findNearestPoi(day.id, lat, lon)
            else
                activeCheckInPoi(day.id, item.dateTakenMs)

            mediaItemDao.insert(
                MediaItem(
                    dayId             = day.id,
                    poiId             = poiId,
                    type              = item.type,
                    filePath          = item.filePath,
                    latitude          = lat,
                    longitude         = lon,
                    altitude          = alt,
                    recordedAt        = item.dateTakenMs,
                    durationSeconds   = item.durationSeconds,
                    associationMethod = method
                )
            )
            imported++
        }

        return imported
    }

    // ── Association helpers ───────────────────────────────────────────────────

    private suspend fun findNearestPoi(dayId: Long, lat: Double, lon: Double): Long? {
        val result = FloatArray(1)
        return poiDao.getPoisForDayOnce(dayId)
            .map { poi ->
                Location.distanceBetween(lat, lon, poi.latitude, poi.longitude, result)
                poi.id to result[0]
            }
            .filter { (_, dist) -> dist <= 100f }
            .minByOrNull { (_, dist) -> dist }
            ?.first
    }

    private suspend fun activeCheckInPoi(dayId: Long, atMs: Long): Long? {
        val latest = checkInDao.getLatestCheckIn(dayId) ?: return null
        val age    = atMs - latest.checkedInAt
        return if (age in 0..(4 * 60 * 60_000L)) latest.poiId else null
    }

    private suspend fun interpolateLat(dayId: Long, timestampMs: Long): Double? =
        closestTrackPoint(dayId, timestampMs)?.latitude

    private suspend fun interpolateLon(dayId: Long, timestampMs: Long): Double? =
        closestTrackPoint(dayId, timestampMs)?.longitude

    private suspend fun closestTrackPoint(dayId: Long, timestampMs: Long) =
        trackingRepository.getPointsForDayOnce(dayId)
            .minByOrNull { abs(it.recordedAt - timestampMs) }
            ?.takeIf { abs(it.recordedAt - timestampMs) <= 5 * 60_000L }   // within 5 min

    // ── Date helpers ──────────────────────────────────────────────────────────

    private fun defaultStart(date: String): Long =
        LocalDate.parse(date)
            .atTime(LocalTime.of(8, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
