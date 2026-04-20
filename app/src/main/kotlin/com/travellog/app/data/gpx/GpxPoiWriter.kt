package com.travellog.app.data.gpx

import android.content.Context
import com.travellog.app.data.db.entity.PointOfInterest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes POIs as GPX <wpt> elements (one file per day).
 * The file is rewritten completely on each update because the number of POIs
 * per day is small (~20–50) and check-in state changes need to be reflected.
 */
@Singleton
class GpxPoiWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Overwrites pois.gpx for [date] with [pois] and returns the file. */
    fun writeAll(date: String, pois: List<PointOfInterest>): File {
        val dir  = getDayDir(date).also { it.mkdirs() }
        val file = File(dir, "pois.gpx")
        file.writeText(buildGpx(pois))
        return file
    }

    fun getDayDir(date: String): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "TravelLog/days/$date")
    }

    // ── XML builders ─────────────────────────────────────────────────────────

    private fun buildGpx(pois: List<PointOfInterest>) = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="TravelLog" """ +
            """xmlns="http://www.topografix.com/GPX/1/1" """ +
            """xmlns:tl="http://travellog.app/gpx/ext/1.0">"""
        )
        pois.forEach { append(buildWpt(it)) }
        append("</gpx>")
    }

    private fun buildWpt(p: PointOfInterest) = buildString {
        appendLine("""  <wpt lat="${p.latitude}" lon="${p.longitude}">""")
        appendLine("""    <name>${p.name.escapeXml()}</name>""")
        p.description?.let { appendLine("""    <desc>${it.escapeXml()}</desc>""") }
        appendLine("""    <type>${p.category}</type>""")
        appendLine("""    <extensions>""")
        appendLine("""      <tl:category>${p.category}</tl:category>""")
        appendLine("""      <tl:checkedIn>${p.checkedIn}</tl:checkedIn>""")
        p.checkedInAt?.let {
            appendLine("""      <tl:checkedInAt>${Instant.ofEpochMilli(it)}</tl:checkedInAt>""")
        }
        p.externalId?.let { appendLine("""      <tl:externalId>${it}</tl:externalId>""") }
        appendLine("""      <tl:source>${p.source}</tl:source>""")
        appendLine("""    </extensions>""")
        appendLine("""  </wpt>""")
    }

    private fun String.escapeXml() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
