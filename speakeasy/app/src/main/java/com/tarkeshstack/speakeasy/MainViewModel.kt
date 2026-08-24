package com.tarkeshstack.speakeasy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarkeshstack.speakeasy.data.HistoryRepository
import com.tarkeshstack.speakeasy.interpret.TranslationService
import com.tarkeshstack.speakeasy.model.InterpretStatus
import com.tarkeshstack.speakeasy.model.InterpretationEntry
import com.tarkeshstack.speakeasy.model.InterpretationResult
import com.tarkeshstack.speakeasy.model.Language
import com.tarkeshstack.speakeasy.model.SpeechRequest
import com.tarkeshstack.speakeasy.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val activeTab: Tab = Tab.Interpret,
    val status: InterpretStatus = InterpretStatus.Idle,
    val liveTranscript: String = "",
    val rmsLevel: Float = 0f,
    val isListening: Boolean = false,
    /** Null means "auto-detect" — the speech recognizer falls back to the device's
     *  default recognition language, and on-device language identification detects the
     *  actual spoken language from the resulting text when translating. */
    val sourceLanguage: Language? = null,
    val targetLanguage: Language = Language.English,
    val result: InterpretationResult? = null,
    val history: List<InterpretationEntry> = emptyList(),
    val errorMessage: String? = null,
    val pendingSpeech: SpeechRequest? = null,
    val playbackError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepo = HistoryRepository(application)
    private val translationService = TranslationService()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(history = historyRepo.loadAll()) }
        }
    }

    fun setTab(tab: Tab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSourceLanguage(language: Language?) {
        _uiState.update { it.copy(sourceLanguage = language) }
    }

    fun setTargetLanguage(language: Language) {
        _uiState.update { it.copy(targetLanguage = language) }
    }

    fun onStartListening() {
        _uiState.update {
            it.copy(status = InterpretStatus.Listening, liveTranscript = "", result = null, errorMessage = null)
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
        _uiState.update { it.copy(status = InterpretStatus.PermissionDenied) }
    }

    fun onNoSpeech() {
        _uiState.update {
            if (it.status == InterpretStatus.Listening) it.copy(status = InterpretStatus.Idle) else it
        }
    }

    fun onVoiceError(message: String) {
        _uiState.update { it.copy(status = InterpretStatus.Error, errorMessage = message) }
    }

    fun onVoiceResult(text: String) {
        val target = _uiState.value.targetLanguage
        val sourceHint = _uiState.value.sourceLanguage
        _uiState.update { it.copy(status = InterpretStatus.Translating, liveTranscript = text) }

        viewModelScope.launch {
            translationService.translate(text, sourceHint, target).fold(
                onSuccess = { translation ->
                    val result = InterpretationResult(
                        originalText = text,
                        sourceLanguage = translation.detectedLanguage,
                        autoDetected = sourceHint == null,
                        translatedText = translation.translatedText,
                        targetLanguage = target,
                    )
                    val entry = InterpretationEntry(
                        id = System.currentTimeMillis().toString(),
                        timestamp = System.currentTimeMillis(),
                        originalText = text,
                        sourceLanguage = translation.detectedLanguage,
                        translatedText = translation.translatedText,
                        targetLanguage = target,
                    )
                    val updatedHistory = historyRepo.saveEntry(entry)
                    _uiState.update {
                        it.copy(
                            status = InterpretStatus.Result,
                            result = result,
                            history = updatedHistory,
                            pendingSpeech = SpeechRequest(System.currentTimeMillis(), translation.translatedText, target),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(status = InterpretStatus.Error, errorMessage = error.message ?: "Couldn't translate that.")
                    }
                },
            )
        }
    }

    fun consumeSpeechRequest() {
        _uiState.update { it.copy(pendingSpeech = null) }
    }

    fun replay(text: String, language: Language) {
        _uiState.update { it.copy(pendingSpeech = SpeechRequest(System.currentTimeMillis(), text, language)) }
    }

    fun onPlaybackFailed(language: Language) {
        _uiState.update { it.copy(playbackError = "Voice playback isn't available in ${language.displayName} on this device.") }
    }

    fun consumePlaybackError() {
        _uiState.update { it.copy(playbackError = null) }
    }

    fun reset() {
        _uiState.update {
            it.copy(status = InterpretStatus.Idle, liveTranscript = "", result = null, errorMessage = null)
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
