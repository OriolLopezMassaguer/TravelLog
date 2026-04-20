package com.travellog.app.data.repository

import com.travellog.app.data.db.dao.TravelDayDao
import com.travellog.app.data.db.entity.TravelDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TravelDayRepository @Inject constructor(
    private val dao: TravelDayDao
) {
    fun getAllDays(): Flow<List<TravelDay>> = dao.getAllDays()

    suspend fun getOrCreateToday(): TravelDay {
        val today = LocalDate.now().toString()
        return dao.getDayByDate(today) ?: run {
            val id = dao.insert(
                TravelDay(
                    date = today,
                    startedAt = System.currentTimeMillis()
                )
            )
            dao.getDayById(id)!!
        }
    }

    suspend fun getByDate(date: String): TravelDay? = dao.getDayByDate(date)

    suspend fun setGpxTrackPath(dayId: Long, path: String) =
        dao.setGpxTrackPath(dayId, path)

    suspend fun setGpxPoiPath(dayId: Long, path: String) =
        dao.setGpxPoiPath(dayId, path)

    suspend fun updateDistance(dayId: Long, distanceMeters: Double) =
        dao.updateDistance(dayId, distanceMeters)

    suspend fun closeDay(dayId: Long) =
        dao.setEndedAt(dayId, System.currentTimeMillis())
}
