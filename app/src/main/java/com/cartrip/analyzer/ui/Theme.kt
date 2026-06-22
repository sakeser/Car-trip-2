package com.cartrip.analyzer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF38BDF8)
private val AccentDark = Color(0xFF0EA5E9)
private val Good = Color(0xFF22C55E)
val WarnColor = Color(0xFFF59E0B)
val BadColor = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06283D),
    secondary = Good,
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111A2B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1B2740),
    onSurfaceVariant = Color(0xFFB6C2D9)
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    secondary = Good
)

@Composable
fun CarTripTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
