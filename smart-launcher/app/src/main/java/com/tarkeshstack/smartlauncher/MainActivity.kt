package com.tarkeshstack.smartlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tarkeshstack.smartlauncher.ui.SearchScreen
import com.tarkeshstack.smartlauncher.ui.theme.SmartAppLauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartAppLauncherTheme {
                val state by viewModel.uiState.collectAsState()
                SearchScreen(
                    state = state,
                    viewModel = viewModel,
                    onRequestContactsPermission = {
                        requestContactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                    },
                )
            }
        }
    }
}
