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
     * Returns POIs near [lat]/[lon] from Overpass without persisting them.
     * POIs are only stored to the database when the user explicitly checks in.
     */
    suspend fun fetchNearbyPois(
        lat: Double,
        lon: Double,
        dayId: Long
    ): List<PointOfInterest> {
        val elements = overpassService.queryNearby(lat, lon)
        return elements.mapNotNull { it.toEntity(dayId) }
    }

    // ── Check-in ─────────────────────────────────────────────────────────────

    suspend fun checkIn(poi: PointOfInterest, dayId: Long, location: Location?) {
        val now = System.currentTimeMillis()
        val insertedId = poiDao.insert(poi.copy(fetchedAt = now, dayId = dayId))
        val poiId = if (insertedId != -1L) insertedId
                    else poiDao.getByExternalId(poi.externalId ?: return, dayId)?.id ?: return
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

    suspend fun deleteCheckIn(poiId: Long) {
        val poi = poiDao.getById(poiId) ?: return
        val dayId = poi.dayId
        poiDao.uncheckIn(poiId)
        checkInDao.deleteForPoi(poiId)
        rewritePoiGpx(dayId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun rewritePoiGpx(dayId: Long) {
        val day       = travelDayRepository.getByDate(LocalDate.now().toString()) ?: return
        val checkedIn = poiDao.getPoisForDayOnce(dayId).filter { it.checkedIn }
        val file      = gpxPoiWriter.writeAll(day.date, checkedIn)
        travelDayRepository.setGpxPoiPath(dayId, file.absolutePath)
    }

    private fun OverpassElement.toEntity(dayId: Long): PointOfInterest? {
        val poiName = name ?: return null
        return PointOfInterest(
            dayId        = dayId,
            externalId   = "osm:$type/$id",
            name         = poiName,
            category     = category,
            latitude     = latValue,
            longitude    = lonValue,
            address      = address,
            openingHours = tags["opening_hours"],
            website      = tags["website"],
            phone        = tags["phone"],
            fetchedAt    = System.currentTimeMillis(),
            source       = "overpass",
            rawData      = gson.toJson(this)
        )
    }

}
