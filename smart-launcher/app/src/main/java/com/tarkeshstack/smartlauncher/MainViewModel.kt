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
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.ConversationEntry
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.ParsedCommand
import com.tarkeshstack.smartlauncher.model.SpeechRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val conversationLog: List<ConversationEntry> = emptyList(),
    val speechEnabled: Boolean = false,
    val pendingSpeech: SpeechRequest? = null,
    val wakeWordEnabled: Boolean = false,
    val pendingWakeResume: Boolean = false,
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
            val customCommands = customCommandsRepo.loadAll()
            _uiState.update { it.copy(allApps = apps, filteredApps = apps, customCommands = customCommands) }
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
        val userText = _uiState.value.query.ifBlank { app.label }
        runExecution(userText, successMessage = "Opened ${app.label}") { executor.openApp(app.packageName) }
    }

    /** Runs the currently parsed command (Enter key / tapping the quick-action card). */
    fun runCommand() {
        val command = _uiState.value.command ?: return
        val userText = _uiState.value.query
        when (command.action) {
            ActionType.OPEN_APP -> {
                _uiState.value.filteredApps.firstOrNull()?.let { launchApp(it) }
            }
            ActionType.CUSTOM -> {
                val custom = _uiState.value.customCommands.firstOrNull { it.id == command.target }
                if (custom != null) {
                    runExecution(userText, successMessage = "Ran \"${custom.label}\"") {
                        executor.runCustomCommand(custom)
                    }
                } else {
                    _uiState.update { it.copy(statusMessage = "That saved command no longer exists") }
                }
            }
            else -> runExecution(userText, successMessage = command.label) { executor.execute(command) }
        }
    }

    fun addCustomCommand(command: CustomCommand) {
        viewModelScope.launch {
            val updated = _uiState.value.customCommands + command
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

    fun toggleConversationMode() {
        _uiState.update { it.copy(speechEnabled = !it.speechEnabled) }
    }

    fun toggleWakeWord() {
        _uiState.update { it.copy(wakeWordEnabled = !it.wakeWordEnabled) }
    }

    fun consumeSpeechRequest() {
        _uiState.update { it.copy(pendingSpeech = null) }
    }

    fun consumeWakeResume() {
        _uiState.update { it.copy(pendingWakeResume = false) }
    }

    fun clearConversation() {
        _uiState.update { it.copy(conversationLog = emptyList()) }
    }

    /** Logs a turn (what you said/tapped, what happened) and, in conversation mode, queues
     *  it to be spoken — re-listening afterward only if this turn came from voice. When wake
     *  word is on, whichever path isn't already re-arming the mic resumes passive wake
     *  listening once the turn is fully settled. */
    private fun completeTurn(userText: String, assistantText: String) {
        val wasVoice = pendingTurnWasVoice
        pendingTurnWasVoice = false
        _uiState.update { state ->
            val speech = if (state.speechEnabled) {
                SpeechRequest(
                    text = assistantText,
                    shouldRelisten = wasVoice,
                    resumeWakeAfter = state.wakeWordEnabled && !wasVoice,
                )
            } else {
                null
            }
            state.copy(
                conversationLog = state.conversationLog +
                    ConversationEntry(userText, isUser = true) +
                    ConversationEntry(assistantText, isUser = false),
                pendingSpeech = speech,
                pendingWakeResume = state.wakeWordEnabled && speech == null,
            )
        }
    }

    /** A link arrived via another app's Share sheet; the command manager prefills from it. */
    fun onLinkCaptured(link: CapturedLink) {
        _uiState.update { it.copy(pendingCapturedLink = link) }
    }

    fun consumeCapturedLink() {
        _uiState.update { it.copy(pendingCapturedLink = null) }
    }

    private fun runExecution(userText: String, successMessage: String, block: suspend () -> ExecutionResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is ExecutionResult.Launched -> {
                    _uiState.update { it.copy(statusMessage = null, query = "", command = null) }
                    completeTurn(userText, successMessage)
                }
                is ExecutionResult.AppNotInstalled -> {
                    val message = "${result.appLabel} isn't installed"
                    _uiState.update { it.copy(statusMessage = message) }
                    completeTurn(userText, message)
                }
                is ExecutionResult.NeedsContactsPermission -> {
                    // No log entry yet: this resolves via onContactsPermissionResult, which
                    // re-runs the command (and logs it) once the permission prompt is answered.
                    _uiState.update { it.copy(pendingContactsPermissionFor = result.retryText) }
                }
                is ExecutionResult.ContactNotFound -> {
                    val message = "No contact found for \"${result.name}\""
                    _uiState.update { it.copy(statusMessage = message) }
                    completeTurn(userText, message)
                }
                is ExecutionResult.Failed -> {
                    _uiState.update { it.copy(statusMessage = result.reason) }
                    completeTurn(userText, result.reason)
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
                    completeTurn(userText, message)
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
