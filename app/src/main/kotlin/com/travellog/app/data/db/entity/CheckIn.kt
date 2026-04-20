package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = PointOfInterest::class,
            parentColumns = ["id"],
            childColumns = ["poi_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TravelDay::class,
            parentColumns = ["id"],
            childColumns = ["day_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("poi_id"), Index("day_id")]
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "poi_id") val poiId: Long,
    @ColumnInfo(name = "day_id") val dayId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    @ColumnInfo(name = "checked_in_at") val checkedInAt: Long,   // unix ms
    @ColumnInfo(name = "user_note") val userNote: String? = null
)
