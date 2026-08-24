package com.tarkeshstack.speakeasy.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tarkeshstack.speakeasy.UiState
import com.tarkeshstack.speakeasy.model.AnalysisResult
import com.tarkeshstack.speakeasy.model.GrammarIssue
import com.tarkeshstack.speakeasy.model.PracticeStatus

@Composable
fun PracticeScreen(
    state: UiState,
    onMicPress: () -> Unit,
    onCancel: () -> Unit,
    onReplay: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveApiKey: (String) -> Unit,
) {
    if (state.showSettings) {
        ApiKeyDialog(currentKey = state.apiKey, onSave = onSaveApiKey, onDismiss = onCloseSettings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("SpeakEasy", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Practice speaking English out loud",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            statusMessage(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.status == PracticeStatus.Listening && state.liveTranscript.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.liveTranscript,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Waveform(level = state.rmsLevel, active = state.status == PracticeStatus.Listening)
            Spacer(Modifier.height(16.dp))
            MicButton(status = state.status, onPress = onMicPress)
            if (state.status == PracticeStatus.Listening) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        state.result?.let { result ->
            FeedbackCard(result = result, onReplay = onReplay)
            Spacer(Modifier.height(16.dp))
            CoachSection(
                hasApiKey = state.apiKey != null,
                loading = state.coachLoading,
                feedback = state.coachFeedback,
                error = state.coachError,
                onOpenSettings = onOpenSettings,
                onReplay = onReplay,
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(currentKey: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(currentKey.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI coach") },
        text = {
            Column {
                Text(
                    "Add your own Anthropic API key to unlock personalized coaching " +
                        "feedback after each practice turn — praise, plain-language " +
                        "explanations, and the natural way to say it. Stored only on " +
                        "this device, never uploaded anywhere else. Get a key at " +
                        "console.anthropic.com.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Anthropic API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CoachSection(
    hasApiKey: Boolean,
    loading: Boolean,
    feedback: String?,
    error: String?,
    onOpenSettings: () -> Unit,
    onReplay: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Label("Your coach says")
            }

            when {
                !hasApiKey -> {
                    Text(
                        "Add an Anthropic API key to get personalized coaching — praise, plain-language explanations, and the natural way to say it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text("Add API key") }
                }
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Thinking about your sentence…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error != null -> {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                feedback != null -> {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onReplay(feedback) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hear your coach")
                    }
                }
            }
        }
    }
}

private fun statusMessage(state: UiState): String = when (state.status) {
    PracticeStatus.Idle -> "Tap the mic and say a sentence in English"
    PracticeStatus.Listening -> "Listening… tap again to stop"
    PracticeStatus.Analyzing -> "Checking your grammar…"
    PracticeStatus.Result -> "Here's your feedback"
    PracticeStatus.PermissionDenied -> "Microphone permission is required to practice speaking"
    PracticeStatus.Error -> state.errorMessage ?: "Something went wrong"
}

@Composable
private fun MicButton(status: PracticeStatus, onPress: () -> Unit) {
    val listening = status == PracticeStatus.Listening
    val analyzing = status == PracticeStatus.Analyzing
    val background = when {
        listening -> MaterialTheme.colorScheme.error
        analyzing -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val size by animateDpAsState(if (listening) 104.dp else 96.dp, label = "micSize")

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = !analyzing, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        when {
            analyzing -> CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            listening -> Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(36.dp))
            else -> Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
        }
    }
}

private val BAR_WEIGHTS = listOf(0.5f, 0.8f, 1f, 0.75f, 0.55f)

@Composable
private fun Waveform(level: Float, active: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp),
    ) {
        BAR_WEIGHTS.forEach { weight ->
            val target = if (active) (6 + level * weight * 42).dp else 6.dp
            val height by animateDpAsState(target, animationSpec = tween(140), label = "bar")
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

/** Renders [base] with each mistake struck through in red and its fix shown right
 *  after in bold, e.g. "I ~~goes~~ **go** to school." Issues with no real replacement
 *  (suggestion identical to the original) are just flagged, not crossed out. */
@Composable
private fun highlightedMistakes(base: String, issues: List<GrammarIssue>): AnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    val fixColor = MaterialTheme.colorScheme.secondary
    return buildAnnotatedString {
        var cursor = 0
        for (issue in issues.sortedBy { it.offset }) {
            if (issue.offset < cursor || issue.offset + issue.length > base.length) continue
            append(base.substring(cursor, issue.offset))
            val hasRealFix = issue.suggestion.isNotBlank() &&
                !issue.suggestion.equals(issue.original, ignoreCase = true)
            withStyle(
                SpanStyle(
                    color = errorColor,
                    textDecoration = if (hasRealFix) TextDecoration.LineThrough else TextDecoration.Underline,
                ),
            ) {
                append(issue.original)
            }
            if (hasRealFix) {
                append(" ")
                withStyle(SpanStyle(color = fixColor, fontWeight = FontWeight.Bold)) {
                    append(issue.suggestion)
                }
            }
            cursor = issue.offset + issue.length
        }
        append(base.substring(cursor))
    }
}

@Composable
private fun FeedbackCard(result: AnalysisResult, onReplay: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(contentPadding = PaddingValues(20.dp), modifier = Modifier.height(420.dp)) {
            item {
                Label("You said")
                Text(
                    result.original,
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.offline) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Offline mode: showing basic suggestions only. Connect to the internet for full grammar checks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Divider()

                val issueCount = result.issues.size
                if (issueCount > 0) {
                    Label("What to fix · $issueCount issue${if (issueCount > 1) "s" else ""}")
                    Text(
                        highlightedMistakes(result.base, result.issues),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Label(if (issueCount > 0) "Corrected sentence" else "Looks good!")
                Text(
                    result.corrected,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onReplay(result.corrected) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play corrected sentence")
                }
            }

            result.issues.groupBy { it.category }.forEach { (category, issuesInCategory) ->
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(issuesInCategory) { issue -> IssueRow(issue) }
            }

            val simplifiedText = result.simplified
            if (simplifiedText != null) {
                item {
                    Divider()
                    Label("Simpler way to say it")
                    Text(
                        simplifiedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onReplay(simplifiedText) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play simplified sentence")
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: GrammarIssue) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row {
                Text(
                    issue.original,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.LineThrough,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(6.dp))
                Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    issue.suggestion,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                issue.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 12.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}
