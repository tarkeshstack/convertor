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
import com.tarkeshstack.smartlauncher.ui.SearchScreen
import com.tarkeshstack.smartlauncher.ui.theme.SmartAppLauncherTheme
import com.tarkeshstack.smartlauncher.voice.VoiceInputController
import com.tarkeshstack.smartlauncher.voice.VoiceOutputController
import com.tarkeshstack.smartlauncher.voice.WakePhraseDetector

private enum class Screen { Search, Commands, Browse }

private const val RELISTEN_DELAY_MS = 350L
private const val WAKE_RETRY_DELAY_MS = 400L

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var voiceController: VoiceInputController? = null
    private var voiceOutputController: VoiceOutputController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while the current listening session is a passive scan for "hey buddy" rather
     *  than actively taking down a real command. */
    private var isWakeCycle = false

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            viewModel.onVoiceError("Microphone permission denied")
            // If this denial was for turning wake word on, reflect that honestly in the switch.
            if (viewModel.uiState.value.wakeWordEnabled) viewModel.toggleWakeWord()
        } else if (viewModel.uiState.value.wakeWordEnabled) {
            startWakeListening()
        } else {
            startCommandListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = VoiceInputController(
            context = this,
            onResult = ::handleVoiceResult,
            onListeningChanged = viewModel::onListeningChanged,
            onError = viewModel::onVoiceError,
            onNoSpeech = {
                // Falls back to passive wake listening whenever wake word is on — not just
                // when the timed-out session was itself a wake scan. Without this, a command
                // session that re-armed after a voice-triggered turn (isWakeCycle = false)
                // would go silent forever the moment it timed out with nothing said, instead
                // of dropping back to listening for "hey buddy".
                if (viewModel.uiState.value.wakeWordEnabled) {
                    mainHandler.postDelayed({ startWakeListening() }, WAKE_RETRY_DELAY_MS)
                }
            },
        )
        voiceOutputController = VoiceOutputController(this)

        handleShareIntent(intent)

        setContent {
            SmartAppLauncherTheme {
                val state by viewModel.uiState.collectAsState()
                var screen by remember { mutableStateOf(Screen.Search) }

                // A link shared in from another app should take you straight to where
                // you finish turning it into a command, not leave you on the search screen.
                LaunchedEffect(state.pendingCapturedLink) {
                    if (state.pendingCapturedLink != null) screen = Screen.Commands
                }

                // Conversation mode: speak the result, then re-arm listening appropriately —
                // straight back to command listening if this turn came from voice, or back to
                // passive wake scanning if wake word is on and this was a typed command.
                LaunchedEffect(state.pendingSpeech) {
                    val request = state.pendingSpeech ?: return@LaunchedEffect
                    viewModel.consumeSpeechRequest()
                    voiceOutputController?.speak(request.text) {
                        // Wait a beat after our own speech ends so the mic doesn't pick up
                        // its tail as the next thing said.
                        when {
                            request.shouldRelisten ->
                                mainHandler.postDelayed({ startCommandListening() }, RELISTEN_DELAY_MS)
                            request.resumeWakeAfter ->
                                mainHandler.postDelayed({ startWakeListening() }, RELISTEN_DELAY_MS)
                        }
                    }
                }

                // Same idea when conversation mode is off (no speech to wait for).
                LaunchedEffect(state.pendingWakeResume) {
                    if (state.pendingWakeResume) {
                        viewModel.consumeWakeResume()
                        startWakeListening()
                    }
                }

                // Single place that starts/stops the passive "hey buddy" loop when the
                // setting is flipped, so toggling it is the only trigger needed.
                LaunchedEffect(state.wakeWordEnabled) {
                    if (state.wakeWordEnabled) {
                        if (hasMicPermission()) {
                            startWakeListening()
                        } else {
                            requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else if (isWakeCycle) {
                        voiceController?.stopListening()
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
                                startCommandListening()
                            } else {
                                requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOpenCommandManager = { screen = Screen.Commands },
                    )
                    Screen.Commands -> CommandManagerScreen(
                        commands = state.customCommands,
                        allApps = state.allApps,
                        speechEnabled = state.speechEnabled,
                        wakeWordEnabled = state.wakeWordEnabled,
                        onToggleConversationMode = viewModel::toggleConversationMode,
                        onToggleWakeWord = viewModel::toggleWakeWord,
                        pendingCapturedLink = state.pendingCapturedLink,
                        onConsumeCapturedLink = viewModel::consumeCapturedLink,
                        onAdd = viewModel::addCustomCommand,
                        onDelete = viewModel::deleteCustomCommand,
                        onBrowseForLink = { screen = Screen.Browse },
                        onBack = { screen = Screen.Search },
                    )
                    Screen.Browse -> BrowseForLinkScreen(
                        onCapture = { url ->
                            viewModel.onLinkCaptured(CapturedLink(uri = url, sourcePackage = null))
                            screen = Screen.Commands
                        },
                        onBack = { screen = Screen.Commands },
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
        // Wake-word listening only ever runs while this screen is in the foreground: Android
        // gives third-party apps no supported way to listen for a wake phrase — or launch
        // themselves — while backgrounded without a permanent foreground-service notification,
        // and even then auto-raising the app to the front isn't reliable across OEMs. So any
        // listening session (wake scan or an in-progress command) stops the moment you leave,
        // and onResume below picks the wake loop back up if it was on.
        voiceController?.stopListening()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value.wakeWordEnabled && !viewModel.uiState.value.isListening) {
            startWakeListening()
        }
    }

    override fun onDestroy() {
        voiceController?.destroy()
        voiceOutputController?.destroy()
        super.onDestroy()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startWakeListening() {
        if (!hasMicPermission()) return
        isWakeCycle = true
        voiceController?.startListening()
    }

    private fun startCommandListening() {
        isWakeCycle = false
        voiceController?.startListening()
    }

    private fun handleVoiceResult(text: String) {
        if (!isWakeCycle) {
            viewModel.onVoiceResult(text)
            return
        }
        when (val remainder = WakePhraseDetector.matchRemainder(text)) {
            null -> if (viewModel.uiState.value.wakeWordEnabled) startWakeListening()
            "" -> voiceOutputController?.speak("Yes?") {
                mainHandler.postDelayed({ startCommandListening() }, RELISTEN_DELAY_MS)
            }
            else -> viewModel.onVoiceResult(remainder)
        }
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
