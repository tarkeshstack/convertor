package com.tarkeshstack.smartlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tarkeshstack.smartlauncher.model.SystemShortcut
import com.tarkeshstack.smartlauncher.model.SystemShortcuts

/** A modal list of built-in Android system shortcuts (Wi-Fi, Bluetooth, all settings) that
 *  can be saved as a command the same way an app deep link can. */
@Composable
fun SystemShortcutPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (SystemShortcut) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(20.dp)) {
                Text("System shortcuts", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(SystemShortcuts.all) { shortcut ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(shortcut) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            Column {
                                Text(shortcut.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    shortcut.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
