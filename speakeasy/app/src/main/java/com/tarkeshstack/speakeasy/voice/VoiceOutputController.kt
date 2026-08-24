package com.tarkeshstack.speakeasy.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Reads corrected/simplified sentences back to the user via Android's system
 * text-to-speech engine. Nothing here is recorded or sent anywhere.
 */
class VoiceOutputController(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                // A language-learner reading this back needs it noticeably slower than
                // natural speech (1.0), not just a shade under it.
                tts?.setSpeechRate(0.72f)
                ready = true
            }
        }
    }

    /** [onDone] always fires on the main thread, whether speech succeeded, failed, or
     *  the engine isn't ready — callers can safely act on it. */
    fun speak(text: String, onDone: () -> Unit = {}) {
        val engine = tts
        if (engine == null || !ready || text.isBlank()) {
            mainHandler.post(onDone)
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post(onDone)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post(onDone)
            }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
