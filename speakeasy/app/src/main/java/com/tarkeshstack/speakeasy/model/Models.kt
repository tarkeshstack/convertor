package com.tarkeshstack.speakeasy.model

/** The languages this interpreter understands, each mapped to a BCP-47 tag used for both
 *  speech recognition and text-to-speech. */
enum class Language(val displayName: String, val bcp47: String) {
    English("English", "en-US"),
    Hindi("Hindi", "hi-IN"),
    Tamil("Tamil", "ta-IN"),
    Spanish("Spanish", "es-ES"),
    French("French", "fr-FR"),
}

data class InterpretationResult(
    val originalText: String,
    /** The language on-device detection identified the text as — the user's own pick
     *  when they chose one, or a best-effort guess from the text itself on auto. */
    val sourceLanguage: Language,
    val autoDetected: Boolean,
    val translatedText: String,
    val targetLanguage: Language,
)

data class InterpretationEntry(
    val id: String,
    val timestamp: Long,
    val originalText: String,
    val sourceLanguage: Language,
    val translatedText: String,
    val targetLanguage: Language,
)

/** A one-shot request for the activity to speak [text] aloud in [language]. [id] makes
 *  each request distinct even if the text repeats, so it always re-triggers playback. */
data class SpeechRequest(val id: Long, val text: String, val language: Language)

enum class InterpretStatus { Idle, Listening, Translating, Result, PermissionDenied, Error }

enum class Tab { Interpret, History }
