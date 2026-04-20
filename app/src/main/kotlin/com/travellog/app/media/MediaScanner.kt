package com.travellog.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceMediaItem(
    val id: Long,
    val uri: Uri,
    val filePath: String,
    val dateTakenMs: Long,
    val type: String,           // "photo" | "video"
    val durationSeconds: Int?
)

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Returns all photos and videos whose DATE_TAKEN falls within [startMs]..[endMs]. */
    suspend fun scanRange(startMs: Long, endMs: Long): List<DeviceMediaItem> =
        withContext(Dispatchers.IO) {
            buildList {
                addAll(queryImages(startMs, endMs))
                addAll(queryVideos(startMs, endMs))
            }.sortedBy { it.dateTakenMs }
        }

    // ── Images ────────────────────────────────────────────────────────────────

    private fun queryImages(startMs: Long, endMs: Long): List<DeviceMediaItem> {
        val results    = mutableListOf<DeviceMediaItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val selection     = "${MediaStore.Images.Media.DATE_TAKEN} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(startMs.toString(), endMs.toString())

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val date = cursor.getLong(dateCol)
                val uri  = ContentUris.withAppendedId(collection, id)
                results += DeviceMediaItem(id, uri, path, date, "photo", null)
            }
        }
        return results
    }

    // ── Videos ────────────────────────────────────────────────────────────────

    private fun queryVideos(startMs: Long, endMs: Long): List<DeviceMediaItem> {
        val results    = mutableListOf<DeviceMediaItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DURATION,
        )
        val selection     = "${MediaStore.Video.Media.DATE_TAKEN} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(startMs.toString(), endMs.toString())

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Video.Media.DATE_TAKEN} ASC"
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val path     = cursor.getString(dataCol) ?: continue
                val date     = cursor.getLong(dateCol)
                val durationMs = cursor.getLong(durationCol)
                val uri      = ContentUris.withAppendedId(collection, id)
                results += DeviceMediaItem(
                    id, uri, path, date, "video",
                    (durationMs / 1_000).toInt().coerceAtLeast(1)
                )
            }
        }
        return results
    }
}
