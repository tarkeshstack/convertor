package com.tarkeshstack.smartlauncher.voice

/** Looks for "hey buddy" (tolerating a comma or extra spacing) inside transcribed speech. */
object WakePhraseDetector {
    private val pattern = Regex("""\bhey,?\s+buddy\b""", RegexOption.IGNORE_CASE)

    /** Null if the phrase wasn't heard. Otherwise, whatever text followed it — blank if the
     *  utterance was just the wake phrase on its own, e.g. "hey buddy, open uber" -> "open uber". */
    fun matchRemainder(text: String): String? {
        val match = pattern.find(text) ?: return null
        return text.substring(match.range.last + 1).trim()
    }
}
