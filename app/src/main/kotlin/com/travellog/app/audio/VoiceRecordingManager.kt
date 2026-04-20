package com.travellog.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingResult(val file: File, val durationSeconds: Int)

@Singleton
class VoiceRecordingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    val isRecording: Boolean get() = recorder != null

    /**
     * Starts a new recording for [date] (YYYY-MM-DD).
     * Returns the output File immediately; audio is being written to it.
     * Throws if already recording or if the microphone is unavailable.
     */
    fun startRecording(date: String): File {
        check(!isRecording) { "Already recording" }

        val dir  = getVoiceDir(date).also { it.mkdirs() }
        val ts   = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        val file = File(dir, "VN_${date.replace("-", "")}_$ts.m4a")

        val rec = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder    = rec
        currentFile = file
        startTimeMs = System.currentTimeMillis()
        return file
    }

    /**
     * Stops the current recording and returns its result.
     * Returns null if nothing was being recorded or if the stop failed.
     */
    fun stopRecording(): RecordingResult? {
        val rec  = recorder  ?: return null
        val file = currentFile ?: return null
        val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1_000)
            .toInt()
            .coerceAtLeast(1)

        recorder    = null
        currentFile = null

        return try {
            rec.stop()
            rec.release()
            RecordingResult(file, durationSec)
        } catch (e: Exception) {
            rec.release()
            file.delete()
            null
        }
    }

    /** Aborts recording and deletes the partial file. */
    fun cancelRecording() {
        recorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        recorder = null
        currentFile?.delete()
        currentFile = null
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else
            @Suppress("DEPRECATION") MediaRecorder()

    private fun getVoiceDir(date: String): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "TravelLog/days/$date/voice")
    }
}
