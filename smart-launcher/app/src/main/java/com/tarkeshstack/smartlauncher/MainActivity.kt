package com.tarkeshstack.smartlauncher

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tarkeshstack.smartlauncher.ui.CommandManagerScreen
import com.tarkeshstack.smartlauncher.ui.SearchScreen
import com.tarkeshstack.smartlauncher.ui.theme.SmartAppLauncherTheme
import com.tarkeshstack.smartlauncher.voice.VoiceInputController

private enum class Screen { Search, Commands }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var voiceController: VoiceInputController? = null

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) voiceController?.startListening() else viewModel.onVoiceError("Microphone permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = VoiceInputController(
            context = this,
            onResult = { text ->
                viewModel.onQueryChanged(text)
                viewModel.runCommand()
            },
            onListeningChanged = viewModel::onListeningChanged,
            onError = viewModel::onVoiceError,
        )

        setContent {
            SmartAppLauncherTheme {
                val state by viewModel.uiState.collectAsState()
                var screen by remember { mutableStateOf(Screen.Search) }

                when (screen) {
                    Screen.Search -> SearchScreen(
                        state = state,
                        viewModel = viewModel,
                        onRequestContactsPermission = {
                            requestContactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                        },
                        onMicTapped = {
                            val granted = ContextCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                voiceController?.startListening()
                            } else {
                                requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOpenCommandManager = { screen = Screen.Commands },
                    )
                    Screen.Commands -> CommandManagerScreen(
                        commands = state.customCommands,
                        allApps = state.allApps,
                        onAdd = viewModel::addCustomCommand,
                        onDelete = viewModel::deleteCustomCommand,
                        onBack = { screen = Screen.Search },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        voiceController?.destroy()
        super.onDestroy()
    }
}
