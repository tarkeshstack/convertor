package com.tarkeshstack.speakeasy.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.tarkeshstack.speakeasy.UiState
import com.tarkeshstack.speakeasy.model.InterpretStatus
import com.tarkeshstack.speakeasy.model.InterpretationResult
import com.tarkeshstack.speakeasy.model.Language

@Composable
fun InterpreterScreen(
    state: UiState,
    onMicPress: () -> Unit,
    onCancel: () -> Unit,
    onReplay: (String, Language) -> Unit,
    onSetSourceLanguage: (Language?) -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Column {
            Text("Interpreter", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Speak, and hear it translated",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        val sourceOptions: List<Pair<String, () -> Unit>> = buildList {
            add("Auto-detect" to { onSetSourceLanguage(null) })
            Language.entries.forEach { language ->
                add(language.displayName to { onSetSourceLanguage(language) })
            }
        }
        val targetOptions: List<Pair<String, () -> Unit>> = Language.entries.map { language ->
            language.displayName to { onSetTargetLanguage(language) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LanguagePicker(
                label = "Speak in",
                selectedLabel = state.sourceLanguage?.displayName ?: "Auto-detect",
                options = sourceOptions,
                modifier = Modifier.weight(1f),
            )
            LanguagePicker(
                label = "Translate to",
                selectedLabel = state.targetLanguage.displayName,
                options = targetOptions,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            statusMessage(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.status == InterpretStatus.Listening && state.liveTranscript.isNotBlank()) {
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
            Waveform(level = state.rmsLevel, active = state.status == InterpretStatus.Listening)
            Spacer(Modifier.height(16.dp))
            MicButton(status = state.status, onPress = onMicPress)
            if (state.status == InterpretStatus.Listening) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        state.result?.let { result ->
            ResultCard(result = result, onReplay = onReplay)
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, onSelect) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(); expanded = false })
            }
        }
    }
}

private fun statusMessage(state: UiState): String = when (state.status) {
    InterpretStatus.Idle -> "Tap the mic and speak"
    InterpretStatus.Listening -> "Listening… tap again to stop"
    InterpretStatus.Translating -> "Translating…"
    InterpretStatus.Result -> "Here's your translation"
    InterpretStatus.PermissionDenied -> "Microphone permission is required to interpret speech"
    InterpretStatus.Error -> state.errorMessage ?: "Something went wrong"
}

@Composable
private fun MicButton(status: InterpretStatus, onPress: () -> Unit) {
    val listening = status == InterpretStatus.Listening
    val busy = status == InterpretStatus.Translating
    val background = when {
        listening -> MaterialTheme.colorScheme.error
        busy -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val size by animateDpAsState(if (listening) 104.dp else 96.dp, label = "micSize")

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = !busy, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
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

@Composable
private fun ResultCard(result: InterpretationResult, onReplay: (String, Language) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Label(
                if (result.autoDetected) {
                    "You said · detected ${result.sourceLanguage.displayName}"
                } else {
                    "You said (${result.sourceLanguage.displayName})"
                },
            )
            Text(
                result.originalText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Label("Translation (${result.targetLanguage.displayName})")
            Text(
                result.translatedText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onReplay(result.translatedText, result.targetLanguage) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play translation")
            }
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
