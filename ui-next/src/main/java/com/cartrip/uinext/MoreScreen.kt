package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * More tab (spec's 5th tab) — a settings/hub menu. **Rough / read-only placeholder:** it lays out the intended
 * settings architecture (Vehicle & fuel, Units & display, Connected features, Privacy & data, About) as inert
 * rows so the product shape is visible. Each destination lands later behind an engine-api `SettingsStore` /
 * gateway; nothing here writes state yet. ASCII source (Cp1252 trap).
 */
private data class MoreItem(val title: String, val subtitle: String)

private val MORE_ITEMS = listOf(
    MoreItem("Vehicle & fuel", "Set your car to estimate fuel and cost"),
    MoreItem("Units & display", "Distance units, theme, trip icon"),
    MoreItem("Connected features", "Cloud sync, traffic + map data, integrations"),
    MoreItem("Privacy & data", "What's stored, export, and delete"),
    MoreItem("About", "Version, licenses, and credits"),
)

@Composable
internal fun MoreScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(MORE_ITEMS) { item -> MoreRow(item) }
        item {
            Text(
                "CarTrip premium preview $MIDDOT :ui-next",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
