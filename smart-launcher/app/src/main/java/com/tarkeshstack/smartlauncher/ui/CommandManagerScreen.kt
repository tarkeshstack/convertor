package com.tarkeshstack.smartlauncher.ui

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import java.util.UUID

private const val PLACEHOLDER = "REPLACE_ME"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandManagerScreen(
    commands: List<CustomCommand>,
    pendingCapturedLink: CapturedLink?,
    onConsumeCapturedLink: () -> Unit,
    onAdd: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
    onGetLink: () -> Unit,
    onBack: () -> Unit,
    /** Opens straight into the add-command form — used when this screen is reached via
     *  the home screen's "Add command" chip/corner button rather than the header icon. */
    openAddFormInitially: Boolean = false,
) {
    var showAddForm by remember { mutableStateOf(openAddFormInitially) }

    // A link captured from Get a link should always land you in the form, however
    // you got to this screen.
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
                        pendingCapturedLink = pendingCapturedLink,
                        onConsumeCapturedLink = onConsumeCapturedLink,
                        onAdd = { command ->
                            onAdd(command)
                            showAddForm = false
                        },
                        onGetLink = onGetLink,
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
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column {
                            commands.forEachIndexed { index, command ->
                                CommandRow(command = command, onDelete = { onDelete(command.id) })
                                if (index != commands.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CommandRow(command: CustomCommand, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(32.dp).height(32.dp),
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(7.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(command.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "\"${command.phrase}\"",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete command")
        }
    }
}

@Composable
private fun AddCommandForm(
    pendingCapturedLink: CapturedLink?,
    onConsumeCapturedLink: () -> Unit,
    onAdd: (CustomCommand) -> Unit,
    onGetLink: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var deepLinkUri by remember { mutableStateOf("") }
    var deepLinkPackage by remember { mutableStateOf("") }
    var placeholderValue by remember { mutableStateOf("") }
    var justCaptured by remember { mutableStateOf(false) }

    // A link captured on the "Get a link" screen lands here pre-filled.
    LaunchedEffect(pendingCapturedLink) {
        val captured = pendingCapturedLink ?: return@LaunchedEffect
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

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onGetLink,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (deepLinkUri.isBlank()) "Get a link" else "Change link",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (deepLinkUri.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    deepLinkUri,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
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
                Text(
                    "Target app package (optional) — restricts which app opens the link",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = deepLinkPackage,
                        onValueChange = { deepLinkPackage = it },
                        placeholder = { Text("e.g. com.example.app") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
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
