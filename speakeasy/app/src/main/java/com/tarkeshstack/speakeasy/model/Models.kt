package com.tarkeshstack.speakeasy.model

data class GrammarIssue(
    val id: String,
    val original: String,
    val suggestion: String,
    val message: String,
)

data class AnalysisResult(
    val original: String,
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
