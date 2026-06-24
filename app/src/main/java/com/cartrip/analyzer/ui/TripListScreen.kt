package com.cartrip.analyzer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripViewModel,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }
    val collapsedBuckets = remember { mutableStateListOf<TripBuckets.Bucket>() }
    // First tap selects (preview route on the frozen map); second tap on the same trip opens it.
    var selectedTripId by remember { mutableStateOf<Long?>(null) }
    val heatPoints by produceState(initialValue = emptyList<AnalysisPointEntity>(), trips) {
        value = viewModel.loadHeatmapPoints(30)
    }
    val tripLabels by produceState(initialValue = emptyMap<Long, String>(), trips) {
        value = viewModel.loadTripLabels(trips)
    }
    val selectedRoute by produceState(initialValue = emptyList<TrackPoint>(), selectedTripId) {
        value = selectedTripId?.let { viewModel.loadRoute(it) } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past trips") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trips.any { it.isSample }) {
                        TextButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text("Clear samples")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No trips yet. Record one from the home screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Frozen map: previews the selected trip's route, else the 30-day heatmap.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    if (selectedTripId != null && selectedRoute.size >= 2) {
                        TripMap(points = selectedRoute, events = emptyList(), modifier = Modifier.fillMaxSize())
                    } else if (heatPoints.isNotEmpty()) {
                        TripHeatMap(points = heatPoints, modifier = Modifier.fillMaxSize())
                    } else {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("No route data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    if (selectedTripId == null) "Tap a trip to preview its route · tap again to open"
                    else "Tap again to open · tap another to preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                val maxDurationS = trips.maxOfOrNull { it.durationS }?.coerceAtLeast(1.0) ?: 1.0
                val grouped = TripBuckets.group(trips)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { TripListHeader() }
                    var runningOffset = 0
                    grouped.forEach { (bucket, bucketTrips) ->
                        val startNumber = runningOffset
                        runningOffset += bucketTrips.size
                        val expanded = bucket !in collapsedBuckets
                        item(key = "hdr_${bucket.name}") {
                            SectionHeader(
                                label = bucket.label,
                                count = bucketTrips.size,
                                expanded = expanded,
                                onToggle = {
                                    if (bucket in collapsedBuckets) collapsedBuckets.remove(bucket)
                                    else collapsedBuckets.add(bucket)
                                }
                            )
                        }
                        if (expanded) {
                            itemsIndexed(bucketTrips, key = { _, t -> t.id }) { index, trip ->
                                TripRow(
                                    number = startNumber + index + 1,
                                    trip = trip,
                                    label = tripLabels[trip.id] ?: "Trip",
                                    maxDurationS = maxDurationS,
                                    selected = selectedTripId == trip.id,
                                    onClick = {
                                        if (selectedTripId == trip.id) onOpen(trip.id)
                                        else selectedTripId = trip.id
                                    },
                                    onRename = { newName -> viewModel.renameTrip(trip.id, newName) },
                                    onDelete = { viewModel.deleteTrip(trip.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear sample trips?") },
            text = { Text("Removes only the demo trips marked SAMPLE. Your real recorded trips are kept. (Delete a real trip by long-pressing it.)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearSampleTrips()
                    }
                ) {
                    Text("Clear samples", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(
    number: Int,
    trip: TripEntity,
    label: String,
    maxDurationS: Double,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val finished = trip.endTime != 0L
    val scores = if (finished) TripScores.from(trip) else null
    val partial = trip.isPartialRecording()
    val displayName = trip.name.ifBlank { label }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showActionMenu = true }),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (trip.isSample) SampleBadge()
                }
                if (partial) {
                    Text(
                        text = "Partial - ${trip.partialReasonText()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        maxLines = 1
                    )
                }
                if (finished) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            Format.tripDistance(trip.distanceM),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(58.dp)
                        )
                        DurationBar(
                            durationS = trip.durationS,
                            fraction = (trip.durationS / maxDurationS).coerceIn(0.03, 1.0).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = "Not finished",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected && finished) {
                    val quality = TripDataQuality.from(trip)
                    val vsGoogle = if (trip.googleEtaTrafficS > 0.0 && trip.durationS > 0.0) {
                        val d = ((trip.durationS - trip.googleEtaTrafficS) / 60.0).roundToInt()
                        when {
                            d > 0 -> " · +$d min vs Google"
                            d < 0 -> " · $d min vs Google"
                            else -> " · on par vs Google"
                        }
                    } else ""
                    Text(
                        "${Format.timeOfDay(trip.startTime)} · ${quality.level.label} quality$vsGoogle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (scores != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(SCORE_COL_GAP)) {
                    MiniScore(scores.safety)
                    MiniScore(scores.comfort)
                    MiniScore(scores.speed)
                }
            }
        }
    }

    if (showActionMenu) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            title = { Text(displayName) },
            text = { Text(Format.dateTime(trip.startTime)) },
            confirmButton = {
                TextButton(onClick = { showActionMenu = false; showRenameDialog = true }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showActionMenu = false; showDeleteDialog = true }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            }
        )
    }

    if (showRenameDialog) {
        var draft by remember { mutableStateOf(trip.name.ifBlank { label }) }
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
                TextButton(onClick = { showRenameDialog = false; onRename(draft) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this trip?") },
            text = { Text("$displayName - ${Format.dateTime(trip.startTime)}") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DurationBar(durationS: Double, fraction: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            Format.tripMinutes(durationS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
    }
}

private val SCORE_COL_WIDTH = 40.dp
private val SCORE_COL_GAP = 8.dp

@Composable
private fun SampleBadge() {
    Text(
        text = "SAMPLE",
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
private fun SectionHeader(label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripListHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(SCORE_COL_GAP)) {
            HeaderCol("Safe")
            HeaderCol("Comf")
            HeaderCol("Speed")
        }
    }
}

@Composable
private fun HeaderCol(label: String) {
    Box(modifier = Modifier.width(SCORE_COL_WIDTH), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniScore(value: Int?) {
    val color = value?.let { TripScores.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.width(SCORE_COL_WIDTH), contentAlignment = Alignment.Center) {
        Text(
            value?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
