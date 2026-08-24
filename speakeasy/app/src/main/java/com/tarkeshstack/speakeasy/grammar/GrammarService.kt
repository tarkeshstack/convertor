package com.tarkeshstack.speakeasy.grammar

import com.tarkeshstack.speakeasy.model.AnalysisResult
import com.tarkeshstack.speakeasy.model.GrammarIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val LANGUAGETOOL_ENDPOINT = "https://api.languagetool.org/v2/check"
private const val TIMEOUT_MS = 8000

private val FILLER_WORDS = listOf(
    "um", "umm", "uh", "uhh", "erm", "hmm",
    "you know", "i mean", "sort of", "kind of",
)

// Wordy phrase -> simpler replacement, applied case-insensitively.
private val SIMPLIFY_PHRASES: List<Pair<Regex, String>> = listOf(
    Regex("\\bin order to\\b", RegexOption.IGNORE_CASE) to "to",
    Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE) to "because",
    Regex("\\bat this point in time\\b", RegexOption.IGNORE_CASE) to "now",
    Regex("\\bin the event that\\b", RegexOption.IGNORE_CASE) to "if",
    Regex("\\ba large number of\\b", RegexOption.IGNORE_CASE) to "many",
    Regex("\\bfor the purpose of\\b", RegexOption.IGNORE_CASE) to "to",
    Regex("\\butilize\\b", RegexOption.IGNORE_CASE) to "use",
    Regex("\\butilized\\b", RegexOption.IGNORE_CASE) to "used",
    Regex("\\bcommence\\b", RegexOption.IGNORE_CASE) to "start",
    Regex("\\bterminate\\b", RegexOption.IGNORE_CASE) to "end",
    Regex("\\bsubsequently\\b", RegexOption.IGNORE_CASE) to "later",
    Regex("\\bnumerous\\b", RegexOption.IGNORE_CASE) to "many",
    Regex("\\bassistance\\b", RegexOption.IGNORE_CASE) to "help",
    Regex("\\bpurchase\\b", RegexOption.IGNORE_CASE) to "buy",
    Regex("\\bregarding\\b", RegexOption.IGNORE_CASE) to "about",
    Regex("\\bnevertheless\\b", RegexOption.IGNORE_CASE) to "still",
)

/** Checks a spoken transcript for grammar issues via the free LanguageTool API, with a
 *  local-only fallback (filler-word cleanup + wordy-phrase simplification) when offline. */
class GrammarService {

    suspend fun analyze(rawTranscript: String): AnalysisResult = withContext(Dispatchers.IO) {
        val withoutFillers = stripFillers(rawTranscript)
        val base = withoutFillers.ifBlank { rawTranscript.trim() }

        val remote = checkWithLanguageTool(base)

        val corrected = capitalizeAndPunctuate(remote?.first ?: base)
        val issues = remote?.second ?: emptyList()
        val simplifiedCandidate = capitalizeAndPunctuate(simplifyText(corrected))
        val simplified = if (simplifiedCandidate != corrected) simplifiedCandidate else null

        AnalysisResult(
            original = rawTranscript.trim(),
            corrected = corrected,
            simplified = simplified,
            issues = issues,
            offline = remote == null,
        )
    }

    private fun stripFillers(text: String): String {
        var cleaned = text
        for (filler in FILLER_WORDS) {
            val pattern = Regex(
                "\\b${filler.replace(" ", "\\s+")}\\b[,]?\\s*",
                RegexOption.IGNORE_CASE,
            )
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.replace(Regex("\\s+"), " ").replace(Regex("\\s+([.,!?])"), "$1").trim()
    }

    private fun capitalizeAndPunctuate(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result
        result = result.replaceFirstChar { it.uppercase() }
        if (!Regex("[.!?]$").containsMatchIn(result)) result += "."
        return result
    }

    private fun simplifyText(text: String): String {
        var simplified = text
        for ((pattern, replacement) in SIMPLIFY_PHRASES) {
            simplified = simplified.replace(pattern, replacement)
        }
        val words = simplified.split(Regex("\\s+"))
        if (words.size > 28) {
            val midpoint = words.size / 2
            for (i in midpoint until words.size - 3) {
                if (Regex("^(and|but|so)$", RegexOption.IGNORE_CASE).matches(words[i])) {
                    val before = words.subList(0, i).joinToString(" ").trimEnd(',')
                    val after = words.subList(i + 1, words.size).joinToString(" ")
                    simplified = "${capitalizeAndPunctuate(before)} ${capitalizeAndPunctuate(after)}"
                    break
                }
            }
        }
        return simplified
    }

    /** Returns (correctedText, issues), or null if the API couldn't be reached. */
    private fun checkWithLanguageTool(text: String): Pair<String, List<GrammarIssue>>? {
        return try {
            val body = "text=${URLEncoder.encode(text, "UTF-8")}&language=en-US"
            val connection = (URL(LANGUAGETOOL_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            connection.outputStream.use { OutputStreamWriter(it).apply { write(body); flush() } }

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }

            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            connection.disconnect()

            val matches = JSONObject(responseText).getJSONArray("matches")
            val matchList = (0 until matches.length()).map { matches.getJSONObject(it) }
                .sortedByDescending { it.getInt("offset") }

            var corrected = text
            val issues = mutableListOf<GrammarIssue>()
            for (match in matchList) {
                val offset = match.getInt("offset")
                val length = match.getInt("length")
                val original = text.substring(offset, offset + length)
                val replacements = match.getJSONArray("replacements")
                val bestReplacement = if (replacements.length() > 0) {
                    replacements.getJSONObject(0).optString("value", original)
                } else null

                issues.add(
                    GrammarIssue(
                        id = "$offset-$length",
                        original = original,
                        suggestion = bestReplacement ?: original,
                        message = match.optString("shortMessage").ifBlank { match.optString("message") },
                    ),
                )

                if (bestReplacement != null) {
                    corrected = corrected.substring(0, offset) + bestReplacement +
                        corrected.substring(offset + length)
                }
            }

            corrected to issues.reversed()
        } catch (e: Exception) {
            null
        }
    }
}
