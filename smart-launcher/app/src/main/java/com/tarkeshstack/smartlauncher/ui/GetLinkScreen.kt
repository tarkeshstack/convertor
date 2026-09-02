package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestion
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions

/** Two ways to get a deep link into the command form, both without ever leaving Smart
 *  Launcher: pick one of the popular, curated links for an app you already have
 *  installed, or open a chosen app's own website in the built-in browser below and
 *  grab the link from there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetLinkScreen(
    allApps: List<AppInfo>,
    onLinkChosen: (CapturedLink) -> Unit,
    onBrowseForLink: (initialQuery: String?) -> Unit,
    onBack: () -> Unit,
) {
    val installedSuggestions = remember(allApps) {
        DeepLinkSuggestions.all.filter { suggestion ->
            suggestion.packageName != null && allApps.any { it.packageName == suggestion.packageName }
        }
    }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var appPickerOpen by remember { mutableStateOf(false) }

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
                        Text("Popular for your apps", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        if (installedSuggestions.isEmpty()) {
                            Text(
                                "None of your installed apps have a popular link ready yet — " +
                                    "find one below instead.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            installedSuggestions.forEach { suggestion ->
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
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Open an app's site here", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pick an app and browse its website right inside Smart Launcher — " +
                                "nothing opens outside this page. Find what you want and tap " +
                                "\"Use this page's link.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { appPickerOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedApp?.label ?: "Choose an app")
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onBrowseForLink(selectedApp?.label) },
                            enabled = selectedApp != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open ${selectedApp?.label ?: "its"} site here")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onBrowseForLink(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Don't know which app? Browse for it instead")
                }
                Spacer(Modifier.height(24.dp))
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${suggestion.appLabel} — ${suggestion.description}",
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
