package com.travellog.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android's SpeechRecognizer for live, local transcription during voice recording.
 *
 * Must be started/stopped on the main thread. Restarts automatically after each utterance
 * so it covers long recordings without an API call or external dependency.
 *
 * Returns accumulated text on [stop]; empty string if recognition is unavailable.
 */
@Singleton
class AndroidSpeechService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accumulated = StringBuilder()
    private var recognizer: SpeechRecognizer? = null

    @Volatile private var active = false

    /** Start continuous recognition. Call from main thread. */
    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        active = true
        accumulated.clear()
        postStartListening()
    }

    /** Stop recognition and return accumulated text. Call from main thread. */
    fun stop(): String {
        active = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        return accumulated.toString().trim()
    }

    /** Cancel without returning text. */
    fun cancel() {
        active = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        accumulated.clear()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun postStartListening() {
        if (!active) return
        mainHandler.post { startListening() }
    }

    private fun startListening() {
        if (!active) return
        recognizer?.destroy()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep listening for up to 60 s before auto-stopping on silence
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
            // Prefer on-device model when available
            putExtra("android.speech.extra.PREFER_OFFLINE", true)
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partial: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    if (!text.isNullOrBlank()) {
                        if (accumulated.isNotEmpty()) accumulated.append(" ")
                        accumulated.append(text)
                    }
                    // Restart to capture the next utterance
                    if (active) mainHandler.postDelayed({ startListening() }, 200L)
                }

                override fun onError(error: Int) {
                    // Restart after transient errors (noise, silence timeout, etc.)
                    if (active) mainHandler.postDelayed({ startListening() }, 500L)
                }
            })
            sr.startListening(intent)
        }
    }
}
