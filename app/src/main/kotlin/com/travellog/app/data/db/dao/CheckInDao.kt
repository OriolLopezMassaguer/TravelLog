package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.CheckIn
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_ins WHERE day_id = :dayId ORDER BY checked_in_at ASC")
    fun getCheckInsForDay(dayId: Long): Flow<List<CheckIn>>

    @Query("SELECT * FROM check_ins WHERE poi_id = :poiId ORDER BY checked_in_at DESC")
    suspend fun getCheckInsForPoi(poiId: Long): List<CheckIn>

    @Query("SELECT * FROM check_ins WHERE day_id = :dayId ORDER BY checked_in_at DESC LIMIT 1")
    suspend fun getLatestCheckIn(dayId: Long): CheckIn?

    @Insert
    suspend fun insert(checkIn: CheckIn): Long

    @Delete
    suspend fun delete(checkIn: CheckIn)
}
