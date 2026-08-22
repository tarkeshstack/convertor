package com.tarkeshstack.smartlauncher.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's system SpeechRecognizer for one-shot "listen,
 * transcribe, hand back the text" voice input. Recognition is handled entirely by
 * the system's speech service (the same one behind Google's voice typing) — this
 * app never records, stores, or uploads audio itself.
 */
class VoiceInputController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    /** Session ended with nothing usable — silence, timeout, or no recognizable speech.
     *  Distinct from [onError]: this is the routine case, not a user-facing failure. */
    private val onNoSpeech: () -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition isn't available on this device")
            return
        }
        stopListening()

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                onListeningChanged(false)
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onNoSpeech()
                } else {
                    onError("Didn't catch that — try again")
                }
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text) else onNoSpeech()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        newRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() = stopListening()
}
