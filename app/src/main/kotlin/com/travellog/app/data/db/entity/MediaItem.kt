package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = TravelDay::class,
            parentColumns = ["id"],
            childColumns = ["day_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PointOfInterest::class,
            parentColumns = ["id"],
            childColumns = ["poi_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("day_id"), Index("poi_id"), Index("recorded_at")]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_id") val dayId: Long,
    @ColumnInfo(name = "poi_id") val poiId: Long? = null,
    // "photo" | "video" | "voice_note"
    val type: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,        // unix ms
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    val title: String? = null,
    val transcription: String? = null,
    // "exif" | "track_interpolation" | "checkin" | "manual"
    @ColumnInfo(name = "association_method") val associationMethod: String? = null,
    @ColumnInfo(name = "distance_to_poi_m") val distanceToPoiMeters: Double? = null
)
