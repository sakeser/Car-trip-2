package com.cartrip.uinext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The recency filter chips (24h / 3d / 7d / 30d / All), horizontally scrollable so they never clip. Shared by
 * the Trips list and the Health overview so both windows use one consistent control. Pure presentation over the
 * engine-api-agnostic [RecencyWindow] enum. ASCII source (Cp1252 trap).
 */
@Composable
internal fun RecencyFilterRow(selected: RecencyWindow, onSelect: (RecencyWindow) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecencyWindow.values().forEach { w ->
            FilterChip(
                selected = w == selected,
                onClick = { onSelect(w) },
                label = { Text(w.label) },
            )
        }
    }
}
