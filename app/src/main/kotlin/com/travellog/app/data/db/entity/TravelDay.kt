package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "travel_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class TravelDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                          // YYYY-MM-DD
    val title: String? = null,
    @ColumnInfo(name = "gpx_track_path") val gpxTrackPath: String? = null,
    @ColumnInfo(name = "gpx_poi_path") val gpxPoiPath: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,   // unix ms
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "total_distance_m") val totalDistanceMeters: Double = 0.0,
    @ColumnInfo(name = "exported_pdf_path") val exportedPdfPath: String? = null,
    val notes: String? = null
)
