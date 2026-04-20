package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE day_id = :dayId ORDER BY recorded_at ASC")
    fun getPointsForDay(dayId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE day_id = :dayId ORDER BY recorded_at ASC")
    suspend fun getPointsForDayOnce(dayId: Long): List<TrackPoint>

    @Query("SELECT * FROM track_points WHERE day_id = :dayId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatestPoint(dayId: Long): TrackPoint?

    @Query("""
        SELECT * FROM track_points
        WHERE day_id = :dayId
          AND recorded_at BETWEEN :fromMs AND :toMs
        ORDER BY recorded_at ASC
    """)
    suspend fun getPointsInRange(dayId: Long, fromMs: Long, toMs: Long): List<TrackPoint>

    @Insert
    suspend fun insert(point: TrackPoint): Long

    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    @Query("DELETE FROM track_points WHERE day_id = :dayId")
    suspend fun deleteForDay(dayId: Long)
}
