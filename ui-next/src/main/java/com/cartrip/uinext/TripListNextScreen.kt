package com.cartrip.uinext

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripSummary

/**
 * Trips-tab content: a premium list of [TripSummary] rows (loading / empty / list), rows tappable -> detail.
 * The trip data is hoisted into the shell ([TripsNextRoot]) and passed in, so the Trips and Health tabs share
 * one observation. No labels / maps / charts / recording. Engine access via com.cartrip.engine.api.* only;
 * ASCII source (Cp1252 trap).
 */
@Composable
internal fun TripListContent(trips: List<TripSummary>?, onOpenTrip: (Long) -> Unit) {
    when {
        trips == null -> Centered { CircularProgressIndicator() }
        trips.isEmpty() -> Centered {
            Text(
                "No trips yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(trips, key = { it.id }) { trip ->
                TripRow(trip, onClick = { onOpenTrip(trip.id) })
            }
        }
    }
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
                // Driving Intelligence verdict (the conditional Drive Quality headline). It already encodes
                // both style and demand, so the row shows it here and the Smoothness (style) number as the chip.
                trip.driveQuality?.let { verdict ->
                    Text(
                        verdict,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
