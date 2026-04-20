package com.travellog.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points_of_interest",
    foreignKeys = [ForeignKey(
        entity = TravelDay::class,
        parentColumns = ["id"],
        childColumns = ["day_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("day_id"), Index(value = ["external_id", "day_id"], unique = true)]
)
data class PointOfInterest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_id") val dayId: Long,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val address: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "opening_hours") val openingHours: String? = null,
    val rating: Float? = null,
    val website: String? = null,
    val phone: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "checked_in") val checkedIn: Boolean = false,
    @ColumnInfo(name = "checked_in_at") val checkedInAt: Long? = null,
    // "overpass" | "foursquare" | "manual"
    val source: String = "overpass",
    @ColumnInfo(name = "raw_data") val rawData: String? = null
)
