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
import androidx.compose.material.icons.filled.Mic
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
import com.tarkeshstack.speakeasy.ui.PracticeScreen
import com.tarkeshstack.speakeasy.ui.theme.SpeakEasyTheme
import com.tarkeshstack.speakeasy.voice.AudioPlaybackController
import com.tarkeshstack.speakeasy.voice.AudioRecorderController
import com.tarkeshstack.speakeasy.voice.VoiceInputController
import com.tarkeshstack.speakeasy.voice.VoiceOutputController

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var voiceController: VoiceInputController? = null
    private var voiceOutputController: VoiceOutputController? = null
    private var audioRecorder: AudioRecorderController? = null
    private var audioPlayback: AudioPlaybackController? = null

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

        audioRecorder = AudioRecorderController(this)
        audioPlayback = AudioPlaybackController()
        voiceController = VoiceInputController(
            context = this,
            onResult = ::handleVoiceResult,
            onPartialResult = viewModel::onPartialTranscript,
            onListeningChanged = viewModel::onListeningChanged,
            onVolumeChanged = viewModel::onRmsChanged,
            onRecognitionError = ::handleVoiceError,
            onNoSpeech = ::handleNoSpeech,
        )
        voiceOutputController = VoiceOutputController(this)

        setContent {
            SpeakEasyTheme {
                val state by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(state.pendingSpeech) {
                    val request = state.pendingSpeech ?: return@LaunchedEffect
                    viewModel.consumeSpeechRequest()
                    voiceOutputController?.speak(request.text)
                }

                LaunchedEffect(state.pendingPlayback) {
                    val request = state.pendingPlayback ?: return@LaunchedEffect
                    viewModel.consumePlaybackRequest()
                    audioPlayback?.play(request.filePath) { success ->
                        if (!success) viewModel.onPlaybackFailed()
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
                                selected = state.activeTab == Tab.Practice,
                                onClick = { viewModel.setTab(Tab.Practice) },
                                icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                                label = { Text("Practice") },
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
                            Tab.Practice -> PracticeScreen(
                                state = state,
                                onMicPress = ::onMicPress,
                                onCancel = ::stopListening,
                                onReplay = viewModel::replay,
                                onPlayRecording = viewModel::playRecording,
                                onOpenSettings = viewModel::openSettings,
                                onCloseSettings = viewModel::closeSettings,
                                onSaveApiKey = viewModel::saveApiKey,
                                onRequestSummary = viewModel::requestSessionSummary,
                                onCloseSummary = viewModel::closeSummary,
                                onNewSession = viewModel::startNewSession,
                            )
                            Tab.History -> HistoryScreen(
                                history = state.history,
                                onDelete = viewModel::deleteHistoryEntry,
                                onClearAll = viewModel::clearHistory,
                                onReplay = viewModel::replay,
                                onPlayRecording = viewModel::playRecording,
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
        audioPlayback?.stop()
        audioRecorder?.start()
        voiceController?.startListening()
    }

    private fun stopListening() {
        audioRecorder?.stop()
        voiceController?.stopListening()
    }

    private fun handleVoiceResult(text: String) {
        val audioPath = audioRecorder?.stop()
        viewModel.onVoiceResult(text, audioPath)
    }

    private fun handleVoiceError(message: String) {
        audioRecorder?.stop()
        viewModel.onVoiceError(message)
    }

    private fun handleNoSpeech() {
        audioRecorder?.stop()
        viewModel.onNoSpeech()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceController?.destroy()
        voiceOutputController?.destroy()
        audioRecorder?.stop()
        audioPlayback?.stop()
    }
}
