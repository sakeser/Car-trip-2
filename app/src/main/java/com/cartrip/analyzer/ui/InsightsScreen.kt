package com.cartrip.analyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.data.TripEntity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val completed = trips
        .filter { it.analyzed && it.endTime > 0 }
        .sortedBy { it.startTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (completed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Record a completed trip to see trends.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24L * 60L * 60L * 1000L
        var window by remember { mutableStateOf(InsightWindow.DAYS30) }
        val windowTrips = when (window) {
            InsightWindow.DAYS30 -> completed.filter { it.startTime >= now - thirtyDays }
            InsightWindow.KM500 -> recentByDistance(completed, 500_000.0)
            InsightWindow.ALL -> completed
        }.ifEmpty { completed.takeLast(1) }
        val wScores = windowTrips.map { TripScores.from(it) }
        val safetySeries = Series("Safety", Color(0xFF22C55E), wScores.map { it.safety.toFloat() })
        val comfortSeries = Series("Comfort", Color(0xFF38BDF8), wScores.map { it.comfort.toFloat() })
        val speedSeries = Series("Speed", Color(0xFFF59E0B), wScores.map { (it.speed ?: it.overall).toFloat() })
        val best = windowTrips.maxByOrNull { it.smoothness }
        val worst = windowTrips.minByOrNull { it.smoothness }
        val longest = windowTrips.maxByOrNull { it.distanceM }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { WindowSelector(window) { window = it } }

            item { GoogleVsYouHero(windowTrips) }

            item {
                SectionTitle("Scores over ${window.label.lowercase()}")
                MultiSeriesChart(
                    title = "Safety · Comfort · Speed",
                    series = listOf(safetySeries, comfortSeries, speedSeries),
                    yMin = 0f,
                    yMax = 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { SectionTitle("Metrics over ${window.label.lowercase()}") }
            items(miniStatSpecs(windowTrips, wScores).chunked(2)) { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowSpecs.forEach { MiniStatCard(it, Modifier.weight(1f)) }
                    if (rowSpecs.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                SectionTitle("Standouts")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    best?.let {
                        InsightTripCard("Best score", it, onClick = { onOpenTrip(it.id) })
                    }
                    worst?.let {
                        InsightTripCard("Needs attention", it, onClick = { onOpenTrip(it.id) })
                    }
                    longest?.let {
                        InsightTripCard("Longest drive", it, onClick = { onOpenTrip(it.id) })
                    }
                }
            }
        }
    }
}

private enum class InsightWindow(val label: String) {
    DAYS30("30 days"), KM500("500 km"), ALL("All time")
}

/** Most-recent trips whose cumulative distance covers [meters], returned in chronological order. */
private fun recentByDistance(completed: List<TripEntity>, meters: Double): List<TripEntity> {
    val out = ArrayList<TripEntity>()
    var sum = 0.0
    for (t in completed.asReversed()) {
        out.add(t)
        sum += t.distanceM
        if (sum >= meters) break
    }
    return out.asReversed()
}

private class StatSpec(val title: String, val value: String, val series: List<Float>, val color: Color)

private fun miniStatSpecs(trips: List<TripEntity>, scores: List<TripScores>): List<StatSpec> {
    fun avg(l: List<Float>) = if (l.isEmpty()) 0.0 else l.map { it.toDouble() }.average()
    val overall = scores.map { it.overall.toFloat() }
    val dist = trips.map { (it.distanceM / 1000.0).toFloat() }
    val spd = trips.map { (it.avgMovingSpeedMps * 3.6).toFloat() }
    val ev = trips.map { (it.hardBrakeCount + it.hardAccelCount + it.hardCornerCount).toFloat() }
    return listOf(
        StatSpec("Overall score", avg(overall).roundToInt().toString(), overall, Color(0xFF22C55E)),
        StatSpec("Avg speed", "${avg(spd).roundToInt()} km/h", spd, Color(0xFF38BDF8)),
        StatSpec("Distance / trip", String.format(java.util.Locale.US, "%.1f km", avg(dist)), dist, Color(0xFF14B8A6)),
        StatSpec("Hard events / trip", String.format(java.util.Locale.US, "%.1f", avg(ev)), ev, Color(0xFFF59E0B))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowSelector(selected: InsightWindow, onSelect: (InsightWindow) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InsightWindow.values().forEach { w ->
            FilterChip(
                selected = w == selected,
                onClick = { onSelect(w) },
                label = { Text(w.label) }
            )
        }
    }
}

@Composable
private fun GoogleVsYouHero(trips: List<TripEntity>) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val withEta = trips.filter { it.googleEtaTrafficS > 0 && it.durationS > 0 }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("You vs Google Maps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = onContainer)
            if (withEta.isEmpty()) {
                Text(
                    "No Google estimates in this window yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            } else {
                val wins = withEta.count { it.durationS < it.googleEtaTrafficS }
                val winRate = (wins * 100.0 / withEta.size).roundToInt()
                val avgMargin = withEta.map { (it.googleEtaTrafficS - it.durationS) / 60.0 }.average()
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$winRate%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = onContainer)
                    Text(
                        "faster than Google\non $wins of ${withEta.size} trips",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                MiniSparkline(
                    values = withEta.map { ((it.googleEtaTrafficS - it.durationS) / 60.0).toFloat() },
                    color = onContainer,
                    zeroBaseline = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    String.format(java.util.Locale.US, "Avg %+.1f min vs estimate  ·  above the line = you were faster", avgMargin),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer
                )
            }
        }
    }
}

@Composable
private fun MiniStatCard(spec: StatSpec, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spec.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(spec.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MiniSparkline(values = spec.series, color = spec.color, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InsightTripCard(title: String, trip: TripEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(
                    Format.dateTime(trip.startTime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${Format.distance(trip.distanceM)}  ${Format.duration(trip.durationS)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                trip.smoothness.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TripStats.scoreColor(trip.smoothness)
            )
        }
    }
}

private data class TripStats(
    val tripCount: Int,
    val distanceM: Double,
    val movingS: Double,
    val avgScore: Double,
    val hardEvents: Int
) {
    fun avgScoreText(): String =
        if (tripCount == 0) "-" else avgScore.roundToInt().toString()

    fun eventsPer100KmText(): String {
        if (distanceM <= 0.0) return "-"
        return String.format(java.util.Locale.US, "%.1f", hardEvents / (distanceM / 1000.0) * 100.0)
    }

    fun scoreColor(): Color = scoreColor(avgScore.roundToInt())

    companion object {
        fun from(trips: List<TripEntity>): TripStats {
            val distance = trips.sumOf { it.distanceM }
            val events = trips.sumOf { it.hardBrakeCount + it.hardAccelCount + it.hardCornerCount }
            val score = if (trips.isEmpty()) 0.0 else trips.map { it.smoothness }.average()
            return TripStats(
                tripCount = trips.size,
                distanceM = distance,
                movingS = trips.sumOf { it.movingS },
                avgScore = score,
                hardEvents = events
            )
        }

        fun scoreColor(score: Int): Color = when {
            score >= 80 -> Color(0xFF22C55E)
            score >= 60 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }
    }
}

private fun trendText(recent: TripStats, previous: TripStats): String {
    if (recent.tripCount == 0) return "No completed trips in the last 30 days."
    if (previous.tripCount == 0) return "No previous 30-day window yet for comparison."
    val scoreDelta = (recent.avgScore - previous.avgScore).roundToInt()
    val distanceDeltaKm = (recent.distanceM - previous.distanceM) / 1000.0
    val scoreText = when {
        scoreDelta > 0 -> "score up $scoreDelta"
        scoreDelta < 0 -> "score down ${-scoreDelta}"
        else -> "score unchanged"
    }
    return String.format(
        java.util.Locale.US,
        "Compared with the prior 30 days: %s, distance %+,.1f km.",
        scoreText,
        distanceDeltaKm
    )
}
