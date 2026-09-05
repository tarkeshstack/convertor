package com.tarkeshstack.smartlauncher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarkeshstack.smartlauncher.command.ActionExecutor
import com.tarkeshstack.smartlauncher.command.CommandParser
import com.tarkeshstack.smartlauncher.command.ExecutionResult
import com.tarkeshstack.smartlauncher.data.ContactsRepository
import com.tarkeshstack.smartlauncher.data.CustomCommandRepository
import com.tarkeshstack.smartlauncher.data.InstalledAppsRepository
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.DEEP_LINK_PLACEHOLDER
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions
import com.tarkeshstack.smartlauncher.model.ParsedCommand
import com.tarkeshstack.smartlauncher.model.SpeechRequest
import com.tarkeshstack.smartlauncher.model.SystemShortcuts
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepo = InstalledAppsRepository(application)
    private val contactsRepo = ContactsRepository(application)
    private val customCommandsRepo = CustomCommandRepository(application)
    private val executor = ActionExecutor(application, appsRepo, contactsRepo)

    private val _uiState = MutableStateFlow(UiState())
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

    /** First-run defaults: every built-in system shortcut, plus a trigger phrase for each
     *  of the popular apps' most useful deep link. Each popular one still carries
     *  [DEEP_LINK_PLACEHOLDER] in its URI — running it prompts for the keyword right then
     *  (see [runCustomCommandWithKeyword]) rather than guessing one up front. */
    private fun defaultCommands(): List<CustomCommand> {
        val systemCommands = SystemShortcuts.all.map { shortcut ->
            val phrase = if (shortcut.action == "android.settings.WIFI_SETTINGS") {
                "wifi"
            } else {
                shortcut.label.lowercase()
            }
            CustomCommand(
                id = UUID.randomUUID().toString(),
                phrase = phrase,
                label = shortcut.label,
                kind = CustomCommandKind.SYSTEM_SHORTCUT,
                packageName = null,
                deepLinkUri = null,
                systemAction = shortcut.action,
            )
        }
        val popularCommands = DeepLinkSuggestions.all.mapNotNull { suggestion ->
            val phrase = popularCommandPhrase(suggestion.appLabel) ?: return@mapNotNull null
            CustomCommand(
                id = UUID.randomUUID().toString(),
                phrase = phrase,
                label = suggestion.appLabel,
                kind = CustomCommandKind.DEEP_LINK,
                packageName = suggestion.packageName,
                deepLinkUri = suggestion.uriTemplate,
                systemAction = null,
            )
        }
        return systemCommands + popularCommands
    }

    /** Trigger phrase for each popular suggestion worth a default command — the generic
     *  ones (Play Store, any website) aren't a specific "popular command" on their own. */
    private fun popularCommandPhrase(appLabel: String): String? = when (appLabel) {
        "YouTube" -> "youtube search"
        "Spotify" -> "spotify search"
        "Amazon" -> "amazon search"
        "Instagram" -> "instagram profile"
        "X (Twitter)" -> "twitter profile"
        "Telegram" -> "telegram chat"
        "Netflix" -> "netflix title"
        "Google Maps" -> "maps search"
        else -> null
    }

    /** Per-command home-screen visibility — each saved command shows or hides on the
     *  home screen independently rather than as one switch for all of them. */
    fun setCommandVisibleOnHome(id: String, visible: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.customCommands.map {
                if (it.id == id) it.copy(visibleOnHome = visible) else it
            }
            customCommandsRepo.saveAll(updated)
            _uiState.update { it.copy(customCommands = updated) }
        }
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

    /** Runs a command whose deep link still has [DEEP_LINK_PLACEHOLDER] in it, substituting
     *  [keyword] just for this run — the saved link itself keeps the placeholder, so the
     *  command stays a reusable template, but [keyword] is remembered as the command's
     *  lastKeyword so it's prefilled, and the command reads as already fed, the next time
     *  the app is opened. */
    fun runCustomCommandWithKeyword(id: String, keyword: String) {
        val custom = _uiState.value.customCommands.firstOrNull { it.id == id } ?: return
        val trimmedKeyword = keyword.trim()
        val filledUri = custom.deepLinkUri?.replace(DEEP_LINK_PLACEHOLDER, Uri.encode(trimmedKeyword))
        saveCustomCommand(custom.copy(lastKeyword = trimmedKeyword))
        runExecution(successMessage = "Ran \"${custom.label}\"") {
            executor.runCustomCommand(custom.copy(deepLinkUri = filledUri))
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
