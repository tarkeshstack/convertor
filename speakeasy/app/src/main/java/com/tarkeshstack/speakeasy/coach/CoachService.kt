package com.tarkeshstack.speakeasy.coach

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.tarkeshstack.speakeasy.model.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SYSTEM_PROMPT = """
You are a warm, encouraging spoken-English coach — like a patient human teacher giving a
student quick live feedback right after they spoke a sentence aloud.

You will be given what the student said, the corrected sentence, and a list of grammar
issues already detected by a grammar checker. Using that, respond exactly like a teacher
speaking directly to the student:

1. One short, specific compliment about something they did right (not generic praise).
2. A plain-language explanation of the main thing(s) to fix — explain it the way you'd
   explain it out loud to a learner, not with grammar jargon.
3. The natural way a native speaker would actually say the sentence.
4. One quick, encouraging tip for next time.

Keep it warm, concise (4-6 sentences total), and conversational. Do not use bullet points,
headers, numbering, or any markdown formatting — plain spoken prose only, since this will
be read aloud by text-to-speech. If there were no grammar issues, skip straight to praising
what was said well and offer one tip to make it sound even more natural.
"""

/** Generates natural-language, teacher-style coaching commentary via the Claude API,
 *  grounded in the grammar issues [com.tarkeshstack.speakeasy.grammar.GrammarService]
 *  already found. Requires the user's own Anthropic API key (see SettingsRepository) —
 *  this is an optional layer on top of the rule-based feedback, not a replacement. */
class CoachService {

    suspend fun coach(apiKey: String, analysis: AnalysisResult): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

                val issuesSummary = if (analysis.issues.isEmpty()) {
                    "No grammar issues were detected."
                } else {
                    analysis.issues.joinToString("\n") { issue ->
                        "- ${issue.category.label}: \"${issue.original}\" -> \"${issue.suggestion}\" (${issue.message})"
                    }
                }

                val userPrompt = """
                    The student said: "${analysis.original}"

                    Corrected version: "${analysis.corrected}"

                    Issues detected:
                    $issuesSummary
                """.trimIndent()

                val params = MessageCreateParams.builder()
                    .model("claude-opus-5")
                    .maxTokens(1024L)
                    .system(SYSTEM_PROMPT.trim())
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                    .addUserMessage(userPrompt)
                    .build()

                val response = client.messages().create(params)
                val text = response.content()
                    .mapNotNull { block -> block.text().orElse(null) }
                    .joinToString(" ") { it.text() }
                    .trim()

                if (text.isBlank()) {
                    Result.failure(IllegalStateException("Coach returned an empty response"))
                } else {
                    Result.success(text)
                }
            } catch (e: UnauthorizedException) {
                Result.failure(Exception("Check your Anthropic API key in Settings.", e))
            } catch (e: RateLimitException) {
                Result.failure(Exception("Coach is a bit busy right now — try again in a moment.", e))
            } catch (e: AnthropicIoException) {
                Result.failure(Exception("Couldn't reach the coach — check your internet connection.", e))
            } catch (e: AnthropicServiceException) {
                Result.failure(Exception("Coaching is unavailable right now.", e))
            } catch (e: Exception) {
                Result.failure(Exception("Coaching is unavailable right now.", e))
            }
        }
}
