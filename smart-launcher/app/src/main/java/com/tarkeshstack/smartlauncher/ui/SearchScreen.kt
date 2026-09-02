package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tarkeshstack.smartlauncher.MainViewModel
import com.tarkeshstack.smartlauncher.UiState
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CustomCommand

private const val QUICK_COMMANDS_LIMIT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: UiState,
    viewModel: MainViewModel,
    onRequestContactsPermission: (String) -> Unit,
    onMicTapped: () -> Unit,
    onOpenCommandManager: () -> Unit,
    onAddCommand: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissStatus()
        }
    }

    LaunchedEffect(state.pendingContactsPermissionFor) {
        state.pendingContactsPermissionFor?.let { onRequestContactsPermission(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Smart Launcher", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        // One continuous scrollable list for the whole screen — search field, quick
        // commands, and the app list all scroll together instead of as separate regions,
        // so there's no hard edge where scrolling used to look like it was cutting off.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search, speak, or type a command…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onMicTapped) {
                            Icon(
                                if (state.isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = "Voice input",
                                tint = if (state.isListening) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.runCommand() }),
                    colors = TextFieldDefaults.colors(
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )

                if (state.isListening) {
                    Text(
                        "Listening…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                val command = state.command
                if (command != null && command.action != ActionType.OPEN_APP && command.action != ActionType.NONE) {
                    QuickActionCard(label = command.label, onClick = viewModel::runCommand)
                }
            }

            item {
                CommandsQuickAccess(
                    commands = state.customCommands,
                    allApps = state.allApps,
                    onRun = viewModel::runCustomCommandById,
                    onAdd = onAddCommand,
                    onOpenAll = onOpenCommandManager,
                )
            }

            items(state.filteredApps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { viewModel.launchApp(app) })
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Up to a few saved commands, one tap away right on the home screen — this is also the
 *  only way to reach the full "Your commands" list now (tap the "Commands" label). With
 *  4 or fewer, an "Add" chip sits inline at the end of the row; once there are more than
 *  that, the row only shows the first few and the add action moves to a small button in
 *  the section's top-right corner instead of scrolling off with the rest. */
@Composable
private fun CommandsQuickAccess(
    commands: List<CustomCommand>,
    allApps: List<AppInfo>,
    onRun: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenAll: () -> Unit,
) {
    val overflowing = commands.size > QUICK_COMMANDS_LIMIT

    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Commands",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenAll),
            )
            if (overflowing) {
                IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add a command",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(commands.take(QUICK_COMMANDS_LIMIT), key = { it.id }) { command ->
                CommandChip(
                    command = command,
                    app = allApps.firstOrNull { it.packageName == command.packageName },
                    onClick = { onRun(command.id) },
                )
            }
            if (!overflowing) {
                item { AddCommandChip(onClick = onAdd) }
            }
        }
    }
}

@Composable
private fun CommandChip(command: CustomCommand, app: AppInfo?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (app != null) {
                Image(
                    bitmap = remember(app.packageName) { app.icon.toBitmap(width = 48, height = 48).asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                command.phrase,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddCommandChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Add command",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickActionCard(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            bitmap = remember(app.packageName) { app.icon.toBitmap(width = 96, height = 96).asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Text(app.label, style = MaterialTheme.typography.bodyLarge)
    }
}
