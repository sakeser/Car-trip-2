package com.cartrip.analyzer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripEntity
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripViewModel,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val fuelProfile by VehicleFuelState.state.collectAsStateWithLifecycle()
    var showClearSamplesDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.All) }

    val heatPoints by produceState(initialValue = emptyList<AnalysisPointEntity>(), trips) {
        value = viewModel.loadHeatmapPoints(30)
    }
    val tripLabels by produceState(initialValue = emptyMap<Long, String>(), trips) {
        value = viewModel.loadTripLabels(trips)
    }

    val historyItems = remember(trips, tripLabels) {
        trips.map { trip ->
            TripHistoryItem(
                trip = trip,
                label = trip.name.ifBlank { tripLabels[trip.id] ?: "Trip" },
                scores = if (trip.endTime > 0) TripScores.from(trip) else null,
                comparison = historyComparison(trip)
            )
        }
    }
    val filteredItems = remember(historyItems, query, filter) {
        historyItems.filter { it.matches(query, filter) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trips.any { it.isSample }) {
                        TextButton(onClick = { showClearSamplesDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text("Samples")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            EmptyHistory(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HistoryHero(items = historyItems, fuelProfile = fuelProfile)
                }
                item {
                    HistorySearchAndFilters(
                        query = query,
                        onQueryChange = { query = it },
                        filter = filter,
                        onFilterChange = { filter = it }
                    )
                }
                item {
                    RouteMemoryCard(heatPoints = heatPoints)
                }

                if (filteredItems.isEmpty()) {
                    item { EmptyFilteredHistory() }
                } else {
                    var lastDate = ""
                    filteredItems.forEach { item ->
                        val date = Format.dateOnly(item.trip.startTime)
                        if (date != lastDate) {
                            item(key = "date-$date") {
                                HistoryDateHeader(date = date)
                            }
                            lastDate = date
                        }
                        item(key = item.trip.id) {
                            TripHistoryCard(
                                item = item,
                                fuelProfile = fuelProfile,
                                onClick = { onOpen(item.trip.id) },
                                onRename = { newName -> viewModel.renameTrip(item.trip.id, newName) },
                                onDelete = { viewModel.deleteTrip(item.trip.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearSamplesDialog) {
        AlertDialog(
            onDismissRequest = { showClearSamplesDialog = false },
            title = { Text("Clear sample trips?") },
            text = { Text("This removes only demo/sample trips. Your recorded trips are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSamplesDialog = false
                        viewModel.clearSampleTrips()
                    }
                ) {
                    Text("Clear samples", color = HistoryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSamplesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryHero(
    items: List<TripHistoryItem>,
    fuelProfile: VehicleFuelProfile
) {
    val finished = items.filter { it.trip.endTime > 0 && it.trip.distanceM > 0.0 }
    val totalDistance = finished.sumOf { it.trip.distanceM }
    val totalDuration = finished.sumOf { it.trip.durationS }
    val averageScore = finished.mapNotNull { it.scores?.overall }.takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
    val comparisons = finished.mapNotNull { it.comparison.deltaS }
    val comparisonTotal = comparisons.takeIf { it.isNotEmpty() }?.sum()
    val fuelEstimate = FuelCost.estimateTrips(finished.map { it.trip }, fuelProfile)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = HistoryInk,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "DRIVE LOG",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Text(
                    "Your driving patterns at a glance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${finished.size} analyzed trips, newest first",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryHeroMetric("Distance", if (totalDistance > 0.0) Format.tripDistance(totalDistance) else "--", Modifier.weight(1f))
                HistoryHeroMetric("Drive time", if (totalDuration > 0.0) Format.duration(totalDuration) else "--", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryHeroMetric("Avg score", averageScore?.toString() ?: "--", Modifier.weight(1f))
                if (fuelEstimate != null) {
                    HistoryHeroMetric("Est. fuel cost", FuelCost.money(fuelEstimate.cost), Modifier.weight(1f))
                } else {
                    HistoryHeroMetric("Vs expected", comparisonTotal?.let { signedHistoryMinutes(it) } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HistoryHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistorySearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            label = { Text("Search trips") }
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun RouteMemoryCard(heatPoints: List<AnalysisPointEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Route memory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Last 30 days of analyzed GPS routes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (heatPoints.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    TripHeatMap(
                        points = heatPoints,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "Route heat map appears after analyzed trips have GPS tracks.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripHistoryCard(
    item: TripHistoryItem,
    fuelProfile: VehicleFuelProfile,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showActionDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val trip = item.trip
    val finished = trip.endTime > 0
    val partial = trip.isPartialRecording()
    val eventCount = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
    val fuelEstimate = FuelCost.estimate(trip.distanceM, fuelProfile)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showActionDialog = true }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (trip.isSample) SampleBadge()
                        if (partial) PartialBadge()
                    }
                    Text(
                        "${Format.timeOfDay(trip.startTime)} - ${Format.dateOnly(trip.startTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.scores?.let {
                    ScoreRing(score = it.overall, ringSize = 58.dp)
                }
            }

            Text(
                item.comparison.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = item.comparison.color
            )

            if (partial) {
                Text(
                    trip.partialReasonText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = HistoryAmber
                )
            }

            if (finished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFact(Icons.Filled.DirectionsCar, "Distance", Format.tripDistance(trip.distanceM), Modifier.weight(1f))
                    HistoryFact(Icons.Filled.Timer, "Time", Format.tripMinutes(trip.durationS), Modifier.weight(1f))
                    HistoryFact(Icons.Filled.Speed, "Avg", Format.speedKmh(trip.avgMovingSpeedMps * 3.6), Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactSignal("Events", eventCount.toString(), item.eventColor, Modifier.weight(1f))
                    CompactSignal("Idle", idleHistoryText(trip.idleS), idleColor(trip), Modifier.weight(1f))
                    if (fuelEstimate != null) {
                        CompactSignal("Est. cost", FuelCost.money(fuelEstimate.cost), HistoryGreen, Modifier.weight(1f))
                    } else {
                        CompactSignal("Pace", item.comparison.shortLabel, item.comparison.color, Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = HistoryAmber)
                    Text(
                        "Trip has not finished analyzing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text(item.label) },
            text = { Text(Format.dateTime(trip.startTime)) },
            confirmButton = {
                TextButton(onClick = { showActionDialog = false; showRenameDialog = true }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActionDialog = false; showDeleteDialog = true }) {
                    Text("Delete", color = HistoryRed)
                }
            }
        )
    }

    if (showRenameDialog) {
        var draft by remember { mutableStateOf(trip.name.ifBlank { item.label }) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename trip") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Trip name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { showRenameDialog = false; onRename(draft) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this trip?") },
            text = { Text("${item.label} - ${Format.dateTime(trip.startTime)}") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = HistoryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactSignal(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryDateHeader(date: String) {
    Text(
        date,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Your drive log is ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Record a trip or preview sample trips from Home to see route memory, filters, scores, and cost estimates here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyFilteredHistory() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            "No trips match this view. Try a different search or filter.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SampleBadge() {
    Text(
        text = "DEMO",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF6366F1),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1A6366F1))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun PartialBadge() {
    Text(
        text = "PARTIAL",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = HistoryAmber,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HistoryAmber.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

private data class TripHistoryItem(
    val trip: TripEntity,
    val label: String,
    val scores: TripScores?,
    val comparison: HistoryComparison
) {
    val eventColor: Color
        get() {
            val events = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
            return when {
                events >= 6 -> HistoryRed
                events >= 2 -> HistoryAmber
                else -> HistoryGreen
            }
        }

    fun matches(query: String, filter: HistoryFilter): Boolean {
        val normalized = query.trim()
        val searchMatches = normalized.isBlank() ||
            label.contains(normalized, ignoreCase = true) ||
            Format.dateOnly(trip.startTime).contains(normalized, ignoreCase = true)

        val filterMatches = when (filter) {
            HistoryFilter.All -> true
            HistoryFilter.Delayed -> (comparison.deltaS ?: 0.0) > 60.0
            HistoryFilter.Saved -> (comparison.deltaS ?: 0.0) < -60.0
            HistoryFilter.Smooth -> (scores?.overall ?: 0) >= 85
            HistoryFilter.Samples -> trip.isSample
        }
        return searchMatches && filterMatches
    }
}

private data class HistoryComparison(
    val label: String,
    val shortLabel: String,
    val deltaS: Double?,
    val color: Color
)

private enum class HistoryFilter(val label: String) {
    All("All"),
    Delayed("Delayed"),
    Saved("Saved"),
    Smooth("Smooth"),
    Samples("Samples")
}

private fun historyComparison(trip: TripEntity): HistoryComparison {
    val expectedS = trip.googleEtaTrafficS
    val actualS = trip.durationS
    if (trip.etaSource.isBlank() || expectedS <= 0.0 || actualS <= 0.0) {
        return HistoryComparison(
            label = "Expected-route comparison not available yet",
            shortLabel = "--",
            deltaS = null,
            color = MaterialColorFallback
        )
    }
    val delta = actualS - expectedS
    val minutes = (abs(delta) / 60.0).roundToInt().coerceAtLeast(1)
    return when {
        delta > 60.0 -> HistoryComparison(
            label = "$minutes min slower than expected",
            shortLabel = "+${minutes}m",
            deltaS = delta,
            color = HistoryRed
        )
        delta < -60.0 -> HistoryComparison(
            label = "$minutes min faster than expected",
            shortLabel = "-${minutes}m",
            deltaS = delta,
            color = HistoryGreen
        )
        else -> HistoryComparison(
            label = "On expected pace",
            shortLabel = "Pace",
            deltaS = delta,
            color = HistoryGreen
        )
    }
}

private fun idleColor(trip: TripEntity): Color {
    val ratio = if (trip.durationS > 0.0) trip.idleS / trip.durationS else 0.0
    return when {
        ratio >= 0.25 -> HistoryRed
        ratio >= 0.10 -> HistoryAmber
        else -> HistoryGreen
    }
}

private fun idleHistoryText(seconds: Double): String =
    if (seconds <= 30.0) "0 min" else Format.tripMinutes(seconds)

private fun signedHistoryMinutes(seconds: Double): String {
    if (abs(seconds) < 60.0) return "On pace"
    val minutes = (abs(seconds) / 60.0).roundToInt().coerceAtLeast(1)
    return if (seconds > 0.0) "+${minutes}m" else "-${minutes}m"
}

private val HistoryInk = Color(0xFF10211F)
private val HistoryGreen = Color(0xFF16A34A)
private val HistoryAmber = Color(0xFFD97706)
private val HistoryRed = Color(0xFFDC2626)
private val MaterialColorFallback = Color(0xFF64748B)
