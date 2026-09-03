package com.tarkeshstack.smartlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarkeshstack.smartlauncher.command.ActionExecutor
import com.tarkeshstack.smartlauncher.command.CommandParser
import com.tarkeshstack.smartlauncher.command.ExecutionResult
import com.tarkeshstack.smartlauncher.data.ContactsRepository
import com.tarkeshstack.smartlauncher.data.CustomCommandRepository
import com.tarkeshstack.smartlauncher.data.InstalledAppsRepository
import com.tarkeshstack.smartlauncher.data.SettingsRepository
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.ParsedCommand
import com.tarkeshstack.smartlauncher.model.SpeechRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val query: String = "",
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val customCommands: List<CustomCommand> = emptyList(),
    val command: ParsedCommand? = null,
    val statusMessage: String? = null,
    val pendingContactsPermissionFor: String? = null,
    val isListening: Boolean = false,
    val pendingCapturedLink: CapturedLink? = null,
    val pendingSpeech: SpeechRequest? = null,
    val showCommandsOnHome: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepo = InstalledAppsRepository(application)
    private val contactsRepo = ContactsRepository(application)
    private val customCommandsRepo = CustomCommandRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val executor = ActionExecutor(application, appsRepo, contactsRepo)

    private val _uiState = MutableStateFlow(UiState(showCommandsOnHome = settingsRepo.showCommandsOnHome()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = appsRepo.loadLaunchableApps()
            var customCommands = customCommandsRepo.loadAll()
            if (customCommands.isEmpty()) {
                // First run (or everything was deleted) — seed a couple of handy
                // defaults instead of an empty list, rather than presuming the user
                // wants to build every command from scratch.
                customCommands = defaultCommands()
                customCommandsRepo.saveAll(customCommands)
            }
            _uiState.update { it.copy(allApps = apps, filteredApps = apps, customCommands = customCommands) }
        }
    }

    private fun defaultCommands(): List<CustomCommand> = listOf(
        CustomCommand(
            id = UUID.randomUUID().toString(),
            phrase = "wifi",
            label = "Wi-Fi settings",
            kind = CustomCommandKind.SYSTEM_SHORTCUT,
            packageName = null,
            deepLinkUri = null,
            systemAction = "android.settings.WIFI_SETTINGS",
        ),
        CustomCommand(
            id = UUID.randomUUID().toString(),
            phrase = "change wallpaper",
            label = "Change wallpaper",
            kind = CustomCommandKind.SYSTEM_SHORTCUT,
            packageName = null,
            deepLinkUri = null,
            systemAction = "android.intent.action.SET_WALLPAPER",
        ),
        CustomCommand(
            id = UUID.randomUUID().toString(),
            phrase = "youtube soothing instrumental",
            label = "YouTube",
            kind = CustomCommandKind.DEEP_LINK,
            packageName = "com.google.android.youtube",
            deepLinkUri = "https://www.youtube.com/results?search_query=soothing+instrumental",
            systemAction = null,
        ),
    )

    fun setShowCommandsOnHome(show: Boolean) {
        settingsRepo.setShowCommandsOnHome(show)
        _uiState.update { it.copy(showCommandsOnHome = show) }
    }

    fun onQueryChanged(text: String) {
        // A user-defined command's trigger phrase wins over the built-in parser.
        val customMatch = customCommandsRepo.findMatch(_uiState.value.customCommands, text)
        val command = if (customMatch != null) {
            ParsedCommand(ActionType.CUSTOM, customMatch.id, label = "▶ ${customMatch.label}")
        } else {
            CommandParser.parse(text)
        }
        // For a plain "open <app>" (including the bare-app-name fallback), filter the
        // list by the extracted app name rather than the whole sentence — otherwise
        // "open uber" or "open latest mail in gmail" would never match "Uber"/"Gmail".
        val appSearchText = if (command.action == ActionType.OPEN_APP) command.target ?: text else text
        val filtered = filterApps(appSearchText)
        _uiState.update {
            it.copy(
                query = text,
                filteredApps = filtered,
                command = command.takeIf { c -> c.action != ActionType.NONE },
                statusMessage = null,
            )
        }
    }

    private fun filterApps(text: String): List<AppInfo> {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return _uiState.value.allApps
        val words = needle.split(Regex("\\s+")).filter { it.length >= 2 }
        return _uiState.value.allApps
            .filter { app ->
                val label = app.label.lowercase()
                label.contains(needle) || needle.contains(label) || words.any { label.contains(it) }
            }
            .sortedBy { app ->
                val label = app.label.lowercase()
                when {
                    label == needle -> 0
                    label.startsWith(needle) -> 1
                    label.contains(needle) -> 2
                    needle.contains(label) -> 3
                    else -> 4
                }
            }
    }

    private fun findAppByName(name: String): AppInfo? {
        val needle = name.trim().lowercase()
        if (needle.isEmpty()) return null
        return _uiState.value.allApps.firstOrNull { app ->
            val label = app.label.lowercase()
            label == needle || label.contains(needle) || needle.contains(label)
        }
    }

    fun launchApp(app: AppInfo) {
        runExecution(successMessage = "Opened ${app.label}") { executor.openApp(app.packageName) }
    }

    /** Runs the currently parsed command (Enter key / tapping the quick-action card). */
    fun runCommand() {
        val command = _uiState.value.command ?: return
        when (command.action) {
            ActionType.OPEN_APP -> {
                _uiState.value.filteredApps.firstOrNull()?.let { launchApp(it) }
            }
            ActionType.CUSTOM -> {
                val custom = _uiState.value.customCommands.firstOrNull { it.id == command.target }
                if (custom != null) {
                    runExecution(successMessage = "Ran \"${custom.label}\"") {
                        executor.runCustomCommand(custom)
                    }
                } else {
                    _uiState.update { it.copy(statusMessage = "That saved command no longer exists") }
                }
            }
            else -> runExecution(successMessage = command.label) { executor.execute(command) }
        }
    }

    /** Runs a saved command directly by id — used by the quick-access commands row on the
     *  search screen, where tapping one runs it without going through the typed query. */
    fun runCustomCommandById(id: String) {
        val custom = _uiState.value.customCommands.firstOrNull { it.id == id } ?: return
        runExecution(successMessage = "Ran \"${custom.label}\"") {
            executor.runCustomCommand(custom)
        }
    }

    /** Adds a new command, or replaces the existing one with the same id — the add and
     *  edit forms are the same UI, so saving just needs to know which case it is. */
    fun saveCustomCommand(command: CustomCommand) {
        viewModelScope.launch {
            val existing = _uiState.value.customCommands
            val updated = if (existing.any { it.id == command.id }) {
                existing.map { if (it.id == command.id) command else it }
            } else {
                existing + command
            }
            customCommandsRepo.saveAll(updated)
            _uiState.update { it.copy(customCommands = updated) }
        }
    }

    fun deleteCustomCommand(id: String) {
        viewModelScope.launch {
            val updated = _uiState.value.customCommands.filterNot { it.id == id }
            customCommandsRepo.saveAll(updated)
            _uiState.update { it.copy(customCommands = updated) }
        }
    }

    fun onListeningChanged(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun onVoiceError(message: String) {
        _uiState.update { it.copy(isListening = false, statusMessage = message) }
    }

    /** Whether the in-flight command was triggered by voice, so the mic can re-arm itself
     *  after speaking the result — typing never triggers that, only speaking does. */
    private var pendingTurnWasVoice = false

    fun onVoiceResult(text: String) {
        pendingTurnWasVoice = true
        onQueryChanged(text)
        runCommand()
    }

    fun consumeSpeechRequest() {
        _uiState.update { it.copy(pendingSpeech = null) }
    }

    /** Only when a turn came from voice, queues the result to be spoken back — voice
     *  replies are always on, the way "Opened Spotify" plays after a spoken command;
     *  typing never triggers a spoken reply. */
    private fun completeTurn(assistantText: String) {
        val wasVoice = pendingTurnWasVoice
        pendingTurnWasVoice = false
        if (wasVoice) {
            _uiState.update { it.copy(pendingSpeech = SpeechRequest(text = assistantText)) }
        }
    }

    /** A link arrived via another app's Share sheet; the command manager prefills from it. */
    fun onLinkCaptured(link: CapturedLink) {
        _uiState.update { it.copy(pendingCapturedLink = link) }
    }

    fun consumeCapturedLink() {
        _uiState.update { it.copy(pendingCapturedLink = null) }
    }

    private fun runExecution(successMessage: String, block: suspend () -> ExecutionResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is ExecutionResult.Launched -> {
                    _uiState.update { it.copy(statusMessage = null, query = "", command = null) }
                    completeTurn(successMessage)
                }
                is ExecutionResult.AppNotInstalled -> {
                    val message = "${result.appLabel} isn't installed"
                    _uiState.update { it.copy(statusMessage = message) }
                    completeTurn(message)
                }
                is ExecutionResult.NeedsContactsPermission -> {
                    // Resolves via onContactsPermissionResult, which re-runs the command
                    // once the permission prompt is answered.
                    _uiState.update { it.copy(pendingContactsPermissionFor = result.retryText) }
                }
                is ExecutionResult.ContactNotFound -> {
                    val message = "No contact found for \"${result.name}\""
                    _uiState.update { it.copy(statusMessage = message) }
                    completeTurn(message)
                }
                is ExecutionResult.Failed -> {
                    _uiState.update { it.copy(statusMessage = result.reason) }
                    completeTurn(result.reason)
                }
                is ExecutionResult.UnknownAppSearch -> {
                    val match = findAppByName(result.appName)
                    val message = if (match != null) {
                        executor.openApp(match.packageName)
                        "${match.label} has no built-in search link here — opened it; try typing \"${result.query}\" inside"
                    } else {
                        "Couldn't find an app matching \"${result.appName}\""
                    }
                    _uiState.update { it.copy(statusMessage = message, query = "", command = null) }
                    completeTurn(message)
                }
            }
        }
    }

    /** Called once the user has responded to the READ_CONTACTS runtime permission prompt. */
    fun onContactsPermissionResult(granted: Boolean) {
        val retryText = _uiState.value.pendingContactsPermissionFor
        _uiState.update { it.copy(pendingContactsPermissionFor = null) }
        if (granted && retryText != null) {
            onQueryChanged(retryText)
            runCommand()
        } else if (!granted) {
            _uiState.update { it.copy(statusMessage = "Contacts permission denied — try typing a phone number instead") }
        }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
