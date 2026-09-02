package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tarkeshstack.smartlauncher.model.AppInfo
import com.tarkeshstack.smartlauncher.model.CapturedLink
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestion
import com.tarkeshstack.smartlauncher.model.DeepLinkSuggestions

/** Two ways to get a deep link into the command form: pick one of the popular, curated
 *  links for an app you already have installed, or get instructions (and a preview of
 *  what to expect) for sharing a link in from any app's own Share button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetLinkScreen(
    allApps: List<AppInfo>,
    onLinkChosen: (CapturedLink) -> Unit,
    onBrowseForLink: () -> Unit,
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
                                    "share one in below instead.",
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
                        Text("Which app?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { appPickerOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedApp?.label ?: "Choose an app")
                        }
                        Spacer(Modifier.height(16.dp))
                        StepsRow(appLabel = selectedApp?.label ?: "the app")
                        if (selectedApp != null) {
                            Spacer(Modifier.height(16.dp))
                            AppSharePreview(app = selectedApp!!)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This just sets up the instructions — you'll actually switch to " +
                                "that app to tap Share, then land straight back here with the " +
                                "link already filled in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onBrowseForLink,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("No Share option in that app? Browse for it instead")
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

/** A horizontal, at-a-glance version of the 3-step share flow, instead of stacking them
 *  as separate rows — so all three are visible without scrolling. */
@Composable
private fun StepsRow(appLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepChip(number = "1", label = "Open $appLabel", modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        StepChip(number = "2", label = "Tap Share", modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        StepChip(number = "3", label = "Come back", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepChip(number: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppSharePreview(app: AppInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = remember(app.packageName) {
                        app.icon.toBitmap(width = 64, height = 64).asImageBitmap()
                    },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Look for Share, then pick Smart Launcher",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
