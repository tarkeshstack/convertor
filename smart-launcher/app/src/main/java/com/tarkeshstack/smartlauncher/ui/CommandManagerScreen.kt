package com.tarkeshstack.smartlauncher.ui

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import java.util.UUID

@Composable
fun CommandManagerScreen(
    commands: List<CustomCommand>,
    allApps: List<AppInfo>,
    onAdd: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text("Custom Commands", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            AddCommandForm(allApps = allApps, onAdd = onAdd)

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
private fun AddCommandForm(allApps: List<AppInfo>, onAdd: (CustomCommand) -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CustomCommandKind.OPEN_APP) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var deepLinkUri by remember { mutableStateOf("") }
    var deepLinkPackage by remember { mutableStateOf("") }
    var appPickerExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text("Add a command", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

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
                Box {
                    OutlinedButton(onClick = { appPickerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedApp?.label ?: "Choose an app…")
                    }
                    DropdownMenu(expanded = appPickerExpanded, onDismissRequest = { appPickerExpanded = false }) {
                        allApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                onClick = {
                                    selectedApp = app
                                    if (label.isBlank()) label = app.label
                                    appPickerExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            CustomCommandKind.DEEP_LINK -> {
                OutlinedTextField(
                    value = deepLinkUri,
                    onValueChange = { deepLinkUri = it },
                    label = { Text("Deep link URI (e.g. myapp://screen)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deepLinkPackage,
                    onValueChange = { deepLinkPackage = it },
                    label = { Text("Target app package (optional, e.g. com.example.app)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val canSave = phrase.isNotBlank() && label.isNotBlank() && when (kind) {
            CustomCommandKind.OPEN_APP -> selectedApp != null
            CustomCommandKind.DEEP_LINK -> deepLinkUri.isNotBlank()
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
                        deepLinkUri = if (kind == CustomCommandKind.DEEP_LINK) deepLinkUri.trim() else null,
                    ),
                )
                phrase = ""
                label = ""
                selectedApp = null
                deepLinkUri = ""
                deepLinkPackage = ""
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save command")
        }
    }
}
