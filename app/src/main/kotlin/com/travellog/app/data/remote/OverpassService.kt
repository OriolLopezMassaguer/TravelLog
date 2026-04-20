package com.travellog.app.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverpassService @Inject constructor() {

    private val gson = Gson()

    /**
     * Queries Overpass for named POIs within [radiusMeters] of the given position.
     * Returns an empty list (not an exception) on network failure so callers can
     * fall back to cached data gracefully.
     */
    suspend fun queryNearby(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 500
    ): List<OverpassElement> = withContext(Dispatchers.IO) {
        try {
            val query = buildQuery(lat, lon, radiusMeters)
            val json  = httpPost(ENDPOINT, query)
            gson.fromJson(json, OverpassResponse::class.java)
                .elements
                .filter { it.name != null }   // skip unnamed elements
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Query builder ─────────────────────────────────────────────────────────

    private fun buildQuery(lat: Double, lon: Double, radius: Int): String {
        val around = "(around:$radius,$lat,$lon)"
        return """
            [out:json][timeout:25];
            (
              node["amenity"~"restaurant|cafe|bar|pub|fast_food|museum|theatre|cinema|hotel|pharmacy|bank"]$around;
              node["tourism"~"attraction|viewpoint|museum|hotel|hostel|guest_house|artwork|gallery"]$around;
              node["historic"]$around;
              node["leisure"~"park|garden|nature_reserve"]$around;
            );
            out body;
        """.trimIndent()
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun httpPost(url: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout    = 30_000
                doOutput       = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val encoded = "data=${URLEncoder.encode(body, "UTF-8")}"
            conn.outputStream.use { it.write(encoded.toByteArray()) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    }
}
