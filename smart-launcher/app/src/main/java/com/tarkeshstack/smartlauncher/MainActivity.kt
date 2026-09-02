package com.tarkeshstack.smartlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tarkeshstack.smartlauncher.capture.ShareIntentParser
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.ui.BrowseForLinkScreen
import com.tarkeshstack.smartlauncher.ui.CommandManagerScreen
import com.tarkeshstack.smartlauncher.ui.GetLinkScreen
import com.tarkeshstack.smartlauncher.ui.SearchScreen
import com.tarkeshstack.smartlauncher.ui.theme.SmartAppLauncherTheme
import com.tarkeshstack.smartlauncher.voice.VoiceInputController
import com.tarkeshstack.smartlauncher.voice.VoiceOutputController

private enum class Screen { Search, Commands, GetLink, Browse }

private const val RELISTEN_DELAY_MS = 350L
private const val PAUSE_STOP_GRACE_MS = 1200L

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var voiceController: VoiceInputController? = null
    private var voiceOutputController: VoiceOutputController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Snapshot taken at the moment of onPause, so onResume can put an in-progress
     *  listening session back the way it was. */
    private var wasListeningBeforePause = false
    private var pendingPauseStop: Runnable? = null

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            viewModel.onVoiceError("Microphone permission denied")
        } else {
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = VoiceInputController(
            context = this,
            onResult = viewModel::onVoiceResult,
            onListeningChanged = viewModel::onListeningChanged,
            onError = viewModel::onVoiceError,
        )
        voiceOutputController = VoiceOutputController(this)

        handleShareIntent(intent)

        setContent {
            SmartAppLauncherTheme {
                val state by viewModel.uiState.collectAsState()
                var screen by remember { mutableStateOf(Screen.Search) }
                var browseQuery by remember { mutableStateOf<String?>(null) }

                // A link shared in from another app should take you straight to where
                // you finish turning it into a command, not leave you on the search screen.
                LaunchedEffect(state.pendingCapturedLink) {
                    if (state.pendingCapturedLink != null) screen = Screen.Commands
                }

                // Voice replies are always on: speak the result of a voice-initiated turn,
                // then re-arm the mic so a hands-free back-and-forth can keep going.
                LaunchedEffect(state.pendingSpeech) {
                    val request = state.pendingSpeech ?: return@LaunchedEffect
                    viewModel.consumeSpeechRequest()
                    voiceOutputController?.speak(request.text) {
                        // Wait a beat after our own speech ends so the mic doesn't pick up
                        // its tail as the next thing said.
                        mainHandler.postDelayed({ startListening() }, RELISTEN_DELAY_MS)
                    }
                }

                when (screen) {
                    Screen.Search -> SearchScreen(
                        state = state,
                        viewModel = viewModel,
                        onRequestContactsPermission = {
                            requestContactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                        },
                        onMicTapped = {
                            if (hasMicPermission()) {
                                startListening()
                            } else {
                                requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOpenCommandManager = { screen = Screen.Commands },
                    )
                    Screen.Commands -> CommandManagerScreen(
                        commands = state.customCommands,
                        pendingCapturedLink = state.pendingCapturedLink,
                        onConsumeCapturedLink = viewModel::consumeCapturedLink,
                        onAdd = viewModel::addCustomCommand,
                        onDelete = viewModel::deleteCustomCommand,
                        onGetLink = { screen = Screen.GetLink },
                        onBack = { screen = Screen.Search },
                    )
                    Screen.GetLink -> GetLinkScreen(
                        allApps = state.allApps,
                        onLinkChosen = { link ->
                            viewModel.onLinkCaptured(link)
                            screen = Screen.Commands
                        },
                        onBrowseForLink = { query ->
                            browseQuery = query
                            screen = Screen.Browse
                        },
                        onBack = { screen = Screen.Commands },
                    )
                    Screen.Browse -> BrowseForLinkScreen(
                        initialQuery = browseQuery,
                        onCapture = { url ->
                            viewModel.onLinkCaptured(CapturedLink(uri = url, sourcePackage = null))
                            screen = Screen.Commands
                        },
                        onBack = { screen = Screen.GetLink },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        wasListeningBeforePause = viewModel.uiState.value.isListening
        // Leaving the app is exactly what happens the instant a command opens another app
        // (e.g. "open uber") — stopping the mic immediately there used to look like it had
        // died, and could cut a session off before Android even resumed us. So don't stop
        // right away: give it a short grace window, and onResume below cancels the pending
        // stop outright if we're back before it fires — a normal app-switch never audibly
        // touches the mic. Only a longer stay away actually stops it.
        val stopRunnable = Runnable { voiceController?.stopListening() }
        pendingPauseStop = stopRunnable
        mainHandler.postDelayed(stopRunnable, PAUSE_STOP_GRACE_MS)
    }

    override fun onResume() {
        super.onResume()
        pendingPauseStop?.let(mainHandler::removeCallbacks)
        pendingPauseStop = null
        if (wasListeningBeforePause && !viewModel.uiState.value.isListening) {
            startListening()
        }
    }

    override fun onDestroy() {
        pendingPauseStop?.let(mainHandler::removeCallbacks)
        voiceController?.destroy()
        voiceOutputController?.destroy()
        super.onDestroy()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!hasMicPermission()) return
        voiceController?.startListening()
    }

    private fun handleShareIntent(intent: Intent?) {
        val captured = intent?.let { ShareIntentParser.extractDeepLink(it, referrerPackageName()) } ?: return
        viewModel.onLinkCaptured(captured)
    }

    /** The package of the app the user shared *from*, when the OS/that app supplied it. */
    private fun referrerPackageName(): String? {
        val ref = referrer
        return if (ref?.scheme == "android-app") ref.host else null
    }
}
