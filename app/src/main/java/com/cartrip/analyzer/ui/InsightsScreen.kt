package com.cartrip.analyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.data.TripEntity
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val vehicle = VehiclePrefs.load(LocalContext.current)
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
        val averages = ScoreAverages.from(wScores)
        val best = windowTrips.maxByOrNull { it.smoothness }
        val worst = windowTrips.minByOrNull { it.smoothness }
        val longest = windowTrips.maxByOrNull { it.distanceM }
        // Cross-trip recurring-event hotspots (grows with data; loaded off the main thread).
        val hotspots by produceState(initialValue = emptyList<EventHotspots.Hotspot>()) {
            value = runCatching { viewModel.loadEventHotspots() }.getOrDefault(emptyList())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { WindowSelector(window) { window = it } }

            item { DriveScoreHero(averages, windowTrips.size, window.label) }

            item { GoogleVsYouHero(windowTrips) }

            item { WhenYouDriveCard(windowTrips) }

            item {
                SectionTitle("Score trends")
                ScoreTrendsCard(wScores, window.label)
            }

            item { SectionTitle("Health metrics") }
            items(miniStatSpecs(windowTrips, wScores).chunked(2)) { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowSpecs.forEach { MiniStatCard(it, Modifier.weight(1f)) }
                    if (rowSpecs.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            run {
                val fuel = FuelInsights.summarize(windowTrips, vehicle)
                if (fuel.hasData) {
                    item {
                        SectionTitle("Fuel & cost")
                        FuelSection(fuel, vehicle)
                    }
                }
            }

            if (hotspots.isNotEmpty()) {
                item {
                    SectionTitle("Recurring spots")
                    RecurringSpotsCard(hotspots.take(6))
                }
            }

            item {
                SectionTitle("Highlights")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    best?.let {
                        InsightTripCard("Best drive score", it, onClick = { onOpenTrip(it.id) })
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

private data class ScoreAverages(
    val drive: Int,
    val safety: Int,
    val comfort: Int,
    val pace: Int?
) {
    companion object {
        fun from(scores: List<TripScores>): ScoreAverages {
            fun avg(values: List<Int>): Int =
                if (values.isEmpty()) 0 else values.average().roundToInt()
            val paces = scores.mapNotNull { it.speed }
            return ScoreAverages(
                drive = avg(scores.map { it.overall }),
                safety = avg(scores.map { it.safety }),
                comfort = avg(scores.map { it.comfort }),
                pace = paces.takeIf { it.isNotEmpty() }?.let(::avg)
            )
        }
    }
}

private class StatSpec(
    val title: String,
    val value: String,
    val caption: String,
    val series: List<Float>,
    val color: Color
)

private fun miniStatSpecs(trips: List<TripEntity>, scores: List<TripScores>): List<StatSpec> {
    fun avg(l: List<Float>) = if (l.isEmpty()) 0.0 else l.map { it.toDouble() }.average()
    val overall = scores.map { it.overall.toFloat() }
    val safety = scores.map { it.safety.toFloat() }
    val comfort = scores.map { it.comfort.toFloat() }
    val dist = trips.map { (it.distanceM / 1000.0).toFloat() }
    val eventRate = trips.map {
        val km = max(0.3, it.distanceM / 1000.0)
        ((it.hardBrakeCount + it.hardAccelCount + it.hardCornerCount) / km * 100.0).toFloat()
    }
    val paceVsGoogle = trips
        .filter { it.googleEtaTrafficS > 0.0 && it.durationS > 0.0 && !TripKind.isLikelyNonDrive(it) }
        .map { ((it.googleEtaTrafficS - it.durationS) / 60.0).toFloat() }
    // Accelerometer-fusion trends.
    val roughRoad = trips.map { (it.roughRoadPct * 100.0).toFloat() }
    val potholesPer100 = trips.map { (it.potholeCount / max(0.3, it.distanceM / 1000.0) * 100.0).toFloat() }
    val harshStops = trips.map { it.harshStopCount.toFloat() }
    val peakG = trips.map { it.peakGForce.toFloat() }

    val out = mutableListOf(
        StatSpec("Drive score", avg(overall).roundToInt().toString(), "Blended trip health", overall, Color(0xFF0EA5E9)),
        StatSpec("Safety", avg(safety).roundToInt().toString(), "Braking, turns, speed", safety, Color(0xFF10B981)),
        StatSpec("Comfort", avg(comfort).roundToInt().toString(), "Smoothness and idle", comfort, Color(0xFF38BDF8)),
        StatSpec("Distance / trip", String.format(Locale.US, "%.1f km", avg(dist)), "Average drive length", dist, Color(0xFF14B8A6)),
        StatSpec("Hard events / 100 km", String.format(Locale.US, "%.1f", avg(eventRate)), "Lower is better", eventRate, Color(0xFFF59E0B)),
        StatSpec("Rough road", String.format(Locale.US, "%.0f%%", avg(roughRoad)), "Bumpy / vibrating road", roughRoad, Color(0xFFF59E0B)),
        StatSpec("Potholes / 100 km", String.format(Locale.US, "%.1f", avg(potholesPer100)), "Big bumps detected", potholesPer100, Color(0xFF78716C)),
        StatSpec("Harsh stops / trip", String.format(Locale.US, "%.1f", avg(harshStops)), "Jerky stops", harshStops, Color(0xFFEF4444)),
        StatSpec("Peak g-force", String.format(Locale.US, "%.2fg", avg(peakG)), "Strongest jolt", peakG, Color(0xFF8B5CF6))
    )
    if (paceVsGoogle.isNotEmpty()) {
        val margin = avg(paceVsGoogle)
        out += StatSpec(
            "Pace vs traffic",
            String.format(Locale.US, "%+.1f min", margin),
            "Positive means faster",
            paceVsGoogle,
            if (margin >= 0.0) Color(0xFF22C55E) else Color(0xFFEF4444)
        )
    }
    return listOf(
        *out.toTypedArray()
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
private fun DriveScoreHero(averages: ScoreAverages, tripCount: Int, windowLabel: String) {
    val driveColor = TripScores.color(averages.drive)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DriveScoreRing(score = averages.drive, color = driveColor)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Drive score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$tripCount trips - $windowLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        scoreBand(averages.drive),
                        style = MaterialTheme.typography.titleSmall,
                        color = driveColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScorePill("Safety", averages.safety, Color(0xFF10B981), Modifier.weight(1f))
                ScorePill("Comfort", averages.comfort, Color(0xFF38BDF8), Modifier.weight(1f))
                ScorePill("Pace", averages.pace, Color(0xFF8B5CF6), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DriveScoreRing(score: Int, color: Color) {
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(118.dp)) {
        Canvas(modifier = Modifier.size(118.dp)) {
            val stroke = 13f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (score / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                "drive",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScorePill(label: String, value: Int?, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$label ${value?.toString() ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun scoreBand(score: Int): String = when {
    score >= 90 -> "Excellent driving health"
    score >= 80 -> "Strong driving health"
    score >= 65 -> "Good, with room to smooth out"
    score >= 50 -> "Needs attention"
    else -> "High-risk pattern"
}


@Composable
private fun GoogleVsYouHero(trips: List<TripEntity>) {
    val withEta = trips.filter { it.googleEtaTrafficS > 0 && it.durationS > 0 && !TripKind.isLikelyNonDrive(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You vs traffic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (withEta.isEmpty()) {
                Text(
                    "No traffic comparisons in this window yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val wins = withEta.count { it.durationS < it.googleEtaTrafficS }
                val winRate = (wins * 100.0 / withEta.size).roundToInt()
                // Per trip: minutes vs Google's typical estimate (positive = you were faster).
                val margins = withEta.map { (it.googleEtaTrafficS - it.durationS) / 60.0 }
                val avgMargin = margins.average()
                val totalHours = withEta.sumOf { it.durationS } / 3600.0
                val perHour = if (totalHours > 0) margins.sum() / totalHours else 0.0
                val marginColor = if (avgMargin >= 0.0) Color(0xFF22C55E) else Color(0xFFEF4444)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$winRate%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = marginColor)
                    Text(
                        "of trips beat\nusual traffic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                // Diverging bars: each trip's minutes saved (green) or lost (red) vs Google, with the
                // average as a dashed line.
                DivergingBarChart(
                    values = margins.map { it.toFloat() },
                    average = avgMargin.toFloat(),
                    minScale = 3f,        // don't exaggerate sub-3-min differences
                    scalePercentile = 0.85f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    String.format(Locale.US, "Avg %+.1f min/trip (%+.1f min/hr driven) vs Google", avgMargin, perHour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "When you drive": trips bucketed by daypart with how much and how safely you drove each. */
@Composable
private fun WhenYouDriveCard(trips: List<TripEntity>) {
    if (trips.isEmpty()) return
    val buckets = remember(trips) {
        DrivingTimes.summarize(
            trips.map { DrivingTimes.Entry(it.startTime, TripScores.from(it).safety, it.distanceM / 1000.0) }
        )
    }
    val active = buckets.filter { it.tripCount > 0 }
    if (active.isEmpty()) return
    val maxCount = active.maxOf { it.tripCount }
    val mostDriven = active.maxByOrNull { it.tripCount }
    val safest = active.filter { it.avgSafety != null }.maxByOrNull { it.avgSafety!! }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("When you drive", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            active.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(b.part.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(70.dp))
                    Box(
                        modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(b.tripCount.toFloat() / maxCount)
                                .height(10.dp).clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        "${b.tripCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        b.avgSafety?.toString() ?: "-",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = b.avgSafety?.let { TripScores.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(34.dp)
                    )
                }
            }
            if (mostDriven != null && safest != null) {
                Text(
                    "Most drives ${mostDriven.part.label.lowercase()}, safest ${safest.part.label.lowercase()} (bars = trips, right = avg Safety)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Score trends, re-imagined: one row per score (icon + current average + 0-100 bar + a trend chip
 * comparing the later half of the window to the earlier half) instead of four overlapping lines.
 */
@Composable
private fun ScoreTrendsCard(scores: List<TripScores>, windowLabel: String) {
    if (scores.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            ScoreTrendRow("Drive", Color(0xFF0EA5E9), Icons.Filled.Star, scores.map { it.overall.toFloat() })
            ScoreTrendRow("Safety", Color(0xFF22C55E), Icons.Filled.Shield, scores.map { it.safety.toFloat() })
            ScoreTrendRow("Comfort", Color(0xFF38BDF8), Icons.Filled.Weekend, scores.map { it.comfort.toFloat() })
            ScoreTrendRow("Pace", Color(0xFF8B5CF6), Icons.Filled.Timer, scores.mapNotNull { it.speed?.toFloat() })
            Text(
                "Arrows compare the later half of ${windowLabel.lowercase()} with the earlier half.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreTrendRow(label: String, color: Color, icon: ImageVector, values: List<Float>) {
    val avg = if (values.isEmpty()) null else values.average().roundToInt()
    val delta = trendDelta(values)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        avg?.toString() ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (avg != null) color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (avg != null) TrendChip(delta)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(((avg ?: 0) / 100f)).height(7.dp)
                        .clip(RoundedCornerShape(4.dp)).background(color)
                )
            }
        }
    }
}

@Composable
private fun TrendChip(delta: Int) {
    val up = Color(0xFF22C55E)
    val down = Color(0xFFEF4444)
    val flat = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, c, txt) = when {
        delta >= 1 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, up, "+$delta")
        delta <= -1 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, down, "$delta")
        else -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, flat, "steady")
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(14.dp))
        Text(txt, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c)
    }
}

/** Change in a score series: average of the later half minus the earlier half (0 if too few points). */
private fun trendDelta(values: List<Float>): Int {
    if (values.size < 4) return 0
    val half = values.size / 2
    val first = values.take(half).average()
    val second = values.takeLast(half).average()
    return (second - first).roundToInt()
}

@Composable
private fun MiniStatCard(spec: StatSpec, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(spec.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(spec.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(spec.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun FuelSection(s: FuelInsights.Summary, v: FuelEstimator.Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelStat(String.format(Locale.US, "$%,.0f", s.totalCost), "Total cost", Modifier.weight(1f))
                FuelStat(String.format(Locale.US, "%,.0f L", s.totalLitres), "Fuel used", Modifier.weight(1f))
                FuelStat(String.format(Locale.US, "$%.2f", s.avgCostPerKm), "per km", Modifier.weight(1f))
                FuelStat(String.format(Locale.US, "%.1f", s.avgL100), "L/100km", Modifier.weight(1f))
            }
            Text(
                "Estimated for ${v.label} at " + String.format(Locale.US, "$%.2f/L", v.pricePerL) +
                    " · ${s.drives} drives",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.costPerKm.size >= 2) {
                TimeSeriesChart("Cost per km", s.costPerKm, "$/km", Color(0xFF22C55E))
            }
            if (s.cumulativeCost.size >= 2) {
                TimeSeriesChart("Spend over time", s.cumulativeCost, "$", Color(0xFF0EA5E9))
            }
        }
    }
}

@Composable
private fun FuelStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RecurringSpotsCard(hotspots: List<EventHotspots.Hotspot>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "The same thing keeps happening here across your drives.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hotspots.forEach { h ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (h.where.isNotEmpty()) "${h.kind} · ${h.where}" else h.kind,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "on ${h.trips} drives" + if (h.count > h.trips) " · ${h.count} times" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${h.trips}×",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightTripCard(title: String, trip: TripEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    "${Format.distance(trip.distanceM)}  |  ${Format.duration(trip.durationS)}",
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
