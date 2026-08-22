package com.tarkeshstack.smartlauncher.capture

import android.content.Intent
import com.tarkeshstack.smartlauncher.model.CapturedLink

/**
 * Pulls a shareable deep link out of an ACTION_SEND intent. Most apps' "Share" (and
 * many "Copy Link") actions hand off a text/plain EXTRA_TEXT that either *is* the
 * link, or has it embedded in a sentence — this pulls out the first URI-looking
 * substring either way.
 */
object ShareIntentParser {

    private val uriPattern = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")

    fun extractDeepLink(intent: Intent, sourcePackage: String?): CapturedLink? {
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = uriPattern.find(text)
        val uri = (match?.value ?: text).trimEnd('.', ')', ']', ',', '"')
        if (uri.isBlank()) return null
        return CapturedLink(uri = uri, sourcePackage = sourcePackage)
    }
}
