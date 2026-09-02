package com.tarkeshstack.smartlauncher.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions
import java.util.UUID

private const val PLACEHOLDER = "REPLACE_ME"

@OptIn(ExperimentalMaterial3Api::class)
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
    var showAddForm by remember { mutableStateOf(false) }

    // A link captured from another app's Share sheet, or from the in-app browser, should
    // always land you in the form, however you got to this screen.
    LaunchedEffect(pendingCapturedLink) {
        if (pendingCapturedLink != null) showAddForm = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddForm = !showAddForm }) {
                        Icon(
                            if (showAddForm) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (showAddForm) "Close" else "Add a command",
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Everything lives in one scrollable list — including the add-command form — so
        // whichever field you're typing into (even the last one) scrolls up above the
        // keyboard instead of being hidden behind it.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Deep-link shortcuts only — a plain app name already opens it, no " +
                        "command needed for that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (showAddForm) {
                item {
                    AddCommandForm(
                        allApps = allApps,
                        pendingCapturedLink = pendingCapturedLink,
                        onConsumeCapturedLink = onConsumeCapturedLink,
                        onAdd = { command ->
                            onAdd(command)
                            showAddForm = false
                        },
                        onBrowseForLink = onBrowseForLink,
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (commands.isEmpty()) {
                item {
                    Text(
                        "No commands yet. Tap + above to add one — e.g. trigger phrase " +
                            "\"movie night\" that opens Netflix.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(commands, key = { it.id }) { command ->
                    CommandRow(command = command, onDelete = { onDelete(command.id) })
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var deepLinkUri by remember { mutableStateOf("") }
    var deepLinkPackage by remember { mutableStateOf("") }
    var placeholderValue by remember { mutableStateOf("") }
    var targetAppPickerOpen by remember { mutableStateOf(false) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var justCaptured by remember { mutableStateOf(false) }

    // A link captured from another app's Share sheet, or from the in-app browser,
    // lands here pre-filled.
    LaunchedEffect(pendingCapturedLink) {
        val captured = pendingCapturedLink ?: return@LaunchedEffect
        deepLinkUri = captured.uri
        deepLinkPackage = captured.sourcePackage.orEmpty()
        placeholderValue = ""
        justCaptured = true
        onConsumeCapturedLink()
    }

    // Suggestions are only useful for apps that are actually installed — no point
    // offering a Spotify search link on a phone that doesn't have Spotify.
    val installedSuggestions = remember(allApps) {
        DeepLinkSuggestions.all.filter { suggestion ->
            suggestion.packageName != null && allApps.any { it.packageName == suggestion.packageName }
        }
    }

    val hasPlaceholder = deepLinkUri.contains(PLACEHOLDER)
    val resolvedDeepLinkUri = if (hasPlaceholder && placeholderValue.isNotBlank()) {
        deepLinkUri.replace(PLACEHOLDER, Uri.encode(placeholderValue.trim()))
    } else {
        deepLinkUri
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Add a command", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (justCaptured) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Share a link in from any app — its Share button → Smart Launcher — or pick " +
                    "a popular one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (installedSuggestions.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { suggestionsExpanded = true },
                    ) {
                        Text("Popular for your apps")
                    }
                    DropdownMenu(
                        expanded = suggestionsExpanded,
                        onDismissRequest = { suggestionsExpanded = false },
                    ) {
                        installedSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text("${suggestion.appLabel} — ${suggestion.description}") },
                                onClick = {
                                    deepLinkUri = suggestion.uriTemplate
                                    deepLinkPackage = suggestion.packageName.orEmpty()
                                    placeholderValue = ""
                                    if (label.isBlank()) label = suggestion.description
                                    suggestionsExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBrowseForLink,
            ) {
                Text("Browse for a link — works even without an app's Share option")
            }
            Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(16.dp))

            val canSave = phrase.isNotBlank() && label.isNotBlank() &&
                deepLinkUri.isNotBlank() && (!hasPlaceholder || placeholderValue.isNotBlank())

            Button(
                onClick = {
                    onAdd(
                        CustomCommand(
                            id = UUID.randomUUID().toString(),
                            phrase = phrase.trim(),
                            label = label.trim(),
                            kind = CustomCommandKind.DEEP_LINK,
                            packageName = deepLinkPackage.trim().ifBlank { null },
                            deepLinkUri = resolvedDeepLinkUri.trim(),
                        ),
                    )
                    phrase = ""
                    label = ""
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
}
