package com.tarkeshstack.speakeasy.coach

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.tarkeshstack.speakeasy.model.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SYSTEM_PROMPT = """
You are a spoken-English coach giving a student a short end-of-session progress report,
after they practiced several sentences aloud. You'll get each sentence they said, its
correction, and how many issues were found. Speak directly to the student like a teacher
wrapping up a session:

1. Open by naming one real strength you noticed across the session.
2. Name the one or two patterns that came up more than once, in plain language.
3. Say whether they improved as the session went on, if that's visible from the pattern
   of issues.
4. Close with one concrete thing to practice next time.

Keep it warm and concise (5-7 sentences), plain spoken prose only (no bullets, headers,
or markdown), since this may be read aloud by text-to-speech.
"""

/**
 * End-of-session progress report — aggregates every turn practiced since the session
 * started (or was last cleared), instead of grading a single sentence in isolation.
 *
 * [summarize] requires the user's own Anthropic API key, same as [CoachService]. Everyone
 * else — including anyone who hasn't added a key — still gets [localSummary], a fully
 * offline report computed from the same data. The API version is a richer optional
 * upgrade, not a requirement to use the feature at all.
 */
class SessionSummaryService {

    suspend fun summarize(apiKey: String, entries: List<ConversationEntry>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

                val turnsSummary = entries.reversed().mapIndexed { index, entry ->
                    "${index + 1}. Said: \"${entry.original}\" -> Corrected: \"${entry.corrected}\" " +
                        "(${entry.issueCount} issue(s)${
                            if (entry.issueCategories.isNotEmpty()) ": ${entry.issueCategories.joinToString(", ")}" else ""
                        })"
                }.joinToString("\n")

                val userPrompt = """
                    Session of ${entries.size} practiced sentences, in order:
                    $turnsSummary
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
                    Result.failure(IllegalStateException("Summary came back empty"))
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
                Result.failure(Exception("Summary is unavailable right now.", e))
            } catch (e: Exception) {
                Result.failure(Exception("Summary is unavailable right now.", e))
            }
        }

    companion object {
        /** Zero network calls, no API key required — every user gets a session summary;
         *  [summarize] above is just a richer opt-in upgrade on top of this. */
        fun localSummary(entries: List<ConversationEntry>): String {
            if (entries.isEmpty()) return "Practice a few sentences first, then ask for a summary."

            // entries arrive newest-first (history order) — chronological is clearer for a trend.
            val chronological = entries.reversed()
            val totalIssues = chronological.sumOf { it.issueCount }
            val avg = totalIssues.toDouble() / chronological.size
            val cleanTurns = chronological.count { it.issueCount == 0 }

            val topCategory = chronological
                .flatMap { it.issueCategories }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }

            val trendLine = if (chronological.size >= 4) {
                val half = chronological.size / 2
                val firstAvg = chronological.subList(0, half).sumOf { it.issueCount }.toDouble() / half
                val secondAvg = chronological.subList(half, chronological.size).sumOf { it.issueCount }
                    .toDouble() / (chronological.size - half)
                when {
                    secondAvg < firstAvg - 0.01 ->
                        "Issues per sentence dropped from %.1f to %.1f as you went — you were improving as you practiced.".format(firstAvg, secondAvg)
                    secondAvg > firstAvg + 0.01 ->
                        "Issues crept up a bit later in the session (%.1f to %.1f per sentence) — that's normal as sentences get more ambitious.".format(firstAvg, secondAvg)
                    else -> "Your issue rate stayed steady across the session."
                }
            } else null

            val opener = when {
                cleanTurns == chronological.size ->
                    "Every one of your ${chronological.size} sentences came out clean this session — great work."
                cleanTurns > chronological.size / 2 ->
                    "Solid session: $cleanTurns of your ${chronological.size} sentences had no issues at all."
                else ->
                    "You practiced ${chronological.size} sentences this session, averaging %.1f issue(s) each.".format(avg)
            }

            val patternLine = if (topCategory != null && topCategory.value > 1) {
                " The pattern to watch is ${topCategory.key.lowercase()} — it came up ${topCategory.value} times."
            } else ""

            val closer = if (totalIssues > 0) {
                " Try replaying your own recordings to hear how you actually sounded, and keep an eye on that pattern next time."
            } else {
                " Try pushing to longer, more complex sentences next time to keep challenging yourself."
            }

            return buildString {
                append(opener)
                if (trendLine != null) append(" ").append(trendLine)
                append(patternLine)
                append(closer)
            }
        }
    }
}
