package com.travellog.app.data.repository

import android.location.Location
import com.google.gson.Gson
import com.travellog.app.data.db.dao.CheckInDao
import com.travellog.app.data.db.dao.PoiDao
import com.travellog.app.data.db.entity.CheckIn
import com.travellog.app.data.db.entity.PointOfInterest
import com.travellog.app.data.gpx.GpxPoiWriter
import com.travellog.app.data.remote.OverpassElement
import com.travellog.app.data.remote.OverpassService
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoiRepository @Inject constructor(
    private val poiDao: PoiDao,
    private val checkInDao: CheckInDao,
    private val overpassService: OverpassService,
    private val gpxPoiWriter: GpxPoiWriter,
    private val travelDayRepository: TravelDayRepository,
) {
    private val gson = Gson()

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getPoisForDay(dayId: Long): Flow<List<PointOfInterest>> =
        poiDao.getPoisForDay(dayId)

    fun getCheckedInPoisForDay(dayId: Long): Flow<List<PointOfInterest>> =
        poiDao.getCheckedInPois(dayId)

    // ── Fetch nearby (with 5-min cache) ──────────────────────────────────────

    /**
     * Returns POIs near [lat]/[lon] for [dayId].
     * Uses cached DB results if a fetch was done within 5 minutes within ~200 m;
     * otherwise queries Overpass and stores results.
     */
    suspend fun fetchNearbyPois(
        lat: Double,
        lon: Double,
        dayId: Long
    ): List<PointOfInterest> {
        val cacheAfterMs   = System.currentTimeMillis() - CACHE_TTL_MS
        // ≈200 m in degrees (rough bounding box, not exact great-circle)
        val degRadiusSq    = 0.0018 * 0.0018

        val cached = poiDao.getNearbyPoisCached(dayId, lat, lon, degRadiusSq, cacheAfterMs)
        if (cached.isNotEmpty()) return cached

        val elements = overpassService.queryNearby(lat, lon)
        if (elements.isEmpty()) {
            // Network failed — return whatever we have cached regardless of age
            return poiDao.getPoisForDayOnce(dayId)
        }

        val pois = elements.mapNotNull { it.toEntity(dayId) }
        poiDao.insertAll(pois)   // IGNORE on conflict — won't overwrite check-ins

        // Write / refresh pois.gpx
        val day = travelDayRepository.getByDate(LocalDate.now().toString())
        if (day != null) {
            val allPois = poiDao.getPoisForDayOnce(dayId)
            val file    = gpxPoiWriter.writeAll(day.date, allPois)
            if (day.gpxPoiPath == null) {
                travelDayRepository.setGpxPoiPath(dayId, file.absolutePath)
            }
        }

        return poiDao.getPoisForDayOnce(dayId)
    }

    // ── Check-in ─────────────────────────────────────────────────────────────

    suspend fun checkIn(poiId: Long, dayId: Long, location: Location?) {
        val now = System.currentTimeMillis()
        poiDao.checkIn(poiId, now)
        checkInDao.insert(
            CheckIn(
                poiId       = poiId,
                dayId       = dayId,
                latitude    = location?.latitude  ?: 0.0,
                longitude   = location?.longitude ?: 0.0,
                altitude    = location?.altitude  ?: 0.0,
                checkedInAt = now
            )
        )
        rewritePoiGpx(dayId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun rewritePoiGpx(dayId: Long) {
        val day  = travelDayRepository.getByDate(LocalDate.now().toString()) ?: return
        val pois = poiDao.getPoisForDayOnce(dayId)
        val file = gpxPoiWriter.writeAll(day.date, pois)
        travelDayRepository.setGpxPoiPath(dayId, file.absolutePath)
    }

    private fun OverpassElement.toEntity(dayId: Long): PointOfInterest? {
        val poiName = name ?: return null
        return PointOfInterest(
            dayId        = dayId,
            externalId   = "osm:node/$id",
            name         = poiName,
            category     = category,
            latitude     = lat,
            longitude    = lon,
            address      = address,
            openingHours = tags["opening_hours"],
            website      = tags["website"],
            phone        = tags["phone"],
            fetchedAt    = System.currentTimeMillis(),
            source       = "overpass",
            rawData      = gson.toJson(this)
        )
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1_000L
    }
}
