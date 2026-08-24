package com.tarkeshstack.speakeasy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tarkeshstack.speakeasy.model.Tab
import com.tarkeshstack.speakeasy.ui.HistoryScreen
import com.tarkeshstack.speakeasy.ui.InterpreterScreen
import com.tarkeshstack.speakeasy.ui.theme.SpeakEasyTheme
import com.tarkeshstack.speakeasy.voice.VoiceInputController
import com.tarkeshstack.speakeasy.voice.VoiceOutputController

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var voiceController: VoiceInputController? = null
    private var voiceOutputController: VoiceOutputController? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onStartListening()
            startListening()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = VoiceInputController(
            context = this,
            onResult = viewModel::onVoiceResult,
            onPartialResult = viewModel::onPartialTranscript,
            onListeningChanged = viewModel::onListeningChanged,
            onVolumeChanged = viewModel::onRmsChanged,
            onRecognitionError = viewModel::onVoiceError,
            onNoSpeech = viewModel::onNoSpeech,
        )
        voiceOutputController = VoiceOutputController(this)

        setContent {
            SpeakEasyTheme {
                val state by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(state.pendingSpeech) {
                    val request = state.pendingSpeech ?: return@LaunchedEffect
                    viewModel.consumeSpeechRequest()
                    voiceOutputController?.speak(request.text, request.language.bcp47) { success ->
                        if (!success) viewModel.onPlaybackFailed(request.language)
                    }
                }

                LaunchedEffect(state.playbackError) {
                    val message = state.playbackError ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(message)
                    viewModel.consumePlaybackError()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = state.activeTab == Tab.Interpret,
                                onClick = { viewModel.setTab(Tab.Interpret) },
                                icon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                                label = { Text("Interpret") },
                            )
                            NavigationBarItem(
                                selected = state.activeTab == Tab.History,
                                onClick = { viewModel.setTab(Tab.History) },
                                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                                label = { Text("History") },
                            )
                        }
                    },
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        when (state.activeTab) {
                            Tab.Interpret -> InterpreterScreen(
                                state = state,
                                onMicPress = ::onMicPress,
                                onCancel = ::stopListening,
                                onReplay = viewModel::replay,
                                onSetSourceLanguage = viewModel::setSourceLanguage,
                                onSetTargetLanguage = viewModel::setTargetLanguage,
                                onOpenSettings = viewModel::openSettings,
                                onCloseSettings = viewModel::closeSettings,
                                onSaveApiKey = viewModel::saveApiKey,
                            )
                            Tab.History -> HistoryScreen(
                                history = state.history,
                                onDelete = viewModel::deleteHistoryEntry,
                                onClearAll = viewModel::clearHistory,
                                onReplay = viewModel::replay,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onMicPress() {
        val state = viewModel.uiState.value
        if (state.isListening) {
            stopListening()
            return
        }
        if (state.apiKey == null) {
            viewModel.openSettings()
            return
        }
        viewModel.reset()
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.onStartListening()
            startListening()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        voiceOutputController?.stop()
        val sourceLanguage = viewModel.uiState.value.sourceLanguage
        voiceController?.startListening(sourceLanguage?.bcp47)
    }

    private fun stopListening() {
        voiceController?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceController?.destroy()
        voiceOutputController?.destroy()
    }
}
