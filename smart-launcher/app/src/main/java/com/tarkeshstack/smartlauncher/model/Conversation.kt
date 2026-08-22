package com.tarkeshstack.smartlauncher.model

/** One line of the running transcript: what you said/typed, or what the app did in response. */
data class ConversationEntry(
    val text: String,
    val isUser: Boolean,
)

/** A confirmation to speak aloud; [shouldRelisten] is true only when the turn that produced
 *  it was voice-initiated, so typing never triggers the mic to reopen on its own. */
data class SpeechRequest(
    val text: String,
    val shouldRelisten: Boolean,
)
