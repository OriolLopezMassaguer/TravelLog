package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = TravelDay::class,
        parentColumns = ["id"],
        childColumns = ["day_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("day_id"),
        Index(value = ["day_id", "recorded_at"])
    ]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_id") val dayId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float = 0f,
    val heading: Float = 0f,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long   // unix ms
)
