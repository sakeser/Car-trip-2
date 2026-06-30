package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary

/**
 * :ui-next trip detail - loads one [TripSummary] via [TripRepository.getTrip] and shows only the basic fields
 * (id, date/time, distance, duration). Walking skeleton: no maps / scores / labels / charts / export / sync /
 * recording. Engine access via com.cartrip.engine.api.* only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailNextScreen(tripId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trip by produceState<TripSummary?>(initialValue = null, tripId) {
        value = repo.getTrip(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip detail (ui-next)") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        val t = trip
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (t == null) {
                Text("Loading...", style = MaterialTheme.typography.bodyMedium)
            } else {
                DetailRow("Trip", "#${t.id}")
                DetailRow("Start", formatStart(t.startEpochMs))
                DetailRow("Distance", formatKm(t.distanceMeters))
                DetailRow("Duration", formatDuration(t.durationSeconds))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
