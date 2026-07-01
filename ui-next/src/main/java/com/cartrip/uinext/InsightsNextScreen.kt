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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripSummary

/**
 * Health-tab content: a windowed premium **Driving Intelligence** overview over the scorable drives in the
 * selected recency window — average Smoothness (style) and Demand/Load (context), a Smoothness trend, and the
 * Drive-Quality mix. Aggregated purely from the [TripSummary] fields the engine already produces
 * (`smoothnessScore` / `stressScore` / `driveQuality`) via the pure [drivingHealth] helper — no scoring logic
 * and no vehicle profile (**Efficiency is intentionally omitted** until a vehicle gateway lands). Band words are
 * derived locally: the UI module owns its presentation and never imports `analysis.*` (the boundary rule).
 * ASCII source (Cp1252 trap).
 */
@Composable
internal fun InsightsContent(trips: List<TripSummary>?) {
    if (trips == null) {
        Centered { CircularProgressIndicator() }
        return
    }

    var window by remember { mutableStateOf(RecencyWindow.ALL) }
    val health = remember(trips, window) {
        trips.inWindow(window, System.currentTimeMillis()).drivingHealth()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RecencyFilterRow(selected = window, onSelect = { window = it })
        if (health.driveCount == 0) {
            Centered {
                Text(
                    if (window == RecencyWindow.ALL) "No scorable drives yet"
                    else "No scorable drives in the last ${window.label}",
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
                item { OverviewCard(health) }
                if (health.smoothnessTrend.size >= 2) item { TrendCard(health.smoothnessTrend) }
                if (health.mix.isNotEmpty()) item { MixCard(health.mix) }
            }
        }
    }
}

@Composable
private fun OverviewCard(health: DrivingHealthSummary) {
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
                val drives = if (health.driveCount == 1) "scorable drive" else "scorable drives"
                Text(
                    "${health.driveCount} $drives $MIDDOT ${"%.1f".format(health.totalKm)} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            health.avgSmoothness?.let { PillarRow("Smoothness", smoothnessBand(it)) { ScoreChip(it) } }
            health.avgDemand?.let { PillarRow("Demand", demandBand(it)) { StressChip(it) } }
        }
    }
}

@Composable
private fun TrendCard(trend: List<Int>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        SmoothnessTrend(values = trend, modifier = Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun MixCard(mix: List<Pair<String, Int>>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Your drive mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for ((verdict, count) in mix) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(verdict, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$count",
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
