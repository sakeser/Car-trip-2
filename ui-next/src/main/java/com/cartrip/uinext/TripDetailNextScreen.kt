package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary

/**
 * :ui-next trip detail - loads one [TripSummary] via [TripRepository.getTrip] and shows only the basic fields
 * in a premium card. No maps / scores / labels / charts / export / sync / recording. Engine access via
 * com.cartrip.engine.api.* only.
 */
@Composable
fun TripDetailNextScreen(tripId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trip by produceState<TripSummary?>(initialValue = null, tripId) {
        value = repo.getTrip(tripId)
    }

    NextScaffold(title = "Trip", onBack = onBack) { padding ->
        val t = trip
        if (t == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Trip #${t.id}", style = MaterialTheme.typography.titleLarge)
                        HorizontalDivider()
                        DetailRow("Start", formatStart(t.startEpochMs))
                        DetailRow("Distance", formatKm(t.distanceMeters))
                        DetailRow("Duration", formatDuration(t.durationSeconds))

                        // Driving Intelligence: the conditional "Drive Quality" headline + the Smoothness (style)
                        // and Demand (context) pillars. Efficiency needs a vehicle profile the engine-api mapper
                        // doesn't hold yet, so it's added later via a vehicle gateway.
                        val dq = t.driveQuality
                        if (dq != null) {
                            HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "DRIVE QUALITY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(dq, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                t.smoothnessScore?.let { s ->
                                    PillarRow("Smoothness", t.smoothnessBand) { ScoreChip(s) }
                                }
                                t.stressScore?.let { s ->
                                    PillarRow("Demand", t.stressBand) { StressChip(s) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** A Driving Intelligence pillar row: label on the left, a score chip + band word on the right. Shared with
 *  the Health screen ([InsightsContent]). */
@Composable
internal fun PillarRow(label: String, band: String?, chip: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            chip()
            band?.let {
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
