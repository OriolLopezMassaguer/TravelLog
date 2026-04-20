package com.travellog.app.data.repository

import android.location.Location
import com.travellog.app.data.db.dao.TrackPointDao
import com.travellog.app.data.db.entity.TrackPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepository @Inject constructor(
    private val dao: TrackPointDao
) {
    fun getPointsForDay(dayId: Long): Flow<List<TrackPoint>> =
        dao.getPointsForDay(dayId)

    suspend fun getPointsForDayOnce(dayId: Long): List<TrackPoint> =
        dao.getPointsForDayOnce(dayId)

    suspend fun insertBatch(points: List<TrackPoint>) =
        dao.insertAll(points)

    /** Returns total track distance in metres for a list of ordered points. */
    fun calculateDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        val result = FloatArray(1)
        for (i in 1 until points.size) {
            Location.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude,     points[i].longitude,
                result
            )
            total += result[0]
        }
        return total
    }

    /** Incremental distance between two consecutive locations. */
    fun distanceBetween(a: TrackPoint, b: TrackPoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }
}
