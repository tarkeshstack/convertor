package com.tarkeshstack.smartlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LauncherBlueLight,
    background = LauncherBackground,
    surface = LauncherSurface,
    onSurface = LauncherOnSurface,
    onBackground = LauncherOnSurface,
)

private val LightColors = lightColorScheme(
    primary = LauncherBlue,
)

@Composable
fun SmartAppLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LauncherTypography, content = content)
}
