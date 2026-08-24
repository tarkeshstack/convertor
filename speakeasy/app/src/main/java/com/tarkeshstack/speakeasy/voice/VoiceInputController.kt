package com.tarkeshstack.speakeasy.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's system SpeechRecognizer for "listen, show live
 * partial text, transcribe" voice input. Recognition is handled entirely by the
 * system's speech service (the same one behind Google's voice typing) — this
 * controller never records, stores, or uploads audio itself.
 */
class VoiceInputController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    /** Raw input level, roughly -2 (silent) to 10 (loud), emitted several times a second
     *  while listening. Drives the waveform animation.
     *
     *  Named differently from RecognitionListener's own `onRmsChanged` on purpose: giving
     *  this the same name previously caused the interface override below to call itself
     *  instead of this property, an infinite recursion that crashed with a
     *  StackOverflowError the instant listening started. */
    private val onVolumeChanged: (Float) -> Unit,
    private val onRecognitionError: (String) -> Unit,
    /** Session ended with nothing usable — silence, timeout, or no recognizable speech.
     *  Distinct from [onRecognitionError]: this is the routine case, not a user-facing failure. */
    private val onNoSpeech: () -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null

    /** [languageTag] is a BCP-47 tag (e.g. "hi-IN") to recognize speech in a specific
     *  language, or null to let the system use its own default recognition language —
     *  the "auto-detect" mode, since the on-device recognizer has no public API to
     *  detect the spoken language itself before transcribing. */
    fun startListening(languageTag: String? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onRecognitionError("Speech recognition isn't available on this device")
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

            override fun onRmsChanged(rmsdB: Float) {
                onVolumeChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                onListeningChanged(false)
                onVolumeChanged(-2f)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        onNoSpeech()
                    // These two are almost always us restarting the recognizer faster than the
                    // previous session finished tearing down, not the user doing anything wrong.
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        onNoSpeech()
                    else -> onRecognitionError("Didn't catch that — try again")
                }
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                onVolumeChanged(-2f)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text) else onNoSpeech()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartialResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (languageTag != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            // Matches smart-launcher's proven-stable config: partial results off.
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
        // A session torn down mid-listen never gets its normal onEndOfSpeech/onError
        // callback, so without this the UI's "listening" flag can get stuck true forever.
        if (recognizer != null) {
            onListeningChanged(false)
            onVolumeChanged(-2f)
        }
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() = stopListening()
}
