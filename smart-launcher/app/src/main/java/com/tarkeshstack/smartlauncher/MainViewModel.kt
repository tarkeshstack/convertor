package com.tarkeshstack.smartlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarkeshstack.smartlauncher.command.ActionExecutor
import com.tarkeshstack.smartlauncher.command.CommandParser
import com.tarkeshstack.smartlauncher.command.ExecutionResult
import com.tarkeshstack.smartlauncher.data.ContactsRepository
import com.tarkeshstack.smartlauncher.data.InstalledAppsRepository
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.ParsedCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val query: String = "",
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val command: ParsedCommand? = null,
    val statusMessage: String? = null,
    val pendingContactsPermissionFor: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepo = InstalledAppsRepository(application)
    private val contactsRepo = ContactsRepository(application)
    private val executor = ActionExecutor(application, appsRepo, contactsRepo)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = appsRepo.loadLaunchableApps()
            _uiState.update { it.copy(allApps = apps, filteredApps = apps) }
        }
    }

    fun onQueryChanged(text: String) {
        val command = CommandParser.parse(text)
        val filtered = filterApps(text)
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
        return _uiState.value.allApps
            .filter { it.label.lowercase().contains(needle) }
            .sortedBy { it.label.lowercase().indexOf(needle) }
    }

    fun launchApp(app: AppInfo) {
        runExecution { executor.openApp(app.packageName) }
    }

    /** Runs the currently parsed command (Enter key / tapping the quick-action card). */
    fun runCommand() {
        val command = _uiState.value.command ?: return
        if (command.action == ActionType.OPEN_APP) {
            _uiState.value.filteredApps.firstOrNull()?.let { launchApp(it) }
            return
        }
        runExecution { executor.execute(command) }
    }

    private fun runExecution(block: suspend () -> ExecutionResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is ExecutionResult.Launched -> {
                    _uiState.update { it.copy(statusMessage = null, query = "", command = null) }
                }
                is ExecutionResult.AppNotInstalled -> {
                    _uiState.update { it.copy(statusMessage = "${result.appLabel} isn't installed") }
                }
                is ExecutionResult.NeedsContactsPermission -> {
                    _uiState.update { it.copy(pendingContactsPermissionFor = result.retryText) }
                }
                is ExecutionResult.ContactNotFound -> {
                    _uiState.update { it.copy(statusMessage = "No contact found for \"${result.name}\"") }
                }
                is ExecutionResult.Failed -> {
                    _uiState.update { it.copy(statusMessage = result.reason) }
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
