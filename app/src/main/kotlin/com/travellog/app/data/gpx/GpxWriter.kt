package com.travellog.app.data.gpx

import android.content.Context
import com.travellog.app.data.db.entity.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes GPS track points incrementally to a GPX file using a "trailer" strategy:
 *
 *  - track.gpx       → header + <trkseg> + appended <trkpt> elements (no closing tags)
 *  - track.gpx.tail  → </trkseg></trk></gpx>  (kept separate so appends are safe)
 *
 * This means track.gpx is never a complete XML document mid-day, but the tail file
 * can always be concatenated to produce a valid GPX. At end of day, finalizeDay()
 * merges them into one complete file.
 *
 * The map (Phase 2) reads track points from Room DB, so GPX validity mid-day is
 * only needed for mid-day sharing/export.
 */
@Singleton
class GpxWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var trackFile: File? = null
    private var tailFile: File? = null
    private var outputStream: FileOutputStream? = null

    /** Call once at the start of each tracking day. Returns the track.gpx File. */
    fun initDay(date: String): File {
        close()
        val dir = getDayDir(date).also { it.mkdirs() }
        trackFile = File(dir, "track.gpx")
        tailFile  = File(dir, "track.gpx.tail")

        if (!trackFile!!.exists() || trackFile!!.length() == 0L) {
            trackFile!!.writeText(buildHeader(date))
            tailFile!!.writeText(GPX_TAIL)
        }

        outputStream = FileOutputStream(trackFile!!, /* append = */ true)
        return trackFile!!
    }

    /** Appends a batch of track points. Call this from the service flush. */
    fun appendPoints(points: List<TrackPoint>) {
        val stream = outputStream ?: return
        val sb = StringBuilder()
        points.forEach { sb.append(buildTrkpt(it)) }
        stream.write(sb.toString().toByteArray(Charsets.UTF_8))
        stream.flush()
    }

    /**
     * Closes the stream and merges track.gpx.tail into track.gpx,
     * producing a complete, valid GPX file.
     */
    fun finalizeDay() {
        close()
        val tail = tailFile ?: return
        val track = trackFile ?: return
        if (tail.exists()) {
            track.appendText(tail.readText())
            tail.delete()
        }
        trackFile = null
        tailFile  = null
    }

    /**
     * Returns a readable GPX for the given date, concatenating the tail if the day
     * is still in progress (i.e. tail file still exists).
     */
    fun readableGpxFile(date: String): File? {
        val dir = getDayDir(date)
        val track = File(dir, "track.gpx").takeIf { it.exists() } ?: return null
        val tail  = File(dir, "track.gpx.tail")
        if (!tail.exists()) return track  // already finalized

        // Produce a temp merged file for reading
        val merged = File(dir, "track.gpx.tmp")
        merged.outputStream().use { out ->
            track.inputStream().copyTo(out)
            tail.inputStream().copyTo(out)
        }
        return merged
    }

    /**
     * Called at app startup to finalize any day that was interrupted mid-tracking
     * (i.e. a `.tail` file still exists alongside `track.gpx`).
     */
    fun recoverOrphanedDays(dates: List<String>) {
        for (date in dates) {
            val dir   = getDayDir(date)
            val track = File(dir, "track.gpx")
            val tail  = File(dir, "track.gpx.tail")
            if (track.exists() && tail.exists()) {
                track.appendText(tail.readText())
                tail.delete()
            }
        }
    }

    fun getDayDir(date: String): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "TravelLog/days/$date")
    }

    private fun close() {
        outputStream?.flush()
        outputStream?.close()
        outputStream = null
    }

    // ── XML builders ────────────────────────────────────────────────────────

    private fun buildHeader(date: String) = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TravelLog"
    xmlns="http://www.topografix.com/GPX/1/1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>TravelLog $date</name>
    <time>${date}T00:00:00Z</time>
  </metadata>
  <trk>
    <name>$date</name>
    <trkseg>
"""

    private fun buildTrkpt(p: TrackPoint): String {
        val isoTime = Instant.ofEpochMilli(p.recordedAt).toString()
        return "      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">\n" +
               "        <ele>${p.altitude}</ele>\n" +
               "        <time>$isoTime</time>\n" +
               "        <extensions>\n" +
               "          <speed>${p.speed}</speed>\n" +
               "          <heading>${p.heading}</heading>\n" +
               "          <accuracy>${p.accuracy}</accuracy>\n" +
               "        </extensions>\n" +
               "      </trkpt>\n"
    }

    companion object {
        private const val GPX_TAIL = "    </trkseg>\n  </trk>\n</gpx>"
    }
}
