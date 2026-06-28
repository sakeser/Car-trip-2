package com.cartrip.analyzer.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.MapType

/** The map render mode for the current satellite-toggle state, shared by every map. */
internal fun mapTypeFor(satellite: Boolean): MapType =
    if (satellite) MapType.HYBRID else MapType.NORMAL

/**
 * A small floating button to flip a map between the normal road view and satellite/aerial (hybrid).
 * Backed by [UiPrefs.satelliteMap] so the choice persists and every map updates together.
 */
@Composable
internal fun MapTypeToggle(satellite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shadowElevation = 2.dp
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (satellite) Icons.Filled.Map else Icons.Filled.Layers,
                contentDescription = if (satellite) "Switch to road map" else "Switch to satellite",
                tint = Color(0xFF1F2937)
            )
        }
    }
}
