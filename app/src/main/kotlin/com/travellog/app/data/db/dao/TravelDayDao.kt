package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.TravelDay
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDayDao {

    @Query("SELECT * FROM travel_days ORDER BY date DESC")
    fun getAllDays(): Flow<List<TravelDay>>

    @Query("SELECT * FROM travel_days WHERE date = :date LIMIT 1")
    suspend fun getDayByDate(date: String): TravelDay?

    @Query("SELECT * FROM travel_days WHERE id = :id")
    suspend fun getDayById(id: Long): TravelDay?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: TravelDay): Long

    @Update
    suspend fun update(day: TravelDay)

    @Query("UPDATE travel_days SET total_distance_m = :distanceM WHERE id = :id")
    suspend fun updateDistance(id: Long, distanceM: Double)

    @Query("UPDATE travel_days SET ended_at = :endedAt WHERE id = :id")
    suspend fun setEndedAt(id: Long, endedAt: Long)

    @Query("UPDATE travel_days SET gpx_track_path = :path WHERE id = :id")
    suspend fun setGpxTrackPath(id: Long, path: String)

    @Query("UPDATE travel_days SET gpx_poi_path = :path WHERE id = :id")
    suspend fun setGpxPoiPath(id: Long, path: String)

    @Query("UPDATE travel_days SET exported_pdf_path = :path WHERE id = :id")
    suspend fun setExportedPdfPath(id: Long, path: String)

    @Delete
    suspend fun delete(day: TravelDay)
}
