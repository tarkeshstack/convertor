package com.tarkeshstack.speakeasy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarkeshstack.speakeasy.coach.CoachService
import com.tarkeshstack.speakeasy.data.HistoryRepository
import com.tarkeshstack.speakeasy.data.SettingsRepository
import com.tarkeshstack.speakeasy.grammar.GrammarService
import com.tarkeshstack.speakeasy.model.ConversationEntry
import com.tarkeshstack.speakeasy.model.AnalysisResult
import com.tarkeshstack.speakeasy.model.PracticeStatus
import com.tarkeshstack.speakeasy.model.SpeechRequest
import com.tarkeshstack.speakeasy.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val activeTab: Tab = Tab.Practice,
    val status: PracticeStatus = PracticeStatus.Idle,
    val liveTranscript: String = "",
    val rmsLevel: Float = 0f,
    val isListening: Boolean = false,
    val result: AnalysisResult? = null,
    val history: List<ConversationEntry> = emptyList(),
    val errorMessage: String? = null,
    val pendingSpeech: SpeechRequest? = null,
    val apiKey: String? = null,
    val showSettings: Boolean = false,
    val coachLoading: Boolean = false,
    val coachFeedback: String? = null,
    val coachError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val grammarService = GrammarService()
    private val historyRepo = HistoryRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val coachService = CoachService()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(history = historyRepo.loadAll(), apiKey = settingsRepo.getApiKey()) }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun saveApiKey(key: String) {
        settingsRepo.setApiKey(key)
        _uiState.update { it.copy(apiKey = settingsRepo.getApiKey(), showSettings = false) }
    }

    fun setTab(tab: Tab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onStartListening() {
        _uiState.update {
            it.copy(
                status = PracticeStatus.Listening,
                liveTranscript = "",
                result = null,
                errorMessage = null,
            )
        }
    }

    fun onListeningChanged(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun onRmsChanged(rawDb: Float) {
        val normalized = ((rawDb + 2f) / 12f).coerceIn(0f, 1f)
        _uiState.update { it.copy(rmsLevel = normalized) }
    }

    fun onPartialTranscript(text: String) {
        _uiState.update { it.copy(liveTranscript = text) }
    }

    fun onMicPermissionDenied() {
        _uiState.update { it.copy(status = PracticeStatus.PermissionDenied) }
    }

    fun onNoSpeech() {
        _uiState.update {
            if (it.status == PracticeStatus.Listening) it.copy(status = PracticeStatus.Idle) else it
        }
    }

    fun onVoiceError(message: String) {
        _uiState.update { it.copy(status = PracticeStatus.Error, errorMessage = message) }
    }

    fun onVoiceResult(text: String) {
        _uiState.update {
            it.copy(
                status = PracticeStatus.Analyzing,
                liveTranscript = text,
                coachFeedback = null,
                coachError = null,
                coachLoading = false,
            )
        }
        viewModelScope.launch {
            val analysis = try {
                grammarService.analyze(text)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = PracticeStatus.Error,
                        errorMessage = "Something went wrong while analyzing your speech.",
                    )
                }
                return@launch
            }

            val entry = ConversationEntry(
                id = System.currentTimeMillis().toString(),
                timestamp = System.currentTimeMillis(),
                original = analysis.original,
                corrected = analysis.corrected,
                simplified = analysis.simplified,
                issueCount = analysis.issues.size,
            )
            val updatedHistory = historyRepo.saveEntry(entry)

            _uiState.update {
                it.copy(
                    status = PracticeStatus.Result,
                    result = analysis,
                    history = updatedHistory,
                    pendingSpeech = SpeechRequest(System.currentTimeMillis(), analysis.corrected),
                )
            }

            requestCoachFeedback(analysis)
        }
    }

    /** Runs alongside (not blocking) the rule-based feedback above — the practice result
     *  already showed by the time this resolves, this just layers a coach's commentary
     *  on top once it's ready. No-op if the user hasn't configured their own API key. */
    private fun requestCoachFeedback(analysis: AnalysisResult) {
        val apiKey = _uiState.value.apiKey ?: return
        _uiState.update { it.copy(coachLoading = true, coachError = null) }
        viewModelScope.launch {
            coachService.coach(apiKey, analysis).fold(
                onSuccess = { feedback ->
                    _uiState.update { it.copy(coachLoading = false, coachFeedback = feedback) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(coachLoading = false, coachError = error.message ?: "Coaching is unavailable right now.")
                    }
                },
            )
        }
    }

    fun consumeSpeechRequest() {
        _uiState.update { it.copy(pendingSpeech = null) }
    }

    fun replay(text: String) {
        _uiState.update { it.copy(pendingSpeech = SpeechRequest(System.currentTimeMillis(), text)) }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                status = PracticeStatus.Idle,
                liveTranscript = "",
                result = null,
                errorMessage = null,
                coachFeedback = null,
                coachError = null,
                coachLoading = false,
            )
        }
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch {
            val updated = historyRepo.deleteEntry(id)
            _uiState.update { it.copy(history = updated) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearAll()
            _uiState.update { it.copy(history = emptyList()) }
        }
    }
}
