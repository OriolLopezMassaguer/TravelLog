package com.travellog.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.travellog.app.R
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
    @ApplicationContext private val context: Context,
    private val mapRenderer: MapImageRenderer,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun build(report: DayReport): String {
        val day     = report.day
        val title   = day.title ?: context.getString(R.string.report_default_day_title, day.date)
        val dateStr = LocalDate.parse(day.date).format(dateFormatter)
        val distKm  = "%.1f km".format(day.totalDistanceMeters / 1_000.0)

        val timeline  = buildTimeline(report)
        val mapBase64 = buildMapImage(report)

        return buildString {
            append(htmlHead(title))
            append("<body>")
            append(headerSection(title, dateStr, distKm, report))
            if (mapBase64 != null) append(mapSection(mapBase64))
            if (!day.notes.isNullOrBlank()) append(notesSection(day.notes))
            if (timeline.isNotEmpty()) append(timelineSection(timeline))
            append("</body></html>")
        }
    }

    // ── Timeline builder ──────────────────────────────────────────────────────

    private fun buildTimeline(report: DayReport): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        report.checkedInPois.forEach { poi ->
            val ts = poi.checkedInAt ?: return@forEach
            events += TimelineEvent.CheckInEvent(
                recordedAt   = ts,
                latitude     = poi.latitude,
                longitude    = poi.longitude,
                poiName      = poi.name,
                poiCategory  = poi.category,
                poiAddress   = poi.address,
            )
        }

        report.mediaItems.filter { it.type == "photo" }.forEach { item ->
            events += TimelineEvent.PhotoEvent(
                recordedAt = item.recordedAt,
                latitude   = item.latitude,
                longitude  = item.longitude,
                filePath   = item.filePath,
            )
        }

        report.mediaItems.filter { it.type == "video" }.forEach { item ->
            events += TimelineEvent.VideoEvent(
                recordedAt      = item.recordedAt,
                latitude        = item.latitude,
                longitude       = item.longitude,
                durationSeconds = item.durationSeconds,
            )
        }

        report.voiceNotes.forEach { vn ->
            events += TimelineEvent.VoiceNoteEvent(
                recordedAt      = vn.recordedAt,
                latitude        = vn.latitude.takeIf { it != 0.0 },
                longitude       = vn.longitude.takeIf { it != 0.0 },
                durationSeconds = vn.durationSeconds,
                transcription   = vn.transcription,
                filePath        = vn.filePath,
            )
        }

        return events.sortedBy { it.recordedAt }
    }

    // ── Map image builder ─────────────────────────────────────────────────────

    private suspend fun buildMapImage(report: DayReport): String? {
        val markers = mutableListOf<MapImageRenderer.Marker>()

        report.checkedInPois.forEach { poi ->
            markers += MapImageRenderer.Marker(poi.latitude, poi.longitude, MapImageRenderer.MarkerType.CHECK_IN)
        }
        report.mediaItems.filter { it.type == "photo" }.forEach { item ->
            val lat = item.latitude ?: return@forEach
            val lon = item.longitude ?: return@forEach
            markers += MapImageRenderer.Marker(lat, lon, MapImageRenderer.MarkerType.PHOTO)
        }
        report.mediaItems.filter { it.type == "video" }.forEach { item ->
            val lat = item.latitude ?: return@forEach
            val lon = item.longitude ?: return@forEach
            markers += MapImageRenderer.Marker(lat, lon, MapImageRenderer.MarkerType.VIDEO)
        }
        report.voiceNotes.forEach { vn ->
            if (vn.latitude == 0.0 && vn.longitude == 0.0) return@forEach
            markers += MapImageRenderer.Marker(vn.latitude, vn.longitude, MapImageRenderer.MarkerType.VOICE_NOTE)
        }

        val labels = mapOf(
            MapImageRenderer.MarkerType.CHECK_IN   to context.getString(R.string.report_type_checkin),
            MapImageRenderer.MarkerType.PHOTO      to context.getString(R.string.report_type_photo),
            MapImageRenderer.MarkerType.VIDEO      to context.getString(R.string.report_type_video),
            MapImageRenderer.MarkerType.VOICE_NOTE to context.getString(R.string.report_type_voice_note),
        )

        return mapRenderer.render(report.trackPoints, markers, labels)
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun headerSection(title: String, dateStr: String, distKm: String, report: DayReport) = buildString {
        val photoCount = report.mediaItems.count { it.type == "photo" }
        val videoCount = report.mediaItems.count { it.type == "video" }
        append("<h1>${title.escapeHtml()}</h1>")
        append("<p class=\"subtitle\">$dateStr</p>")
        append("<div class=\"stat-row\">")
        append(stat(distKm,                                context.getString(R.string.report_stat_distance)))
        append(stat("${report.checkedInPois.size}",        context.getString(R.string.report_stat_places)))
        append(stat("$photoCount",                         context.getString(R.string.report_stat_photos)))
        if (videoCount > 0) append(stat("$videoCount",    context.getString(R.string.report_stat_videos)))
        append(stat("${report.voiceNotes.size}",           context.getString(R.string.report_stat_voice_notes)))
        append("</div>")
    }

    private fun stat(value: String, label: String) =
        "<div class=\"stat\"><div class=\"value\">$value</div><div class=\"label\">${label.escapeHtml()}</div></div>"

    private fun mapSection(base64: String) = buildString {
        append("<div class=\"map-wrap\">")
        append("<img class=\"map-img\" src=\"data:image/png;base64,$base64\" alt=\"\">")
        append("</div>")
    }

    private fun notesSection(notes: String) =
        "<h2>${context.getString(R.string.report_section_notes).escapeHtml()}</h2>" +
        "<div class=\"notes\">${notes.escapeHtml()}</div>"

    private fun timelineSection(events: List<TimelineEvent>) = buildString {
        append("<h2>${context.getString(R.string.report_section_timeline).escapeHtml()}</h2>")
        append("<div class=\"timeline\">")
        events.forEach { event -> append(timelineItem(event)) }
        append("</div>")
    }

    private fun timelineItem(event: TimelineEvent): String {
        val time = Instant.ofEpochMilli(event.recordedAt)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

        val coordHtml = coordHtml(event.latitude, event.longitude)

        return when (event) {
            is TimelineEvent.CheckInEvent -> buildString {
                append("<div class=\"tl-item tl-checkin\">")
                append("<div class=\"tl-time\">$time</div>")
                append("<div class=\"tl-body\">")
                append("<span class=\"tl-type tl-tag-checkin\">${context.getString(R.string.report_type_checkin).escapeHtml()}</span>")
                append("<div class=\"tl-title\">${event.poiName.escapeHtml()}</div>")
                append("<div class=\"tl-meta\">${event.poiCategory.escapeHtml()}</div>")
                if (!event.poiAddress.isNullOrBlank())
                    append("<div class=\"tl-meta\">${event.poiAddress.escapeHtml()}</div>")
                if (coordHtml != null) append(coordHtml)
                append("</div></div>")
            }

            is TimelineEvent.PhotoEvent -> buildString {
                val b64 = encodePhoto(event.filePath)
                append("<div class=\"tl-item tl-photo\">")
                append("<div class=\"tl-time\">$time</div>")
                append("<div class=\"tl-body\">")
                append("<span class=\"tl-type tl-tag-photo\">${context.getString(R.string.report_type_photo).escapeHtml()}</span>")
                if (b64 != null)
                    append("<img class=\"tl-thumb\" src=\"data:image/jpeg;base64,$b64\" alt=\"\">")
                if (coordHtml != null) append(coordHtml)
                append("</div></div>")
            }

            is TimelineEvent.VideoEvent -> buildString {
                append("<div class=\"tl-item tl-video\">")
                append("<div class=\"tl-time\">$time</div>")
                append("<div class=\"tl-body\">")
                append("<span class=\"tl-type tl-tag-video\">${context.getString(R.string.report_type_video).escapeHtml()}</span>")
                if (event.durationSeconds != null)
                    append("<div class=\"tl-meta\">${formatDuration(event.durationSeconds)}</div>")
                if (coordHtml != null) append(coordHtml)
                append("</div></div>")
            }

            is TimelineEvent.VoiceNoteEvent -> buildString {
                val audioB64  = encodeAudio(event.filePath)
                val audioMime = audioMimeType(event.filePath)
                append("<div class=\"tl-item tl-vn\">")
                append("<div class=\"tl-time\">$time</div>")
                append("<div class=\"tl-body\">")
                append("<span class=\"tl-type tl-tag-vn\">${context.getString(R.string.report_type_voice_note).escapeHtml()}</span>")
                append("<div class=\"tl-meta\">${formatDuration(event.durationSeconds)}</div>")
                if (audioB64 != null) {
                    append("<audio class=\"tl-audio\" controls preload=\"metadata\">")
                    append("<source src=\"data:$audioMime;base64,$audioB64\" type=\"$audioMime\">")
                    append("</audio>")
                    append("<div class=\"tl-audio-print\">&#127908;&nbsp;${formatDuration(event.durationSeconds)}</div>")
                }
                if (!event.transcription.isNullOrBlank())
                    append("<div class=\"tl-quote\">&ldquo;${event.transcription.escapeHtml()}&rdquo;</div>")
                if (coordHtml != null) append(coordHtml)
                append("</div></div>")
            }
        }
    }

    private fun coordHtml(lat: Double?, lon: Double?): String? {
        if (lat == null || lon == null) return null
        val text = context.getString(R.string.report_coord_label, lat, lon)
        return "<div class=\"tl-coord\">$text</div>"
    }

    // ── Photo encoding ────────────────────────────────────────────────────────

    private fun encodePhoto(filePath: String): String? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp  = BitmapFactory.decodeFile(filePath, opts) ?: return null
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
        bmp.recycle()
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    private fun encodeAudio(filePath: String): String? = try {
        val file = java.io.File(filePath)
        if (!file.exists() || file.length() > 5 * 1024 * 1024) null
        else Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    private fun audioMimeType(filePath: String): String = when {
        filePath.endsWith(".m4a",  ignoreCase = true) -> "audio/mp4"
        filePath.endsWith(".ogg",  ignoreCase = true) -> "audio/ogg"
        filePath.endsWith(".opus", ignoreCase = true) -> "audio/ogg; codecs=opus"
        filePath.endsWith(".3gp",  ignoreCase = true) -> "audio/3gpp"
        filePath.endsWith(".aac",  ignoreCase = true) -> "audio/aac"
        else                                          -> "audio/mpeg"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60; val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun String.escapeHtml() = replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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
    .subtitle { color: #777; font-size: 0.9rem; margin-bottom: 20px; }
    .stat-row { display: flex; gap: 28px; flex-wrap: wrap; margin-bottom: 28px; }
    .stat .value { font-size: 1.5rem; font-weight: bold; }
    .stat .label { font-size: 0.7rem; color: #999; text-transform: uppercase; letter-spacing: 0.05em; }
    h2 { font-size: 1.2rem; border-bottom: 1px solid #ddd; padding-bottom: 6px;
         margin-top: 36px; margin-bottom: 14px; }
    .map-wrap { margin-bottom: 28px; border-radius: 8px; overflow: hidden;
                border: 1px solid #ddd; }
    .map-img { width: 100%; display: block; }
    .notes { background: #fffbe6; border-left: 3px solid #f5c518;
             padding: 12px 16px; border-radius: 4px; font-style: italic; }
    /* Timeline */
    .timeline { position: relative; }
    .timeline::before { content: ""; position: absolute; left: 52px; top: 0; bottom: 0;
                        width: 2px; background: #e0e0e0; }
    .tl-item { display: flex; gap: 16px; margin-bottom: 20px; break-inside: avoid; }
    .tl-time { width: 44px; min-width: 44px; font-size: 0.78rem; color: #888;
               padding-top: 3px; text-align: right; }
    .tl-body { flex: 1; background: #fafafa; border: 1px solid #ebebeb;
               border-radius: 6px; padding: 10px 12px; }
    .tl-type { display: inline-block; font-size: 0.68rem; font-weight: bold;
               text-transform: uppercase; letter-spacing: 0.06em;
               padding: 2px 8px; border-radius: 12px; margin-bottom: 6px; }
    .tl-tag-checkin  { background: #e8f5e9; color: #2e7d32; }
    .tl-tag-photo    { background: #e3f2fd; color: #1565c0; }
    .tl-tag-video    { background: #f3e5f5; color: #6a1b9a; }
    .tl-tag-vn       { background: #fff3e0; color: #bf360c; }
    .tl-title { font-weight: bold; font-size: 0.95rem; }
    .tl-meta  { font-size: 0.8rem; color: #666; margin-top: 2px; }
    .tl-coord { font-size: 0.72rem; color: #aaa; margin-top: 4px; font-family: monospace; }
    .tl-quote { font-style: italic; color: #444; margin-top: 4px; font-size: 0.88rem; }
    .tl-thumb { max-width: 160px; max-height: 120px; object-fit: cover;
                border-radius: 4px; margin-top: 6px; display: block; }
    .tl-audio { width: 100%; margin-top: 8px; }
    .tl-audio-print { display: none; }
    @media print {
      body { padding: 0; max-width: 100%; }
      .tl-item { break-inside: avoid; }
      .timeline::before { display: none; }
      .tl-audio { display: none; }
      .tl-audio-print { display: block; color: #666; font-size: 0.8rem; margin-top: 4px; }
    }
  </style>
</head>
"""
}
