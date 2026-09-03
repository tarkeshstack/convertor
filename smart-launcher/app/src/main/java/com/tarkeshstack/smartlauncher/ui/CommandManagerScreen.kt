package com.tarkeshstack.smartlauncher.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CommandDraft
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.DEEP_LINK_PLACEHOLDER
import java.util.UUID

private const val PLACEHOLDER = DEEP_LINK_PLACEHOLDER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandManagerScreen(
    commands: List<CustomCommand>,
    allApps: List<AppInfo>,
    pendingCapturedLink: CapturedLink?,
    onConsumeCapturedLink: () -> Unit,
    draft: CommandDraft,
    onDraftChanged: (CommandDraft) -> Unit,
    onSave: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
    onGetLink: (currentPackage: String?) -> Unit,
    onBack: () -> Unit,
    onToggleVisibleOnHome: (CustomCommand) -> Unit,
    /** Opens straight into the add-command form — used when this screen is reached via
     *  the home screen's "Add command"/edit-pencil row rather than the header icon. */
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
                title = { Text(if (draft.editingId != null) "Edit command" else "Your commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showAddForm = !showAddForm
                        if (!showAddForm) onDraftChanged(CommandDraft())
                    }) {
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
                    "Shortcuts only — a plain app name already opens it, no command " +
                        "needed for that.",
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
                        draft = draft,
                        onDraftChanged = onDraftChanged,
                        onSave = { command ->
                            onSave(command)
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
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column {
                            commands.forEachIndexed { index, command ->
                                CommandRow(
                                    command = command,
                                    allApps = allApps,
                                    onEdit = {
                                        onDraftChanged(
                                            CommandDraft(
                                                editingId = command.id,
                                                phrase = command.phrase,
                                                deepLinkUri = command.deepLinkUri.orEmpty(),
                                                deepLinkPackage = if (command.kind == CustomCommandKind.DEEP_LINK) {
                                                    command.packageName.orEmpty()
                                                } else {
                                                    ""
                                                },
                                                systemAction = command.systemAction.orEmpty(),
                                                systemActionLabel = if (command.kind == CustomCommandKind.SYSTEM_SHORTCUT) {
                                                    command.label
                                                } else {
                                                    ""
                                                },
                                            ),
                                        )
                                        showAddForm = true
                                    },
                                    onToggleVisibleOnHome = { onToggleVisibleOnHome(command) },
                                    onDelete = { onDelete(command.id) },
                                )
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
private fun CommandRow(
    command: CustomCommand,
    allApps: List<AppInfo>,
    onEdit: () -> Unit,
    onToggleVisibleOnHome: () -> Unit,
    onDelete: () -> Unit,
) {
    val app = remember(command.packageName, allApps) {
        allApps.firstOrNull { it.packageName == command.packageName }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(32.dp).height(32.dp),
        ) {
            when {
                app != null -> Image(
                    bitmap = remember(app.packageName) { app.icon.toBitmap(width = 64, height = 64).asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                )
                command.kind == CustomCommandKind.SYSTEM_SHORTCUT -> Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(7.dp),
                )
                else -> Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(7.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            command.phrase,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (command.deepLinkUri?.contains(PLACEHOLDER) == true) {
            Icon(
                Icons.Filled.Keyboard,
                contentDescription = "Needs a keyword to run",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp).padding(end = 4.dp),
            )
        }
        IconButton(onClick = onToggleVisibleOnHome) {
            Icon(
                if (command.visibleOnHome) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (command.visibleOnHome) {
                    "Hide from home screen"
                } else {
                    "Show on home screen"
                },
                tint = if (command.visibleOnHome) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit command")
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
    draft: CommandDraft,
    onDraftChanged: (CommandDraft) -> Unit,
    onSave: (CustomCommand) -> Unit,
    onGetLink: (currentPackage: String?) -> Unit,
) {
    var shortcutPickerOpen by remember { mutableStateOf(false) }

    // A link captured on the "Get a link" screen lands here pre-filled, and always wins
    // over any system shortcut previously chosen in this same draft.
    LaunchedEffect(pendingCapturedLink) {
        val captured = pendingCapturedLink ?: return@LaunchedEffect
        onDraftChanged(
            draft.copy(
                deepLinkUri = captured.uri,
                deepLinkPackage = captured.sourcePackage.orEmpty(),
                placeholderValue = "",
                justCaptured = true,
                systemAction = "",
                systemActionLabel = "",
            ),
        )
        onConsumeCapturedLink()
    }

    val hasPlaceholder = draft.deepLinkUri.contains(PLACEHOLDER)
    val resolvedDeepLinkUri = if (hasPlaceholder && draft.placeholderValue.isNotBlank()) {
        draft.deepLinkUri.replace(PLACEHOLDER, Uri.encode(draft.placeholderValue.trim()))
    } else {
        draft.deepLinkUri
    }
    val hasShortcut = draft.systemAction.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (draft.editingId != null) "Edit command" else "Add a command",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            if (draft.justCaptured) {
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
                value = draft.phrase,
                onValueChange = { onDraftChanged(draft.copy(phrase = it)) },
                label = { Text("Trigger phrase (what you'll type or say)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "What should it do?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Link,
                    label = if (draft.deepLinkUri.isBlank()) "Get a link" else "Change link",
                    selected = draft.deepLinkUri.isNotBlank(),
                    onClick = { onGetLink(draft.deepLinkPackage.trim().ifBlank { null }) },
                )
                ChoiceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Settings,
                    label = if (hasShortcut) "Change" else "Shortcut",
                    selected = hasShortcut,
                    onClick = { shortcutPickerOpen = true },
                )
            }

            if (draft.deepLinkUri.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    draft.deepLinkUri,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                )

                if (hasPlaceholder) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft.placeholderValue,
                        onValueChange = { onDraftChanged(draft.copy(placeholderValue = it)) },
                        label = { Text("Value to fill in (replaces $PLACEHOLDER in the link)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        if (draft.placeholderValue.isNotBlank()) {
                            "Will save as: $resolvedDeepLinkUri"
                        } else {
                            "Type a value above — you never need to edit the link text itself."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else if (hasShortcut) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        draft.systemActionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val canSave = draft.phrase.isNotBlank() &&
                (draft.deepLinkUri.isNotBlank() || hasShortcut) &&
                (!hasPlaceholder || draft.placeholderValue.isNotBlank())

            Button(
                onClick = {
                    val kind = if (hasShortcut) CustomCommandKind.SYSTEM_SHORTCUT else CustomCommandKind.DEEP_LINK
                    // The label is the picked app's/shortcut's real name, falling back to
                    // the trigger phrase itself, since there's no separate "display name"
                    // field and the target is always whatever was picked above.
                    val resolvedLabel = when (kind) {
                        CustomCommandKind.SYSTEM_SHORTCUT -> draft.systemActionLabel
                        else -> allApps.firstOrNull { it.packageName == draft.deepLinkPackage.trim() }?.label
                            ?: draft.phrase.trim().replaceFirstChar { it.uppercaseChar() }
                    }
                    onSave(
                        CustomCommand(
                            id = draft.editingId ?: UUID.randomUUID().toString(),
                            phrase = draft.phrase.trim(),
                            label = resolvedLabel,
                            kind = kind,
                            packageName = if (kind == CustomCommandKind.DEEP_LINK) {
                                draft.deepLinkPackage.trim().ifBlank { null }
                            } else {
                                null
                            },
                            deepLinkUri = if (kind == CustomCommandKind.DEEP_LINK) resolvedDeepLinkUri.trim() else null,
                            systemAction = if (kind == CustomCommandKind.SYSTEM_SHORTCUT) draft.systemAction else null,
                        ),
                    )
                    onDraftChanged(CommandDraft())
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (draft.editingId != null) "Save changes" else "Save command")
            }
        }
    }

    if (shortcutPickerOpen) {
        SystemShortcutPickerDialog(
            onDismiss = { shortcutPickerOpen = false },
            onSelect = { shortcut ->
                onDraftChanged(
                    draft.copy(
                        systemAction = shortcut.action,
                        systemActionLabel = shortcut.label,
                        deepLinkUri = "",
                        deepLinkPackage = "",
                        placeholderValue = "",
                        justCaptured = false,
                    ),
                )
                shortcutPickerOpen = false
            },
        )
    }
}

@Composable
private fun ChoiceCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
