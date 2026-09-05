package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tarkeshstack.smartlauncher.ui.theme.KeywordInputEmpty
import com.tarkeshstack.smartlauncher.ui.theme.KeywordInputFilled

/** A one-field prompt for the keyword a command's link still needs before it can run —
 *  the command's link itself keeps its placeholder, so it stays a reusable template, but
 *  the value is remembered and passed back in as [initialKeyword] next time, surviving
 *  closing and reopening the app.
 *  [hint], when known for the target app, shows a concrete example of what to type (e.g.
 *  "a username — your own, or someone else's" for an Instagram profile) so it's never a
 *  guess what belongs there. */
@Composable
fun QuickFillDialog(
    commandLabel: String,
    hint: String?,
    initialKeyword: String?,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
) {
    var keyword by remember { mutableStateOf(initialKeyword.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("What for $commandLabel?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("Type a keyword…") },
                    // The keyboard icon itself is light red while empty and light green
                    // once something's typed — the field stays plain white either way.
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Keyboard,
                            contentDescription = null,
                            tint = if (keyword.isNotBlank()) KeywordInputFilled else KeywordInputEmpty,
                        )
                    },
                    supportingText = if (hint != null) {
                        { Text(hint) }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { if (keyword.isNotBlank()) onRun(keyword) },
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onRun(keyword) },
                    enabled = keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Run")
                }
            }
        }
    }
}
