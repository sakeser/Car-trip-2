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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary

/**
 * :ui-next trip list - a tiny premium list from [TripRepository.observeTrips], rows tappable -> detail
 * (see [TripsNextRoot]). Three states: loading / empty / list. No labels / scores / maps / charts / recording.
 * Engine access via com.cartrip.engine.api.* only; ASCII source (Cp1252 trap).
 */
@Composable
fun TripListNextScreen(onOpenTrip: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trips: List<TripSummary>? by repo.observeTrips().collectAsState(initial = null)

    NextScaffold(title = "Trips", onBack = onBack) { padding ->
        val list = trips
        when {
            list == null -> Centered(padding) { CircularProgressIndicator() }
            list.isEmpty() -> Centered(padding) {
                Text(
                    "No trips yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { trip ->
                    TripRow(trip, onClick = { onOpenTrip(trip.id) })
                }
            }
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { content() }
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
            }
            trip.stressScore?.let { score ->
                StressChip(score)
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
