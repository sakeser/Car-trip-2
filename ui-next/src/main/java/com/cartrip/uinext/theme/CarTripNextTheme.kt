package com.cartrip.uinext.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * First-pass premium theme for the :ui-next flow - a restrained teal accent on deep neutral surfaces, so the
 * new product reads as its own thing rather than inheriting the legacy app's MaterialTheme. Deliberately
 * minimal (no custom fonts/shapes yet); grow it as the redesign matures.
 */

private val Accent = Color(0xFF3DD6C4)       // calm teal
private val AccentDeep = Color(0xFF0E8C7E)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201C),
    secondary = Color(0xFF7FD1C6),
    background = Color(0xFF0B0F12),
    onBackground = Color(0xFFE6EAEC),
    surface = Color(0xFF11161A),
    onSurface = Color(0xFFE6EAEC),
    surfaceVariant = Color(0xFF1B242B),
    onSurfaceVariant = Color(0xFF9BA8B0),
    outline = Color(0xFF2A343C),
)

private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Color.White,
    secondary = AccentDeep,
    background = Color(0xFFF4F8F7),
    onBackground = Color(0xFF111819),
    surface = Color(0xFFFBFDFC),
    onSurface = Color(0xFF111819),
    surfaceVariant = Color(0xFFE6EDEC),
    onSurfaceVariant = Color(0xFF55636A),
    outline = Color(0xFFC4CFCD),
)

private val Base = Typography()
private val NextTypography = Base.copy(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun CarTripNextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NextTypography,
        content = content,
    )
}
