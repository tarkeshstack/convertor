package com.tarkeshstack.smartlauncher.model

/** One line of the running transcript: what you said/typed, or what the app did in response. */
data class ConversationEntry(
    val text: String,
    val isUser: Boolean,
)

/** A confirmation to speak aloud. Only ever queued for a voice-initiated turn — typing
 *  never triggers a spoken reply — so the mic always reopens once it's done speaking. */
data class SpeechRequest(val text: String)
