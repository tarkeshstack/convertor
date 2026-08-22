package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tarkeshstack.smartlauncher.MainViewModel
import com.tarkeshstack.smartlauncher.UiState
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.ConversationEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: UiState,
    viewModel: MainViewModel,
    onRequestContactsPermission: (String) -> Unit,
    onMicTapped: () -> Unit,
    onOpenCommandManager: () -> Unit,
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
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Smart Launcher", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleConversationMode) {
                        Icon(
                            if (state.speechEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (state.speechEnabled) {
                                "Conversation mode on — turn off spoken replies"
                            } else {
                                "Conversation mode off — turn on spoken replies"
                            },
                            tint = if (state.speechEnabled) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onOpenCommandManager) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage custom commands")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        val isIdle = state.query.isBlank() && state.conversationLog.isEmpty() && state.command == null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            // The search bar sits roughly mid-screen rather than jammed under the header —
            // this region is vertically centered, with the app list filling the rest below.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (isIdle) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "What do you want to do?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

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

                    if (state.conversationLog.isNotEmpty()) {
                        ConversationTranscript(
                            entries = state.conversationLog,
                            onClear = viewModel::clearConversation,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    AppRow(app = app, onClick = { viewModel.launchApp(app) })
                }
            }
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
private fun ConversationTranscript(entries: List<ConversationEntry>, onClear: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Conversation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entries) { entry -> ConversationBubble(entry) }
        }
    }
}

@Composable
private fun ConversationBubble(entry: ConversationEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (entry.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                entry.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
