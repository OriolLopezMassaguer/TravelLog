package com.travellog.app.data.gpx

import com.travellog.app.data.export.DayReport
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpxExportBuilder @Inject constructor() {

    /**
     * Builds a single, unified GPX 1.1 string containing:
     * 1. Waypoints (<wpt>) for all visited POIs.
     * 2. Waypoints (<wpt>) for all voice notes (if they have coordinates).
     * 3. A track (<trk>) with all recorded track points.
     */
    fun build(report: DayReport): String = buildInternal(report, includeWaypoints = true, includeTrack = true)

    /**
     * Builds a GPX string containing only the track points.
     */
    fun buildTrackOnly(report: DayReport): String = buildInternal(report, includeWaypoints = false, includeTrack = true)

    /**
     * Builds a GPX string containing only waypoints (POIs and voice notes).
     */
    fun buildPoisOnly(report: DayReport): String = buildInternal(report, includeWaypoints = true, includeTrack = false)

    private fun buildInternal(
        report: DayReport,
        includeWaypoints: Boolean,
        includeTrack: Boolean
    ): String = buildString {
        val date = report.day.date
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="TravelLog" """)
        appendLine("""    xmlns="http://www.topografix.com/GPX/1/1" """)
        appendLine("""    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """)
        appendLine("""    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")

        appendLine("  <metadata>")
        appendLine("    <name>TravelLog $date</name>")
        appendLine("    <time>${date}T00:00:00Z</time>")
        appendLine("  </metadata>")

        if (includeWaypoints) {
            // 1. Waypoints for POIs
            report.checkedInPois.forEach { poi ->
                appendLine("  <wpt lat=\"${poi.latitude}\" lon=\"${poi.longitude}\">")
                appendLine("    <name>${poi.name.escapeXml()}</name>")
                appendLine("    <type>${poi.category}</type>")
                poi.checkedInAt?.let {
                    appendLine("    <time>${Instant.ofEpochMilli(it)}</time>")
                }
                appendLine("  </wpt>")
            }

            // 2. Waypoints for Voice Notes
            report.voiceNotes.forEach { vn ->
                if (vn.latitude != 0.0 || vn.longitude != 0.0) {
                    appendLine("  <wpt lat=\"${vn.latitude}\" lon=\"${vn.longitude}\">")
                    appendLine("    <name>Voice Note</name>")
                    val desc = vn.transcription?.takeIf { it.isNotBlank() } ?: "Voice recording"
                    appendLine("    <desc>${desc.escapeXml()}</desc>")
                    appendLine("    <time>${Instant.ofEpochMilli(vn.recordedAt)}</time>")
                    appendLine("  </wpt>")
                }
            }
        }

        // 3. Track
        if (includeTrack && report.trackPoints.isNotEmpty()) {
            appendLine("  <trk>")
            appendLine("    <name>$date</name>")
            appendLine("    <trkseg>")
            report.trackPoints.forEach { p ->
                val isoTime = Instant.ofEpochMilli(p.recordedAt).toString()
                appendLine("      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
                appendLine("        <ele>${p.altitude}</ele>")
                appendLine("        <time>$isoTime</time>")
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
        }

        append("</gpx>")
    }

    private fun String.escapeXml() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
