package com.travellog.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoskTranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_DIR = "vosk-model"
        private const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        private const val TARGET_SAMPLE_RATE = 16_000
    }

    private var cachedModel: Model? = null

    fun isModelReady(): Boolean {
        val dir = modelDir()
        return dir.isDirectory && dir.listFiles()?.isNotEmpty() == true
    }

    fun modelDir(): File = File(context.filesDir, MODEL_DIR)

    fun deleteModel() {
        cachedModel?.close()
        cachedModel = null
        modelDir().deleteRecursively()
    }

    /**
     * Downloads and extracts the small English Vosk model (~50 MB).
     * [onProgress] receives 0..1 as the download progresses.
     * Returns true on success.
     */
    suspend fun downloadModel(onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val zipFile = File(context.cacheDir, "vosk-model.zip")
                val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
                conn.connect()
                val total = conn.contentLengthLong
                var downloaded = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buf = ByteArray(8_192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress(downloaded.toFloat() / total)
                        }
                    }
                }

                val dest = modelDir().also { it.deleteRecursively(); it.mkdirs() }
                ZipInputStream(FileInputStream(zipFile)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        // Strip the top-level directory from zip entry paths
                        val parts = entry.name.split("/").drop(1)
                        if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                            val out = File(dest, parts.joinToString("/"))
                            if (entry.isDirectory) out.mkdirs()
                            else {
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { zip.copyTo(it) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
                zipFile.delete()
                true
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Transcribes [audioFile] (m4a/AAC) using the on-device Vosk model.
     * Decodes audio to PCM, mixes to mono, resamples to 16 kHz, then runs recognition.
     * Returns null if the model is not ready or transcription fails.
     */
    suspend fun transcribe(audioFile: File): String? = withContext(Dispatchers.IO) {
        if (!isModelReady()) return@withContext null
        try {
            val model = cachedModel ?: Model(modelDir().absolutePath).also { cachedModel = it }
            val pcm = decodeToPcm16k(audioFile) ?: return@withContext null
            Recognizer(model, TARGET_SAMPLE_RATE.toFloat()).use { rec ->
                // Feed in chunks to avoid OOM on long recordings
                val chunkSize = TARGET_SAMPLE_RATE * 2   // 1 second of 16-bit mono = 32 000 bytes
                var offset = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + chunkSize, pcm.size)
                    val chunk = pcm.copyOfRange(offset, end)
                    rec.acceptWaveForm(chunk, chunk.size)
                    offset = end
                }
                val json = rec.finalResult
                Regex(""""text"\s*:\s*"([^"]*)"""")
                    .find(json)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Audio decoding ────────────────────────────────────────────────────────

    private fun decodeToPcm16k(file: File): ShortArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = fmt
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            extractor.selectTrack(trackIndex)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBytes = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    val chunk = ByteArray(info.size)
                    buf.get(chunk)
                    pcmBytes.write(chunk)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }

            codec.stop()
            codec.release()

            val bytes = pcmBytes.toByteArray()
            // Bytes are little-endian 16-bit PCM interleaved
            var shorts = ShortArray(bytes.size / 2) { i ->
                ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
            }

            // Mix to mono
            if (channels > 1) {
                shorts = ShortArray(shorts.size / channels) { i ->
                    var sum = 0
                    for (c in 0 until channels) sum += shorts[i * channels + c]
                    (sum / channels).toShort()
                }
            }

            // Resample to 16 kHz using linear interpolation
            if (srcRate != TARGET_SAMPLE_RATE) resample(shorts, srcRate, TARGET_SAMPLE_RATE)
            else shorts
        } finally {
            extractor.release()
        }
    }

    private fun resample(pcm: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        val ratio = fromRate.toDouble() / toRate
        val outLen = (pcm.size / ratio).toInt()
        return ShortArray(outLen) { i ->
            val pos = i * ratio
            val idx = pos.toInt().coerceIn(0, pcm.size - 2)
            val frac = pos - idx
            ((pcm[idx] * (1.0 - frac) + pcm[idx + 1] * frac).toInt()).toShort()
        }
    }
}
