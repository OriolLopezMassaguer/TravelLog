package com.travellog.app.data.export

import android.content.Context
import android.graphics.*
import android.util.Base64
import com.travellog.app.data.db.entity.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val SNAPSHOT_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Singleton
class MapImageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Marker(val latitude: Double, val longitude: Double, val type: MarkerType)
    enum class MarkerType { CHECK_IN, PHOTO, VIDEO, VOICE_NOTE }

    suspend fun render(
        trackPoints: List<TrackPoint>,
        markers: List<Marker>,
        labels: Map<MarkerType, String> = defaultLabels,
        widthPx: Int = 800,
        heightPx: Int = 400,
    ): String? {
        val sampledTrack = sampleTrack(trackPoints, maxPoints = 600)

        val allLats = (sampledTrack.map { it.latitude } + markers.map { it.latitude }).filter { it != 0.0 }
        val allLons = (sampledTrack.map { it.longitude } + markers.map { it.longitude }).filter { it != 0.0 }
        if (allLats.isEmpty() || allLons.isEmpty()) return null

        val minLat = allLats.min(); val maxLat = allLats.max()
        val minLon = allLons.min(); val maxLon = allLons.max()

        val latPad  = max(maxLat - minLat, 0.002) * 0.25
        val lonPad  = max(maxLon - minLon, 0.002) * 0.25
        val bMinLat = minLat - latPad; val bMaxLat = maxLat + latPad
        val bMinLon = minLon - lonPad; val bMaxLon = maxLon + lonPad
        val bounds  = LatLngBounds.Builder()
            .include(LatLng(bMaxLat, bMaxLon))
            .include(LatLng(bMinLat, bMinLon))
            .build()

        return try {
            renderWithSnapshot(bounds, sampledTrack, markers, labels, widthPx, heightPx)
        } catch (_: Exception) {
            // Fallback: plain canvas (no tiles) when snapshot fails (e.g. offline)
            renderPlain(bMinLat, bMaxLat, bMinLon, bMaxLon, sampledTrack, markers, labels, widthPx, heightPx)
        }
    }

    // ── MapLibre snapshot renderer ────────────────────────────────────────────

    private suspend fun renderWithSnapshot(
        bounds: LatLngBounds,
        track: List<TrackPoint>,
        markers: List<Marker>,
        labels: Map<MarkerType, String>,
        widthPx: Int,
        heightPx: Int,
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            MapLibre.getInstance(context)
            val opts = MapSnapshotter.Options(widthPx, heightPx)
                .withStyleBuilder(Style.Builder().fromUri(SNAPSHOT_STYLE))
                .withRegion(bounds)
                .withPixelRatio(1f)

            val snapshotter = MapSnapshotter(context, opts)
            snapshotter.start(
                { snapshot ->
                    try {
                        val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        drawTrackAndMarkers(Canvas(bitmap), track, markers, labels) { latLng ->
                            snapshot.pixelForLatLng(latLng)
                        }
                        val result = encodeToBase64(bitmap)
                        bitmap.recycle()
                        if (cont.isActive) cont.resumeWith(Result.success(result))
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                    }
                },
                { error ->
                    if (cont.isActive) cont.resumeWith(Result.failure(Exception(error ?: "snapshot failed")))
                }
            )
            cont.invokeOnCancellation { _: Throwable? -> snapshotter.cancel() }
        }
    }

    // ── Plain canvas fallback (no network required) ───────────────────────────

    private fun renderPlain(
        bMinLat: Double, bMaxLat: Double,
        bMinLon: Double, bMaxLon: Double,
        track: List<TrackPoint>,
        markers: List<Marker>,
        labels: Map<MarkerType, String>,
        widthPx: Int,
        heightPx: Int,
    ): String {
        val bLatSpan = bMaxLat - bMinLat
        val bLonSpan = bMaxLon - bMinLon

        fun toScreen(latLng: LatLng) = PointF(
            ((latLng.longitude - bMinLon) / bLonSpan * widthPx).toFloat(),
            ((bMaxLat - latLng.latitude) / bLatSpan * heightPx).toFloat()
        )

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#E8E8E8"))

        drawTrackAndMarkers(canvas, track, markers, labels, ::toScreen)

        return encodeToBase64(bitmap).also { bitmap.recycle() }
    }

    // ── Shared drawing logic ──────────────────────────────────────────────────

    private fun drawTrackAndMarkers(
        canvas: Canvas,
        track: List<TrackPoint>,
        markers: List<Marker>,
        labels: Map<MarkerType, String>,
        project: (LatLng) -> PointF,
    ) {
        if (track.size >= 2) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1565C0")
                strokeWidth = 4f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            val first = project(LatLng(track[0].latitude, track[0].longitude))
            path.moveTo(first.x, first.y)
            for (i in 1 until track.size) {
                val pt = project(LatLng(track[i].latitude, track[i].longitude))
                path.lineTo(pt.x, pt.y)
            }
            canvas.drawPath(path, linePaint)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0D47A1")
                style = Paint.Style.FILL
            }
            val s = project(LatLng(track.first().latitude, track.first().longitude))
            val e = project(LatLng(track.last().latitude, track.last().longitude))
            canvas.drawCircle(s.x, s.y, 6f, dotPaint)
            canvas.drawCircle(e.x, e.y, 6f, dotPaint)
        }

        val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f
        }
        markers.forEach { m ->
            if (m.latitude == 0.0 && m.longitude == 0.0) return@forEach
            val pt = project(LatLng(m.latitude, m.longitude))
            fillPaint.color = markerColor(m.type)
            canvas.drawCircle(pt.x, pt.y, 8f, fillPaint)
            canvas.drawCircle(pt.x, pt.y, 8f, borderPaint)
        }

        drawLegend(canvas, canvas.width, canvas.height, labels, markers)
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
            textSize = 22f; color = Color.parseColor("#333333")
        }
        val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 1.5f
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF"); style = Paint.Style.FILL
        }

        val items = MarkerType.entries.filter { it in presentTypes }
        val lineH = 26f; val dotR = 6f; val padH = 10f; val padV = 6f
        val boxW  = 130f
        val boxH  = items.size * lineH + padV * 2
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

    // ── Utilities ─────────────────────────────────────────────────────────────

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

    private fun encodeToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
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
