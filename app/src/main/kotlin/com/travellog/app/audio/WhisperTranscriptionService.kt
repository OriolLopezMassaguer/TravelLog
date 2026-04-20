package com.travellog.app.audio

import com.travellog.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionService @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun transcribe(audioFile: File): String? {
        val apiKey = settingsRepository.getOpenAiApiKey().takeIf { it.isNotBlank() } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val boundary = "TravelLogBoundary${System.currentTimeMillis()}"
                val conn = (URL("https://api.openai.com/v1/audio/transcriptions").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 30_000
                    readTimeout    = 60_000
                    doOutput = true
                }
                DataOutputStream(conn.outputStream).use { out ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                    out.writeBytes("whisper-1\r\n")
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes(
                        "Content-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n"
                    )
                    out.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                    out.write(audioFile.readBytes())
                    out.writeBytes("\r\n--$boundary--\r\n")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    // Parse "text" field from JSON without adding a JSON library
                    Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                        .find(body)?.groupValues?.get(1)
                        ?.replace("\\n", "\n")
                        ?.replace("\\\"", "\"")
                        ?.replace("\\\\", "\\")
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
