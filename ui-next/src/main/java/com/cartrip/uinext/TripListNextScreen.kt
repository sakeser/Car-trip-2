package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * First :ui-next screen - a deliberately tiny trip list rendered from the engine's [TripRepository]
 * ([TripSummary]). Walking skeleton: real module, real data, minimal row (date/time, distance, duration).
 * No labels / scores / maps / nav / recording / polish - those are later slices. Reaches the engine ONLY
 * through com.cartrip.engine.api.* (guarded by EngineBoundaryTest). Renders inside the host's MaterialTheme.
 *
 * Strings are kept ASCII on purpose: this Windows build mojibakes non-ASCII string literals in BOM-less
 * .kt files. If a real glyph is ever needed, build it from a code point, do not paste it in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListNextScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trips by repo.observeTrips().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips (ui-next)") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No trips yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trips, key = { it.id }) { TripRow(it) }
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripSummary) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                formatStart(trip.startEpochMs),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${formatKm(trip.distanceMeters)}  |  ${formatDuration(trip.durationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val startFormat = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())

private fun formatStart(epochMs: Long): String =
    if (epochMs <= 0L) "-" else startFormat.format(Date(epochMs))

private fun formatKm(meters: Double): String = "%.1f km".format(meters / 1000.0)

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
