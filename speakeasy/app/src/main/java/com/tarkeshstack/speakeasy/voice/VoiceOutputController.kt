package com.tarkeshstack.speakeasy.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Reads text back to the user via Android's system text-to-speech engine, in whichever
 * language the caller asks for. Nothing here is recorded or sent anywhere.
 */
class VoiceOutputController(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(1.0f)
                ready = true
            }
        }
    }

    /** [onResult] fires with whether speech actually started — false if the engine isn't
     *  ready, the text is blank, or this device's TTS has no voice data installed for
     *  [languageTag]. Always fires, on the main thread. */
    fun speak(text: String, languageTag: String, onResult: (Boolean) -> Unit = {}) {
        val engine = tts
        if (engine == null || !ready || text.isBlank()) {
            mainHandler.post { onResult(false) }
            return
        }
        val languageResult = engine.setLanguage(Locale.forLanguageTag(languageTag))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            mainHandler.post { onResult(false) }
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post { onResult(true) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onResult(false) }
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
