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
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        onNoSpeech()
                    // These two are almost always us restarting the recognizer faster than the
                    // previous session finished tearing down (e.g. a quick relisten), not the
                    // user doing anything wrong — showing "Didn't catch that" for them was both
                    // misleading and, since it fired instead of a real retry, made listening
                    // sessions end far sooner than intended. Treat them the same as no-speech.
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        onNoSpeech()
                    else -> onError("Didn't catch that — try again")
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
            // The system default cuts off after roughly a second of silence, which reads as
            // "the mic isn't waiting" the moment you pause mid-sentence. These (undocumented
            // but widely honored, including by Google's own recognizer) extras give it more
            // patience before deciding you're done or that nothing was said.
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000)
            putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000)
        }
        newRecognizer.startListening(intent)
    }

    fun stopListening() {
        // A session torn down mid-listen (e.g. the activity pausing to launch another app)
        // never gets its normal onEndOfSpeech/onError callback, so without this the UI's
        // "listening" flag can get stuck true forever and block any future restart.
        if (recognizer != null) onListeningChanged(false)
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() = stopListening()
}
