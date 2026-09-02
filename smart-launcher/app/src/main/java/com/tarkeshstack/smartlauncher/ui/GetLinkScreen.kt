package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestion
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions

/** One flow for getting a deep link: search for an app (with its icon shown, not just a
 *  name) in a searchable picker, then either use one of its popular links or add a new
 *  one via the built-in in-page browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetLinkScreen(
    allApps: List<AppInfo>,
    onLinkChosen: (CapturedLink) -> Unit,
    onBrowseForLink: (initialQuery: String?, sourcePackage: String?) -> Unit,
    onBack: () -> Unit,
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var appPickerOpen by remember { mutableStateOf(false) }

    val suggestionsForApp = remember(selectedApp) {
        val app = selectedApp ?: return@remember emptyList()
        DeepLinkSuggestions.all.filter { it.packageName == app.packageName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get a link") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Which app?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Search for it, then use a popular link or add a new one — " +
                                "all without losing your place here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { appPickerOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val app = selectedApp
                            if (app != null) {
                                Image(
                                    bitmap = remember(app.packageName) {
                                        app.icon.toBitmap(width = 48, height = 48).asImageBitmap()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(app.label, modifier = Modifier.weight(1f))
                            } else {
                                Icon(Icons.Filled.Apps, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Search for an app…", modifier = Modifier.weight(1f))
                            }
                        }

                        selectedApp?.let { app ->
                            Spacer(Modifier.height(16.dp))
                            if (suggestionsForApp.isEmpty()) {
                                Text(
                                    "No popular link for ${app.label} yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "Popular for ${app.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                suggestionsForApp.forEach { suggestion ->
                                    SuggestionCard(
                                        suggestion = suggestion,
                                        onUse = {
                                            onLinkChosen(
                                                CapturedLink(
                                                    uri = suggestion.uriTemplate,
                                                    sourcePackage = suggestion.packageName,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onBrowseForLink(app.label, app.packageName) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Public, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add a new link")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (appPickerOpen) {
        AppPickerDialog(
            apps = allApps,
            onDismiss = { appPickerOpen = false },
            onSelect = { app ->
                selectedApp = app
                appPickerOpen = false
            },
        )
    }
}

@Composable
private fun SuggestionCard(suggestion: DeepLinkSuggestion, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    suggestion.uriTemplate,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onUse) { Text("Use this") }
        }
    }
}
