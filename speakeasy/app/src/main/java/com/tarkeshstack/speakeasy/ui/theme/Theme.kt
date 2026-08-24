package com.tarkeshstack.speakeasy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Colors = lightColorScheme(
    primary = SpeakEasyPrimary,
    onPrimary = SpeakEasyOnPrimary,
    primaryContainer = SpeakEasyPrimaryContainer,
    onPrimaryContainer = SpeakEasyOnPrimaryContainer,
    secondary = SpeakEasySecondary,
    onSecondary = SpeakEasyOnSecondary,
    background = SpeakEasyBackground,
    surface = SpeakEasySurface,
    surfaceVariant = SpeakEasySurfaceVariant,
    onSurface = SpeakEasyOnSurface,
    onSurfaceVariant = SpeakEasyOnSurfaceVariant,
    onBackground = SpeakEasyOnSurface,
    outline = SpeakEasyOutline,
    error = SpeakEasyError,
)

@Composable
fun SpeakEasyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = SpeakEasyTypography, content = content)
}
