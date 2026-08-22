package com.tarkeshstack.smartlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LauncherPrimaryDark,
    onPrimary = LauncherOnPrimaryDark,
    primaryContainer = LauncherPrimaryContainerDark,
    onPrimaryContainer = LauncherOnPrimaryContainerDark,
    secondary = LauncherSecondaryDark,
    onSecondary = LauncherOnSecondaryDark,
    background = LauncherBackgroundDark,
    surface = LauncherSurfaceDark,
    surfaceVariant = LauncherSurfaceVariantDark,
    onSurface = LauncherOnSurfaceDark,
    onBackground = LauncherOnSurfaceDark,
    outline = LauncherOutlineDark,
)

private val LightColors = lightColorScheme(
    primary = LauncherPrimaryLight,
    onPrimary = LauncherOnPrimaryLight,
    primaryContainer = LauncherPrimaryContainerLight,
    onPrimaryContainer = LauncherOnPrimaryContainerLight,
    secondary = LauncherSecondaryLight,
    onSecondary = LauncherOnSecondaryLight,
    background = LauncherBackgroundLight,
    surface = LauncherSurfaceLight,
    surfaceVariant = LauncherSurfaceVariantLight,
    onSurface = LauncherOnSurfaceLight,
    onBackground = LauncherOnSurfaceLight,
    outline = LauncherOutlineLight,
)

@Composable
fun SmartAppLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LauncherTypography, content = content)
}
