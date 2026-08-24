package com.tarkeshstack.speakeasy.model

enum class IssueCategory(val label: String) {
    Preposition("Preposition"),
    Tense("Verb tense"),
    Conjunction("Conjunction"),
    Article("Article"),
    Agreement("Subject-verb agreement"),
    Punctuation("Punctuation / pause"),
    Spelling("Spelling"),
    WordChoice("Word choice"),
    Grammar("Grammar"),
}

data class GrammarIssue(
    val id: String,
    /** Offset and length into [AnalysisResult.base] — the text these were computed
     *  against — so the UI can render the mistake struck through in place. */
    val offset: Int,
    val length: Int,
    val original: String,
    val suggestion: String,
    val message: String,
    val category: IssueCategory,
)

data class AnalysisResult(
    val original: String,
    /** Filler-word-stripped transcript that [issues]' offsets are relative to. */
    val base: String,
    val corrected: String,
    val simplified: String?,
    val issues: List<GrammarIssue>,
    val offline: Boolean,
)

data class ConversationEntry(
    val id: String,
    val timestamp: Long,
    val original: String,
    val corrected: String,
    val simplified: String?,
    val issueCount: Int,
)

/** A one-shot request for the activity to speak [text] aloud. [id] makes each request
 *  distinct even if the text repeats, so it always re-triggers playback. */
data class SpeechRequest(val id: Long, val text: String)

enum class PracticeStatus { Idle, Listening, Analyzing, Result, PermissionDenied, Error }

enum class Tab { Practice, History }
