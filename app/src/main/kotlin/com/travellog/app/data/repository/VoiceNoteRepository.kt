package com.travellog.app.data.repository

import android.location.Location
import com.travellog.app.data.db.dao.CheckInDao
import com.travellog.app.data.db.dao.MediaItemDao
import com.travellog.app.data.db.dao.PoiDao
import com.travellog.app.data.db.dao.VoiceNoteDao
import com.travellog.app.data.db.entity.MediaItem
import com.travellog.app.data.db.entity.VoiceNote
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceNoteRepository @Inject constructor(
    private val voiceNoteDao: VoiceNoteDao,
    private val mediaItemDao: MediaItemDao,
    private val poiDao: PoiDao,
    private val checkInDao: CheckInDao,
) {
    fun getVoiceNotesForDay(dayId: Long): Flow<List<VoiceNote>> =
        voiceNoteDao.getVoiceNotesForDay(dayId)

    /**
     * Persists a completed recording as both a [MediaItem] (type = "voice_note")
     * and a [VoiceNote] record. Auto-associates with a POI using:
     *   1. Active check-in within the last 4 h
     *   2. Nearest POI within 100 m
     */
    suspend fun save(
        dayId: Long,
        filePath: String,
        durationSeconds: Int,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        heading: Float?,
        nearbyPoiIds: List<Long>,
        recordedAt: Long,
    ): VoiceNote {
        val associatedPoiId = findAssociatedPoi(dayId, latitude, longitude)

        val mediaItemId = mediaItemDao.insert(
            MediaItem(
                dayId             = dayId,
                poiId             = associatedPoiId,
                type              = "voice_note",
                filePath          = filePath,
                latitude          = latitude,
                longitude         = longitude,
                altitude          = altitude,
                recordedAt        = recordedAt,
                durationSeconds   = durationSeconds,
                associationMethod = associatedPoiId?.let { "checkin_or_proximity" }
            )
        )

        val note = VoiceNote(
            mediaItemId        = mediaItemId,
            dayId              = dayId,
            poiId              = associatedPoiId,
            latitude           = latitude,
            longitude          = longitude,
            altitude           = altitude,
            heading            = heading,
            recordedAt         = recordedAt,
            durationSeconds    = durationSeconds,
            filePath           = filePath,
            nearbyPoisSnapshot = nearbyPoiIds.joinToString(",").ifEmpty { null }
        )

        val id = voiceNoteDao.insert(note)
        return note.copy(id = id)
    }

    // ── Association ───────────────────────────────────────────────────────────

    private suspend fun findAssociatedPoi(dayId: Long, lat: Double, lon: Double): Long? {
        // Priority 1: active check-in within last 4 hours
        val latest = checkInDao.getLatestCheckIn(dayId)
        if (latest != null) {
            val ageMs = System.currentTimeMillis() - latest.checkedInAt
            if (ageMs < 4 * 60 * 60_000L) return latest.poiId
        }

        // Priority 2: nearest POI within 100 m
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
}
