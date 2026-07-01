package com.cartrip.uinext

import androidx.compose.foundation.clickable
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
 * More tab (spec's 5th tab) — a settings/hub menu. Rows drill in to a [MoreDetailScreen] section (nav route
 * `more/{key}`). "About" has real read-only content today; the rest show their intent + a "Soon" marker until
 * they land behind an engine-api `SettingsStore` / gateway (nothing here writes state yet). ASCII source.
 */
internal data class MoreItem(val key: String, val title: String, val subtitle: String, val ready: Boolean = false)

internal val MORE_ITEMS = listOf(
    MoreItem("vehicle", "Vehicle & fuel", "Set your car to estimate fuel and cost"),
    MoreItem("units", "Units & display", "Distance units, theme, trip icon"),
    MoreItem("connected", "Connected features", "Cloud sync, traffic + map data, integrations"),
    MoreItem("privacy", "Privacy & data", "What's stored, export, and delete"),
    MoreItem("about", "About", "Version, licenses, and credits", ready = true),
)

@Composable
internal fun MoreScreen(onOpenSection: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(MORE_ITEMS) { item -> MoreRow(item, onClick = { onOpenSection(item.key) }) }
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
private fun MoreRow(item: MoreItem, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
            if (!item.ready) {
                Text(
                    "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
