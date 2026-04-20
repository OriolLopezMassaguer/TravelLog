package com.travellog.app.data.export

import android.graphics.*
import android.util.Base64
import com.travellog.app.data.db.entity.TrackPoint
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class MapImageRenderer @Inject constructor() {

    data class Marker(
        val latitude: Double,
        val longitude: Double,
        val type: MarkerType,
    )

    enum class MarkerType { CHECK_IN, PHOTO, VIDEO, VOICE_NOTE }

    fun render(
        trackPoints: List<TrackPoint>,
        markers: List<Marker>,
        labels: Map<MarkerType, String> = defaultLabels,
        widthPx: Int = 800,
        heightPx: Int = 320,
    ): String? {
        val sampledTrack = sampleTrack(trackPoints, maxPoints = 600)

        val allLats = (sampledTrack.map { it.latitude } + markers.map { it.latitude })
            .filter { it != 0.0 }
        val allLons = (sampledTrack.map { it.longitude } + markers.map { it.longitude })
            .filter { it != 0.0 }

        if (allLats.isEmpty() || allLons.isEmpty()) return null

        val minLat = allLats.min()
        val maxLat = allLats.max()
        val minLon = allLons.min()
        val maxLon = allLons.max()

        val latPad = max(maxLat - minLat, 0.001) * 0.15
        val lonPad = max(maxLon - minLon, 0.001) * 0.15
        val bMinLat = minLat - latPad; val bMaxLat = maxLat + latPad
        val bMinLon = minLon - lonPad; val bMaxLon = maxLon + lonPad
        val bLatSpan = bMaxLat - bMinLat
        val bLonSpan = bMaxLon - bMinLon

        fun toX(lon: Double) = ((lon - bMinLon) / bLonSpan * widthPx).toFloat()
        fun toY(lat: Double) = ((bMaxLat - lat) / bLatSpan * heightPx).toFloat()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F0F0F0"))

        // Track polyline
        if (sampledTrack.size >= 2) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1565C0")
                strokeWidth = 3.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(toX(sampledTrack[0].longitude), toY(sampledTrack[0].latitude))
                for (i in 1 until sampledTrack.size) {
                    lineTo(toX(sampledTrack[i].longitude), toY(sampledTrack[i].latitude))
                }
            }
            canvas.drawPath(path, paint)

            // Start / end dots
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0D47A1")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(toX(sampledTrack.first().longitude), toY(sampledTrack.first().latitude), 5f, dotPaint)
            canvas.drawCircle(toX(sampledTrack.last().longitude), toY(sampledTrack.last().latitude), 5f, dotPaint)
        }

        // Markers
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2f
        }
        markers.forEach { m ->
            if (m.latitude == 0.0 && m.longitude == 0.0) return@forEach
            fillPaint.color = markerColor(m.type)
            val x = toX(m.longitude); val y = toY(m.latitude)
            canvas.drawCircle(x, y, 7f, fillPaint)
            canvas.drawCircle(x, y, 7f, borderPaint)
        }

        // Legend
        drawLegend(canvas, widthPx, heightPx, labels, markers)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
        bitmap.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun drawLegend(
        canvas: Canvas,
        width: Int,
        height: Int,
        labels: Map<MarkerType, String>,
        markers: List<Marker>,
    ) {
        val presentTypes = markers.map { it.type }.toSet()
        if (presentTypes.isEmpty()) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = Color.parseColor("#333333")
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 1.5f
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            style = Paint.Style.FILL
        }

        val items = MarkerType.entries.filter { it in presentTypes }
        val lineH = 26f; val dotR = 6f; val padH = 10f; val padV = 6f

        val boxW = 130f
        val boxH = items.size * lineH + padV * 2
        val bx = padH; val by = height - boxH - padH

        canvas.drawRoundRect(bx, by, bx + boxW, by + boxH, 6f, 6f, bgPaint)

        items.forEachIndexed { i, type ->
            val cx = bx + padH + dotR
            val cy = by + padV + dotR + i * lineH
            fillPaint.color = markerColor(type)
            canvas.drawCircle(cx, cy, dotR, fillPaint)
            canvas.drawCircle(cx, cy, dotR, borderPaint)
            canvas.drawText(labels[type] ?: type.name, cx + dotR + 6f, cy + textPaint.textSize * 0.36f, textPaint)
        }
    }

    private fun markerColor(type: MarkerType): Int = when (type) {
        MarkerType.CHECK_IN   -> Color.parseColor("#00C853")
        MarkerType.PHOTO      -> Color.parseColor("#00B0FF")
        MarkerType.VIDEO      -> Color.parseColor("#AA00FF")
        MarkerType.VOICE_NOTE -> Color.parseColor("#FF6D00")
    }

    private fun sampleTrack(points: List<TrackPoint>, maxPoints: Int): List<TrackPoint> {
        if (points.size <= maxPoints) return points
        val step = points.size / maxPoints
        return points.filterIndexed { i, _ -> i % step == 0 }
    }

    companion object {
        private val defaultLabels = mapOf(
            MarkerType.CHECK_IN   to "Check-in",
            MarkerType.PHOTO      to "Photo",
            MarkerType.VIDEO      to "Video",
            MarkerType.VOICE_NOTE to "Voice note",
        )
    }
}
