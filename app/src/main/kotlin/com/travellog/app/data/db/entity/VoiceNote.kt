package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = TravelDay::class,
            parentColumns = ["id"],
            childColumns = ["day_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["media_item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PointOfInterest::class,
            parentColumns = ["id"],
            childColumns = ["poi_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("day_id"), Index("media_item_id"), Index("poi_id")]
)
data class VoiceNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_item_id") val mediaItemId: Long,
    @ColumnInfo(name = "day_id") val dayId: Long,
    @ColumnInfo(name = "poi_id") val poiId: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val heading: Float? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,         // unix ms
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "file_path") val filePath: String,
    // JSON array of POI IDs fetched at recording time
    @ColumnInfo(name = "nearby_pois_snapshot") val nearbyPoisSnapshot: String? = null,
    val transcription: String? = null
)
