package com.cartrip.uinext

import androidx.compose.foundation.clickable
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

/**
 * :ui-next trip list - a deliberately tiny list rendered from the engine's [TripRepository] ([TripSummary]).
 * Rows are tappable -> trip detail (see [TripsNextRoot]). Walking skeleton: real module, real data, minimal
 * row (date/time, distance, duration); no labels / scores / maps / charts / recording / polish. Reaches the
 * engine ONLY through com.cartrip.engine.api.* (guarded by EngineBoundaryTest); renders in the host theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListNextScreen(onOpenTrip: (Long) -> Unit, onBack: () -> Unit) {
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
                items(trips, key = { it.id }) { trip ->
                    TripRow(trip, onClick = { onOpenTrip(trip.id) })
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripSummary, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
