package com.travellog.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlReportBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormatter  = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val timeFormatter  = DateTimeFormatter.ofPattern("HH:mm")

    fun build(report: DayReport): String {
        val day     = report.day
        val title   = day.title ?: "Day in ${day.date}"
        val dateStr = LocalDate.parse(day.date).format(dateFormatter)
        val distKm  = "%.1f km".format(day.totalDistanceMeters / 1_000.0)

        return buildString {
            append(htmlHead(title))
            append("<body>")
            append(header(title, dateStr, distKm, report))
            if (report.checkedInPois.isNotEmpty()) append(poisSection(report))
            if (!day.notes.isNullOrBlank()) append(notesSection(day.notes))
            if (report.mediaItems.any { it.type == "photo" }) append(photosSection(report))
            if (report.voiceNotes.isNotEmpty()) append(voiceNotesSection(report))
            append("</body></html>")
        }
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun header(title: String, dateStr: String, distKm: String, report: DayReport) = buildString {
        append("<h1>$title</h1>")
        append("<p class=\"subtitle\">$dateStr</p>")
        append("<div class=\"stat-row\">")
        append(stat(distKm, "Distance"))
        append(stat("${report.checkedInPois.size}", "Places visited"))
        append(stat("${report.mediaItems.count { it.type == "photo" }}", "Photos"))
        append(stat("${report.voiceNotes.size}", "Voice notes"))
        append("</div>")
    }

    private fun stat(value: String, label: String) =
        "<div class=\"stat\"><div class=\"value\">$value</div><div class=\"label\">$label</div></div>"

    private fun poisSection(report: DayReport) = buildString {
        append("<h2>Places Visited</h2><ul class=\"poi-list\">")
        report.checkedInPois.forEach { poi ->
            append("<li class=\"poi-item\">")
            append("<span class=\"poi-name\">${poi.name.escapeHtml()}</span>")
            append("<span class=\"poi-cat\">${poi.category.escapeHtml()}</span>")
            if (!poi.address.isNullOrBlank())
                append("<div class=\"poi-addr\">${poi.address.escapeHtml()}</div>")
            append("</li>")
        }
        append("</ul>")
    }

    private fun notesSection(notes: String) =
        "<h2>Notes</h2><div class=\"notes\">${notes.escapeHtml()}</div>"

    private fun photosSection(report: DayReport) = buildString {
        val photos = report.mediaItems.filter { it.type == "photo" }.take(MAX_PHOTOS)
        append("<h2>Photos</h2><div class=\"photo-grid\">")
        photos.forEach { item ->
            val b64 = encodePhoto(item.filePath)
            if (b64 != null)
                append("<img src=\"data:image/jpeg;base64,$b64\" alt=\"\">")
        }
        append("</div>")
    }

    private fun voiceNotesSection(report: DayReport) = buildString {
        append("<h2>Voice Notes</h2><ul class=\"vn-list\">")
        report.voiceNotes.forEach { vn ->
            val time = Instant.ofEpochMilli(vn.recordedAt)
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
            val dur = formatDuration(vn.durationSeconds)
            append("<li class=\"vn-item\">")
            append("<div class=\"vn-time\">$time &bull; $dur</div>")
            if (!vn.transcription.isNullOrBlank())
                append("<div class=\"vn-text\">&ldquo;${vn.transcription.escapeHtml()}&rdquo;</div>")
            append("</li>")
        }
        append("</ul>")
    }

    // ── Photo encoding ────────────────────────────────────────────────────────

    private fun encodePhoto(filePath: String): String? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp  = BitmapFactory.decodeFile(filePath, opts) ?: return null
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        bmp.recycle()
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    // ── HTML skeleton ─────────────────────────────────────────────────────────

    private fun htmlHead(title: String) = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1.0">
  <title>${title.escapeHtml()}</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: Georgia, 'Times New Roman', serif; max-width: 760px;
           margin: 0 auto; padding: 32px 24px; color: #222; line-height: 1.6; }
    h1 { font-size: 2rem; margin-bottom: 4px; }
    .subtitle { color: #777; font-size: 0.9rem; margin-bottom: 28px; }
    .stat-row { display: flex; gap: 32px; margin-bottom: 36px; }
    .stat .value { font-size: 1.6rem; font-weight: bold; }
    .stat .label { font-size: 0.7rem; color: #999; text-transform: uppercase; letter-spacing: 0.05em; }
    h2 { font-size: 1.2rem; border-bottom: 1px solid #ddd; padding-bottom: 6px;
         margin-top: 36px; margin-bottom: 14px; }
    .poi-list { list-style: none; }
    .poi-item { padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
    .poi-name { font-weight: bold; }
    .poi-cat  { color: #888; font-size: 0.8rem; margin-left: 8px; }
    .poi-addr { color: #999; font-size: 0.8rem; margin-top: 2px; }
    .notes { background: #fffbe6; border-left: 3px solid #f5c518;
             padding: 12px 16px; border-radius: 4px; font-style: italic; }
    .photo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .photo-grid img { width: 100%; aspect-ratio: 1/1; object-fit: cover; border-radius: 4px; }
    .vn-list { list-style: none; }
    .vn-item { background: #f8f8f8; border-radius: 6px; padding: 10px 14px; margin-bottom: 8px; }
    .vn-time { font-size: 0.78rem; color: #888; margin-bottom: 4px; }
    .vn-text { font-style: italic; color: #333; }
    @media print {
      body { padding: 0; max-width: 100%; }
      .photo-grid { break-inside: avoid; }
    }
  </style>
</head>
"""

    companion object {
        private const val MAX_PHOTOS = 12
    }
}
