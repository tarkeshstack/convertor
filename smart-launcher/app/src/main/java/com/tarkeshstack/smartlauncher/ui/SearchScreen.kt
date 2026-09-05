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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.DEEP_LINK_PLACEHOLDER
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions
import com.tarkeshstack.smartlauncher.ui.theme.KeywordInputEmpty
import com.tarkeshstack.smartlauncher.ui.theme.KeywordInputFilled

private enum class HomeTab { Apps, Commands }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: UiState,
    viewModel: MainViewModel,
    onRequestContactsPermission: (String) -> Unit,
    onMicTapped: () -> Unit,
    onOpenCommandManager: () -> Unit,
    onAddCommand: () -> Unit,
    onEditCommand: (CustomCommand) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var quickFillCommand by remember { mutableStateOf<CustomCommand?>(null) }
    var selectedTab by remember { mutableStateOf(HomeTab.Apps) }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        // Commands not hidden, app-linked ones first and system shortcuts after — each
        // still carries its own hide eye and edit pencil; a hidden one stays fully
        // manageable (show again, edit, delete) in "Your commands" only.
        val visibleCommands = state.customCommands
            .filter { it.visibleOnHome }
            .sortedBy { if (it.kind == CustomCommandKind.SYSTEM_SHORTCUT) 1 else 0 }

        // While typing, the Apps/Commands split gives way to one merged list — apps
        // matching the text, plus any command whose own phrase matches or that's linked
        // to one of those apps (so typing "wifi" surfaces the wifi command even though no
        // app is named "wifi").
        val isSearching = state.query.isNotBlank()
        val relatedCommands = if (isSearching) {
            val needle = state.query.trim().lowercase()
            visibleCommands.filter { command ->
                command.phrase.lowercase().contains(needle) ||
                    state.filteredApps.any { it.packageName == command.packageName }
            }
        } else {
            emptyList()
        }

        @Composable
        fun commandRow(command: CustomCommand) {
            CommandListRow(
                command = command,
                app = state.allApps.firstOrNull { it.packageName == command.packageName },
                onClick = {
                    if (command.deepLinkUri?.contains(DEEP_LINK_PLACEHOLDER) == true) {
                        quickFillCommand = command
                    } else {
                        viewModel.runCustomCommandById(command.id)
                    }
                },
                onEdit = { onEditCommand(command) },
                onHide = { viewModel.setCommandVisibleOnHome(command.id, false) },
                fedIn = command.lastKeyword != null,
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The search field and the Apps/Commands tabs never scroll away — they sit
            // above the list, not inside it, so switching tabs (or adding a command, on
            // the Commands tab) is always available no matter how far down the list below
            // is scrolled.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("App name or custom commands") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.query.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.onQueryChanged("") },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
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

                // Searching replaces the tabs outright — a single merged list underneath
                // says more than which of two tabs is active.
                if (!isSearching) {
                    Spacer(Modifier.height(12.dp))
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == HomeTab.Apps,
                            onClick = { selectedTab = HomeTab.Apps },
                            text = { Text("Apps") },
                        )
                        Tab(
                            selected = selectedTab == HomeTab.Commands,
                            onClick = { selectedTab = HomeTab.Commands },
                            text = { Text("Commands") },
                        )
                    }

                    if (selectedTab == HomeTab.Commands) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Manage all",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).clickable(onClick = onOpenCommandManager),
                            )
                            IconButton(onClick = onAddCommand, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add command",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isSearching) {
                    items(relatedCommands, key = { "cmd_${it.id}" }) { command -> commandRow(command) }
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        AppRow(app = app, onClick = { viewModel.launchApp(app) })
                    }
                } else {
                    when (selectedTab) {
                        HomeTab.Apps -> {
                            items(state.filteredApps, key = { it.packageName }) { app ->
                                AppRow(app = app, onClick = { viewModel.launchApp(app) })
                            }
                        }
                        HomeTab.Commands -> {
                            items(visibleCommands, key = { it.id }) { command -> commandRow(command) }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    val fillTarget = quickFillCommand
    if (fillTarget != null) {
        QuickFillDialog(
            commandLabel = fillTarget.label,
            hint = DeepLinkSuggestions.all.firstOrNull { it.packageName == fillTarget.packageName }?.keywordHint,
            initialKeyword = fillTarget.lastKeyword,
            onDismiss = { quickFillCommand = null },
            onRun = { keyword ->
                viewModel.runCustomCommandWithKeyword(fillTarget.id, keyword)
                quickFillCommand = null
            },
        )
    }
}

@Composable
private fun CommandListRow(
    command: CustomCommand,
    app: AppInfo?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    fedIn: Boolean,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                app != null -> Image(
                    bitmap = remember(app.packageName) { app.icon.toBitmap(width = 48, height = 48).asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                command.kind == CustomCommandKind.SYSTEM_SHORTCUT -> Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                else -> Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                command.phrase,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (command.deepLinkUri?.contains(DEEP_LINK_PLACEHOLDER) == true) {
                Icon(
                    Icons.Filled.Keyboard,
                    contentDescription = if (fedIn) {
                        "Run with a keyword this session"
                    } else {
                        "Needs a keyword to run"
                    },
                    tint = if (fedIn) KeywordInputFilled else KeywordInputEmpty,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = "Hide from home screen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit command",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
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
