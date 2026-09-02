package com.tarkeshstack.smartlauncher.ui

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A minimal in-app browser for finding a deep link when the target app has no Share
 * option at all. Works for any app with a web presence, independent of what that app
 * chooses to expose: browse to the item on the mobile web, tap "Use this page's link",
 * done. Many apps' websites use the same https:// URL as their App Link, so this often
 * opens straight back into the native app too.
 */
@Composable
fun BrowseForLinkScreen(
    onCapture: (String) -> Unit,
    onBack: () -> Unit,
    /** When set (e.g. an app's name), starts already searching for that instead of a
     *  blank Google homepage — so picking an app elsewhere in the app lands you here
     *  already looking for its site, without ever leaving Smart Launcher. */
    initialQuery: String? = null,
) {
    var addressBarText by remember {
        mutableStateOf(initialQuery?.let { "$it official site" } ?: "https://www.google.com")
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    OutlinedTextField(
                        value = addressBarText,
                        onValueChange = { addressBarText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { webView?.loadUrl(normalizeUrl(addressBarText)) },
                        ),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { webView?.url?.let(onCapture) },
                    ) {
                        Text("Use this page's link")
                    }
                }
                Text(
                    "Search or browse to the app's website version of what you want, then tap above.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            addressBarText = url.orEmpty()
                        }
                    }
                    loadUrl(normalizeUrl(addressBarText))
                    webView = this
                }
            },
        )
    }
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(" ") || !trimmed.contains(".") -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        else -> "https://$trimmed"
    }
}
