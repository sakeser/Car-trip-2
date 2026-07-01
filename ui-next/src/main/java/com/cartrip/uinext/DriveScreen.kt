package com.cartrip.uinext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripSummary

/**
 * Drive tab (spec's 1st tab) — the recording home. **Recording itself is still a read-only placeholder** (live
 * recording + auto-start stay in the legacy app until a `RecordingController` engine-api gateway + M1 land), but
 * the tab now leads with a quick, interpretation-first read of your driving: your last drive's plain-English
 * quality and a this-week glance, both read-only from the shared [TripSummary]s. ASCII source (Cp1252 trap).
 */
@Composable
internal fun DriveScreen(trips: List<TripSummary>?, onOpenTrip: (Long) -> Unit) {
    val lastDrive = remember(trips) { trips.orEmpty().firstOrNull { it.isDrive } }
    val week = remember(trips) { trips.orEmpty().inWindow(RecencyWindow.WEEK, System.currentTimeMillis()).windowSummary() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        // Last drive — lead with the plain-English quality, support with the numbers. Tap to open.
        lastDrive?.let { LastDriveCard(it, onClick = { onOpenTrip(it.id) }) }
        // This week glance.
        if (week.driveCount > 0) ThisWeekCard(week)

        PlaceholderCard(
            title = "Recording",
            body = "Live recording + auto-start are handled in the current app for now. This tab will host the " +
                "premium recording flow once the engine exposes a recording gateway.",
        )
    }
}

@Composable
private fun LastDriveCard(trip: TripSummary, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "LAST DRIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Interpretation first (the Drive-Quality headline), then the supporting facts.
                Text(
                    trip.driveQuality ?: "Recorded drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${formatStart(trip.startEpochMs)} $MIDDOT ${formatKm(trip.distanceMeters)} $MIDDOT ${formatDuration(trip.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trip.smoothnessScore?.let { score ->
                ScoreChip(score)
                Spacer(Modifier.width(12.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThisWeekCard(summary: TripWindowSummary) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "THIS WEEK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val drives = if (summary.driveCount == 1) "1 drive" else "${summary.driveCount} drives"
            Text(
                "$drives $MIDDOT ${"%.0f".format(summary.totalKm)} km",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summary.avgSmoothness?.let {
                Text(
                    "Smoothness averaging $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Coming soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
