package com.travellog.app.data.db.dao

import androidx.room.*
import com.travellog.app.data.db.entity.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {

    @Query("SELECT * FROM media_items WHERE day_id = :dayId ORDER BY recorded_at ASC")
    fun getMediaForDay(dayId: Long): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE poi_id = :poiId ORDER BY recorded_at ASC")
    fun getMediaForPoi(poiId: Long): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE day_id = :dayId AND type = :type ORDER BY recorded_at ASC")
    fun getMediaByType(dayId: Long, type: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE file_path = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE day_id = :dayId AND type != 'voice_note' ORDER BY recorded_at ASC")
    suspend fun getPhotoVideoForDayOnce(dayId: Long): List<MediaItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaItem>)

    @Update
    suspend fun update(item: MediaItem)

    @Query("UPDATE media_items SET poi_id = :poiId, association_method = :method, distance_to_poi_m = :distanceM WHERE id = :id")
    suspend fun updateAssociation(id: Long, poiId: Long?, method: String?, distanceM: Double?)

    @Delete
    suspend fun delete(item: MediaItem)
}
