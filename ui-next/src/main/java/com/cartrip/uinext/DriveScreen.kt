package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Drive tab — the recording home (spec's 1st tab). **Rough / read-only placeholder** for now: it shows the
 * shape (a big "start" affordance + a recording-status card) but does NOT record. Live recording lands later
 * via a `RecordingController` engine-api gateway + the engine self-describing manifest (M1); until then the
 * legacy app owns recording. Clearly non-functional so nothing here can touch the running recorder.
 * ASCII source (Cp1252 trap).
 */
@Composable
internal fun DriveScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Big start affordance (placeholder — not wired).
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Text("Ready to drive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Mount your phone and go. Sensor + GPS capture and trip analysis run automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PlaceholderCard(
            title = "Recording",
            body = "Live recording + auto-start are handled in the current app for now. This tab will host the " +
                "premium recording flow once the engine exposes a recording gateway.",
        )
        PlaceholderCard(
            title = "Auto-record",
            body = "Hands-free start/stop on connect (charger / Bluetooth) — surfaced here later, read from the " +
                "engine's recording state.",
        )
    }
}

/** A rough, clearly-inert section placeholder used across the roughed-in tabs. */
@Composable
internal fun PlaceholderCard(title: String, body: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row2(title)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Row2(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Coming soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}
