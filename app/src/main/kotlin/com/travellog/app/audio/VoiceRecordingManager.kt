package com.travellog.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RecordingResult(val file: File, val durationSeconds: Int)

@Singleton
class VoiceRecordingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Emits whenever the OS takes audio focus away or audio routing changes (e.g. BT disconnect).
    private val _interruptions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val interruptions: SharedFlow<Unit> = _interruptions.asSharedFlow()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            _interruptions.tryEmit(Unit)
        }
    }

    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        } else null
    }

    // Fired when BT headset disconnects or headphones are unplugged mid-recording.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isRecording) {
                _interruptions.tryEmit(Unit)
            }
        }
    }
    private var noisyReceiverRegistered = false

    val isRecording: Boolean get() = recorder != null

    /**
     * Starts a new recording for [date] (YYYY-MM-DD).
     * Returns the output File immediately; audio is being written to it.
     * Throws if already recording or if the microphone is unavailable.
     */
    fun startRecording(date: String): File {
        check(!isRecording) { "Already recording" }

        requestAudioFocus()
        registerNoisyReceiver()

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
        releaseAudioSession()

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
        releaseAudioSession()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (noisyReceiverRegistered) {
            try { context.unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
            noisyReceiverRegistered = false
        }
    }

    private fun releaseAudioSession() {
        unregisterNoisyReceiver()
        abandonAudioFocus()
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
