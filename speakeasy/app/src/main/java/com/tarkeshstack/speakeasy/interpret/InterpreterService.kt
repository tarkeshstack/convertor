package com.tarkeshstack.speakeasy.interpret

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.tarkeshstack.speakeasy.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val SYSTEM_PROMPT = """
You are a real-time speech interpreter. You will be given text spoken by a user and a
target language to translate it into. Respond with ONLY a single JSON object — no
markdown code fences, no commentary before or after — in exactly this shape:

{"detectedLanguage": "<the language the input text is actually written in, as a plain
English name such as \"Hindi\" or \"Spanish\">", "translation": "<a natural, fluent
translation of the input into the target language>"}

The translation must read like something a native speaker would actually say, not a
stiff word-for-word conversion. If the input is already in the target language, return it
unchanged (only lightly cleaned up) as the translation.
"""

/** Detects the language of a spoken transcript and translates it into a target language,
 *  via the Claude API. Requires the user's own Anthropic API key (see
 *  SettingsRepository) — this app has no server of its own, so every user calls Claude
 *  directly with their own key. */
class InterpreterService {

    data class Translation(val detectedLanguage: Language, val translatedText: String)

    /** [sourceHint] is the language the user explicitly picked to speak in, or null if
     *  they left it on auto-detect — either way, Claude still returns its own
     *  [Translation.detectedLanguage] so the UI can show what was actually understood. */
    suspend fun translate(
        apiKey: String,
        text: String,
        sourceHint: Language?,
        targetLanguage: Language,
    ): Result<Translation> = withContext(Dispatchers.IO) {
        try {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

            val sourceHintLine = if (sourceHint != null) {
                "The user selected \"${sourceHint.displayName}\" as their spoken language " +
                    "(use this unless the text clearly doesn't match)."
            } else {
                "The user did not specify their spoken language — detect it from the text."
            }
            val userPrompt = """
                $sourceHintLine

                Target language: ${targetLanguage.displayName}

                Text: "$text"
            """.trimIndent()

            val params = MessageCreateParams.builder()
                .model("claude-opus-5")
                .maxTokens(1024L)
                .system(SYSTEM_PROMPT.trim())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .addUserMessage(userPrompt)
                .build()

            val response = client.messages().create(params)
            val raw = response.content()
                .mapNotNull { block -> block.text().orElse(null) }
                .joinToString(" ") { it.text() }
                .trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val json = JSONObject(raw)
            val translated = json.optString("translation")
            val detectedName = json.optString("detectedLanguage")

            if (translated.isBlank()) {
                Result.failure(IllegalStateException("Translation came back empty"))
            } else {
                val detected = Language.entries.find { it.displayName.equals(detectedName, ignoreCase = true) }
                    ?: sourceHint
                    ?: targetLanguage
                Result.success(Translation(detected, translated))
            }
        } catch (e: UnauthorizedException) {
            Result.failure(Exception("Check your Anthropic API key in Settings.", e))
        } catch (e: RateLimitException) {
            Result.failure(Exception("Too many requests right now — try again in a moment.", e))
        } catch (e: AnthropicIoException) {
            Result.failure(Exception("Couldn't reach the translator — check your internet connection.", e))
        } catch (e: AnthropicServiceException) {
            Result.failure(Exception("Translation is unavailable right now.", e))
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't translate that — try again.", e))
        }
    }
}
