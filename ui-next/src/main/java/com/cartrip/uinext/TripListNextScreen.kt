package com.cartrip.uinext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripSummary

/**
 * Trips-tab content: recency filter chips + a window summary over a premium list of [TripSummary] rows
 * (loading / empty / list), rows tappable -> detail. The trip data is hoisted into the shell ([TripsNextRoot])
 * and passed in, so the Trips and Health tabs share one observation. Filtering / aggregation is pure :ui-next
 * logic on the DTO ([TripWindow]). No maps / charts / recording here. Engine access via com.cartrip.engine.api.*
 * only; ASCII source (Cp1252 trap).
 */
@Composable
internal fun TripListContent(trips: List<TripSummary>?, onOpenTrip: (Long) -> Unit) {
    if (trips == null) {
        Centered { CircularProgressIndicator() }
        return
    }
    if (trips.isEmpty()) {
        Centered {
            Text(
                "No trips yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var window by remember { mutableStateOf(RecencyWindow.ALL) }
    // Recompute the filtered view and its summary when the data or the selected window changes. "now" is taken
    // at filter time — recency windows don't need a live-ticking clock.
    val filtered = remember(trips, window) { trips.inWindow(window, System.currentTimeMillis()) }
    val summary = remember(filtered) { filtered.windowSummary() }

    Column(modifier = Modifier.fillMaxSize()) {
        RecencyFilterRow(selected = window, onSelect = { window = it })
        if (summary.driveCount > 0) WindowSummaryBar(summary)
        if (filtered.isEmpty()) {
            Centered {
                Text(
                    "No trips in the last ${window.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { trip ->
                    TripRow(trip, onClick = { onOpenTrip(trip.id) })
                }
            }
        }
    }
}

/** The recency filter chips (24h / 3d / 7d / 30d / All), horizontally scrollable so they never clip. */
@Composable
private fun RecencyFilterRow(selected: RecencyWindow, onSelect: (RecencyWindow) -> Unit) {
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

/** A compact one-line read of the filtered window: drive count, total distance, and (if scored) avg smoothness. */
@Composable
private fun WindowSummaryBar(summary: TripWindowSummary) {
    val drives = if (summary.driveCount == 1) "1 drive" else "${summary.driveCount} drives"
    val km = "%.1f km".format(summary.totalKm)
    val text = buildString {
        append(drives); append(" $MIDDOT "); append(km)
        summary.avgSmoothness?.let { append(" $MIDDOT smooth $it") }
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TripRow(trip: TripSummary, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(formatStart(trip.startEpochMs), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatKm(trip.distanceMeters)} $MIDDOT ${formatDuration(trip.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trip.isDrive) {
                    // Driving Intelligence verdict (the conditional Drive Quality headline). It already encodes
                    // both style and demand, so the row shows it and the Smoothness (style) number as the chip.
                    trip.driveQuality?.let { verdict ->
                        Text(
                            verdict,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // A walk / non-drive has no meaningful driving score — say so instead of showing an empty row.
                    Text(
                        "Walk $MIDDOT non-drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (trip.isDrive) {
                trip.smoothnessScore?.let { score ->
                    ScoreChip(score)
                    Spacer(Modifier.width(12.dp))
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
