package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripSummary
import kotlin.math.roundToInt

/**
 * Health-tab content: the premium **Driving Intelligence** overview over all scorable drives — average
 * Smoothness (style) and Demand/Load (context), plus the Drive-Quality mix. Aggregated purely from the
 * [TripSummary] fields already produced by the engine (`smoothnessScore` / `stressScore` / `driveQuality`),
 * so it needs no vehicle profile — **Efficiency is intentionally omitted here** until a vehicle gateway lands
 * (same reason the trip detail omits it). Band words are derived locally: the UI module owns its own
 * presentation and never imports `analysis.*` (the engine-boundary rule). ASCII source (Cp1252 trap).
 */
@Composable
internal fun InsightsContent(trips: List<TripSummary>?) {
    when {
        trips == null -> Centered { CircularProgressIndicator() }
        else -> {
            val drives = trips.filter { it.smoothnessScore != null }
            if (drives.isEmpty()) {
                Centered {
                    Text(
                        "No scorable drives yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val avgSmooth = drives.mapNotNull { it.smoothnessScore }.average().roundToInt()
                val avgDemand = drives.mapNotNull { it.stressScore }.average().roundToInt()
                val mix = drives.mapNotNull { it.driveQuality }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { OverviewCard(drives.size, avgSmooth, avgDemand) }
                    if (mix.isNotEmpty()) item { MixCard(mix) }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(driveCount: Int, avgSmooth: Int, avgDemand: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "DRIVING INTELLIGENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "How you drove & how hard it was",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "$driveCount ${if (driveCount == 1) "scorable drive" else "scorable drives"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            PillarRow("Smoothness", smoothnessBand(avgSmooth)) { ScoreChip(avgSmooth) }
            PillarRow("Demand", demandBand(avgDemand)) { StressChip(avgDemand) }
        }
    }
}

@Composable
private fun MixCard(mix: List<Map.Entry<String, Int>>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Your drive mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for (entry in mix) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(entry.key, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${entry.value}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Local band words (mirror the engine's DrivingIntelligence / StressScore thresholds; the boundary forbids
 *  :ui-next importing analysis.*). */
private fun smoothnessBand(s: Int): String = when {
    s >= 85 -> "Very smooth"
    s >= 70 -> "Smooth"
    s >= 55 -> "A bit rough"
    else -> "Rough"
}

private fun demandBand(s: Int): String = when {
    s < 25 -> "Calm"
    s < 45 -> "Moderate"
    s < 65 -> "Busy"
    else -> "High"
}
