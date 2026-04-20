package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.PointOfInterest
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {

    @Query("SELECT * FROM points_of_interest WHERE day_id = :dayId ORDER BY name ASC")
    fun getPoisForDay(dayId: Long): Flow<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE day_id = :dayId ORDER BY name ASC")
    suspend fun getPoisForDayOnce(dayId: Long): List<PointOfInterest>

    @Query("SELECT * FROM points_of_interest WHERE day_id = :dayId AND checked_in = 1 ORDER BY checked_in_at ASC")
    fun getCheckedInPois(dayId: Long): Flow<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE id = :id")
    suspend fun getById(id: Long): PointOfInterest?

    @Query("SELECT * FROM points_of_interest WHERE external_id = :externalId AND day_id = :dayId LIMIT 1")
    suspend fun getByExternalId(externalId: String, dayId: Long): PointOfInterest?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(poi: PointOfInterest): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(pois: List<PointOfInterest>)

    @Update
    suspend fun update(poi: PointOfInterest)

    @Query("UPDATE points_of_interest SET checked_in = 1, checked_in_at = :checkedInAt WHERE id = :id")
    suspend fun checkIn(id: Long, checkedInAt: Long)

    @Query("""
        SELECT * FROM points_of_interest
        WHERE day_id = :dayId
          AND fetched_at > :afterMs
          AND ((:lat - latitude) * (:lat - latitude) + (:lon - longitude) * (:lon - longitude)) < :degreeRadiusSq
    """)
    suspend fun getNearbyPoisCached(
        dayId: Long,
        lat: Double,
        lon: Double,
        degreeRadiusSq: Double,
        afterMs: Long
    ): List<PointOfInterest>

    @Delete
    suspend fun delete(poi: PointOfInterest)
}
