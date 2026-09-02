package com.tarkeshstack.smartlauncher.model

/** A confirmation to speak aloud. Only ever queued for a voice-initiated turn — typing
 *  never triggers a spoken reply — so the mic always reopens once it's done speaking. */
data class SpeechRequest(val text: String)
