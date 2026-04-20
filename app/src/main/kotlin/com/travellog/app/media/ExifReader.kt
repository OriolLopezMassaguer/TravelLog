package com.travellog.app.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExifReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ExifData(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
    )

    /**
     * Reads GPS coordinates from the EXIF metadata of an image URI.
     * Returns null if no GPS data is present or the file cannot be read.
     */
    fun read(uri: Uri): ExifData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif     = ExifInterface(stream)
                val latLong  = exif.latLong ?: return null
                val altitude = exif.getAltitude(0.0)
                ExifData(
                    latitude  = latLong[0],
                    longitude = latLong[1],
                    altitude  = altitude
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
