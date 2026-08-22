package com.tarkeshstack.smartlauncher.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions
import java.util.UUID

private const val PLACEHOLDER = "REPLACE_ME"

@Composable
fun CommandManagerScreen(
    commands: List<CustomCommand>,
    allApps: List<AppInfo>,
    pendingCapturedLink: CapturedLink?,
    onConsumeCapturedLink: () -> Unit,
    onAdd: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
    onBrowseForLink: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            AddCommandForm(
                allApps = allApps,
                pendingCapturedLink = pendingCapturedLink,
                onConsumeCapturedLink = onConsumeCapturedLink,
                onAdd = onAdd,
                onBrowseForLink = onBrowseForLink,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (commands.isEmpty()) {
                Text(
                    "No custom commands yet. Add one above — e.g. trigger phrase " +
                        "\"check weather\" that opens your weather app.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn {
                    items(commands, key = { it.id }) { command ->
                        CommandRow(command = command, onDelete = { onDelete(command.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: CustomCommand, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(command.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "\"${command.phrase}\" → " + when (command.kind) {
                    CustomCommandKind.OPEN_APP -> "opens ${command.packageName}"
                    CustomCommandKind.DEEP_LINK -> command.deepLinkUri.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete command")
        }
    }
}

@Composable
private fun AddCommandForm(
    allApps: List<AppInfo>,
    pendingCapturedLink: CapturedLink?,
    onConsumeCapturedLink: () -> Unit,
    onAdd: (CustomCommand) -> Unit,
    onBrowseForLink: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CustomCommandKind.OPEN_APP) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var deepLinkUri by remember { mutableStateOf("") }
    var deepLinkPackage by remember { mutableStateOf("") }
    var placeholderValue by remember { mutableStateOf("") }
    var appPickerOpen by remember { mutableStateOf(false) }
    var targetAppPickerOpen by remember { mutableStateOf(false) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var justCaptured by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    // A link captured from another app's Share sheet, or from the in-app browser,
    // lands here pre-filled.
    LaunchedEffect(pendingCapturedLink) {
        val captured = pendingCapturedLink ?: return@LaunchedEffect
        kind = CustomCommandKind.DEEP_LINK
        deepLinkUri = captured.uri
        deepLinkPackage = captured.sourcePackage.orEmpty()
        placeholderValue = ""
        justCaptured = true
        onConsumeCapturedLink()
    }

    val hasPlaceholder = deepLinkUri.contains(PLACEHOLDER)
    val resolvedDeepLinkUri = if (hasPlaceholder && placeholderValue.isNotBlank()) {
        deepLinkUri.replace(PLACEHOLDER, Uri.encode(placeholderValue.trim()))
    } else {
        deepLinkUri
    }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text("Add a command", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (justCaptured) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    "Captured a link — give it a trigger phrase and save.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = phrase,
            onValueChange = { phrase = it },
            label = { Text("Trigger phrase (what you'll type or say)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = kind == CustomCommandKind.OPEN_APP,
                onClick = { kind = CustomCommandKind.OPEN_APP },
                label = { Text("Open an app") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = kind == CustomCommandKind.DEEP_LINK,
                onClick = { kind = CustomCommandKind.DEEP_LINK },
                label = { Text("Deep link / URI") },
            )
        }
        Spacer(Modifier.height(8.dp))

        when (kind) {
            CustomCommandKind.OPEN_APP -> {
                OutlinedButton(
                    onClick = { appPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedApp?.label ?: "Search for an app…")
                }
                if (appPickerOpen) {
                    AppPickerDialog(
                        apps = allApps,
                        onDismiss = { appPickerOpen = false },
                        onSelect = { app ->
                            selectedApp = app
                            if (label.isBlank()) label = app.label
                            appPickerOpen = false
                        },
                    )
                }
            }
            CustomCommandKind.DEEP_LINK -> {
                Text(
                    "Not sure of the link? Three ways to find one, no app-share needed:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                        if (clip.isNotBlank()) {
                            val match = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""").find(clip)
                            deepLinkUri = match?.value ?: clip
                            placeholderValue = ""
                        }
                    },
                ) {
                    Text("Paste from clipboard")
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { suggestionsExpanded = true },
                    ) {
                        Text("Suggestions")
                    }
                    DropdownMenu(
                        expanded = suggestionsExpanded,
                        onDismissRequest = { suggestionsExpanded = false },
                    ) {
                        DeepLinkSuggestions.all.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text("${suggestion.appLabel} — ${suggestion.description}") },
                                onClick = {
                                    deepLinkUri = suggestion.uriTemplate
                                    deepLinkPackage = suggestion.packageName.orEmpty()
                                    placeholderValue = ""
                                    suggestionsExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBrowseForLink,
                ) {
                    Text("Browse for a link — works even without an app's Share option")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deepLinkUri,
                    onValueChange = {
                        deepLinkUri = it
                        placeholderValue = ""
                    },
                    label = { Text("Deep link URI (e.g. myapp://screen)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (hasPlaceholder) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = placeholderValue,
                        onValueChange = { placeholderValue = it },
                        label = { Text("Value to fill in (replaces $PLACEHOLDER in the link)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        if (placeholderValue.isNotBlank()) {
                            "Will save as: $resolvedDeepLinkUri"
                        } else {
                            "Type a value above — you never need to edit the link text itself."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = deepLinkPackage,
                        onValueChange = { deepLinkPackage = it },
                        label = { Text("Target app package (optional)") },
                        placeholder = { Text("e.g. com.example.app") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { targetAppPickerOpen = true }) {
                        Icon(Icons.Filled.Apps, contentDescription = "Search for an app")
                    }
                }
                if (targetAppPickerOpen) {
                    AppPickerDialog(
                        apps = allApps,
                        title = "Restrict to which app?",
                        onDismiss = { targetAppPickerOpen = false },
                        onSelect = { app ->
                            deepLinkPackage = app.packageName
                            targetAppPickerOpen = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val canSave = phrase.isNotBlank() && label.isNotBlank() && when (kind) {
            CustomCommandKind.OPEN_APP -> selectedApp != null
            CustomCommandKind.DEEP_LINK -> deepLinkUri.isNotBlank() && (!hasPlaceholder || placeholderValue.isNotBlank())
        }

        Button(
            onClick = {
                onAdd(
                    CustomCommand(
                        id = UUID.randomUUID().toString(),
                        phrase = phrase.trim(),
                        label = label.trim(),
                        kind = kind,
                        packageName = when (kind) {
                            CustomCommandKind.OPEN_APP -> selectedApp?.packageName
                            CustomCommandKind.DEEP_LINK -> deepLinkPackage.trim().ifBlank { null }
                        },
                        deepLinkUri = if (kind == CustomCommandKind.DEEP_LINK) resolvedDeepLinkUri.trim() else null,
                    ),
                )
                phrase = ""
                label = ""
                selectedApp = null
                deepLinkUri = ""
                deepLinkPackage = ""
                placeholderValue = ""
                justCaptured = false
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save command")
        }
    }
}
