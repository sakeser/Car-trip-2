package com.cartrip.analyzer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripKind
import com.cartrip.analyzer.analysis.TripScores
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
    // Multi-select: a long-press enters selection mode; the top bar becomes a contextual action bar.
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // Recency window for the map + list. Default to a week so the all-time (heavy) map isn't the first load.
    var recency by remember { mutableStateOf(RecencyFilter.WEEK) }
    val listState = rememberLazyListState()
    // System back exits selection mode before leaving the screen.
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }
    val shownTrips = remember(trips, recency) {
        val days = recency.days ?: return@remember trips
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        trips.filter { it.startTime >= cutoff }
    }
    val heatPoints by produceState(initialValue = emptyList<AnalysisPointEntity>(), trips, recency) {
        value = viewModel.loadHeatmapPoints(recency.days ?: 36_500)
    }
    val tripLabels by produceState(initialValue = emptyMap<Long, String>(), trips) {
        value = viewModel.loadTripLabels(trips)
    }
    val selectedRoute by produceState(initialValue = emptyList<TrackPoint>(), selectedTripId) {
        value = selectedTripId?.let { viewModel.loadRoute(it) } ?: emptyList()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                // Contextual action bar: Rename (only when exactly one is picked) + Delete (any count).
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        if (selectedIds.size == 1) {
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                        }
                        IconButton(onClick = { showMultiDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
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
                RecencyFilterRow(selected = recency, onSelect = { recency = it })
                // Frozen map: previews the selected trip's route, else the recency-window heatmap.
                // Maximized: near-edge-to-edge and taller so the route reads clearly.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .height(264.dp)
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

                // Size duration bars against a tidy axis with a little headroom (BarScale) so the longest
                // trip fills ~75-90% of the track, not edge-to-edge. Axis kept in seconds (minutes * 60).
                val rawMaxDurationS = shownTrips.maxOfOrNull { it.durationS }?.coerceAtLeast(1.0) ?: 1.0
                val maxDurationS = BarScale.niceAxisMax(rawMaxDurationS / 60.0, headroom = 1.15) * 60.0
                val grouped = TripBuckets.group(shownTrips)
                // Differentiate same-named trips ("North York Loop (10:14am)") across the whole list.
                val shownNames = remember(shownTrips, tripLabels) {
                    TripNaming.disambiguate(
                        shownTrips.map {
                            TripNaming.Entry(it.id, it.name.ifBlank { tripLabels[it.id] ?: "Trip" }, it.startTime)
                        }
                    )
                }
                if (shownTrips.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No trips in ${recency.label.lowercase()}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .drawVerticalScrollbar(listState, scrollbarColor),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var runningOffset = 0
                    grouped.forEachIndexed { bucketIndex, (bucket, bucketTrips) ->
                        val startNumber = runningOffset
                        runningOffset += bucketTrips.size
                        val expanded = bucket !in collapsedBuckets
                        item(key = "hdr_${bucket.name}") {
                            SectionHeader(
                                label = bucket.label,
                                count = bucketTrips.size,
                                expanded = expanded,
                                // First section header also carries the Safe/Comf/Speed column labels
                                // (the old standalone header row was removed to save vertical space).
                                showScoreLabels = bucketIndex == 0,
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
                                    shownName = shownNames[trip.id] ?: trip.name.ifBlank { tripLabels[trip.id] ?: "Trip" },
                                    maxDurationS = maxDurationS,
                                    selected = selectedTripId == trip.id && !selectionMode,
                                    selectionMode = selectionMode,
                                    checked = trip.id in selectedIds,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (trip.id in selectedIds) selectedIds - trip.id
                                            else selectedIds + trip.id
                                        } else if (selectedTripId == trip.id) {
                                            onOpen(trip.id)
                                        } else {
                                            selectedTripId = trip.id
                                        }
                                    },
                                    onLongClick = { selectedIds = selectedIds + trip.id }
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
            text = { Text("Removes only the demo trips marked SAMPLE. Your real recorded trips are kept. (Delete real trips by long-pressing to select one or more, then tapping Delete.)") },
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

    if (showMultiDeleteDialog) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { showMultiDeleteDialog = false },
            title = { Text(if (n == 1) "Delete this trip?" else "Delete $n trips?") },
            text = {
                Text(
                    if (n == 1) "This permanently deletes the trip and its data."
                    else "This permanently deletes $n trips and their data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMultiDeleteDialog = false
                    viewModel.deleteTrips(selectedIds)
                    if (selectedTripId in selectedIds) selectedTripId = null
                    selectedIds = emptySet()
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        val target = trips.firstOrNull { it.id in selectedIds }
        if (target == null) {
            showRenameDialog = false
        } else {
            var draft by remember(target.id) {
                mutableStateOf(target.name.ifBlank { tripLabels[target.id] ?: "Trip" })
            }
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
                    TextButton(onClick = {
                        showRenameDialog = false
                        viewModel.renameTrip(target.id, draft)
                        selectedIds = emptySet()
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/** Recency windows for the Past-trips map + list. Default is a week so the heavy all-time map isn't first. */
private enum class RecencyFilter(val label: String, val days: Int?) {
    DAY("the last 24 h", 1),
    THREE("the last 3 days", 3),
    WEEK("the last 7 days", 7),
    MONTH("the last 30 days", 30),
    ALL("all time", null);

    val chip: String
        get() = when (this) {
            DAY -> "24h"; THREE -> "3d"; WEEK -> "7d"; MONTH -> "30d"; ALL -> "All"
        }
}

@Composable
private fun RecencyFilterRow(selected: RecencyFilter, onSelect: (RecencyFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RecencyFilter.values().forEach { f ->
            val on = f == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(f) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    f.chip,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(
    number: Int,
    trip: TripEntity,
    shownName: String,
    maxDurationS: Double,
    selected: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val finished = trip.endTime != 0L
    val scores = if (finished) TripScores.from(trip) else null
    val partial = trip.isPartialRecording()
    // A walk/non-drive: driving scores (Safety especially) are meaningless, so the card flags it with a
    // walking icon and shows moving-average speed instead of the score columns.
    val isWalk = finished && TripKind.isLikelyNonDrive(trip)
    val highlighted = selected || checked

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Title spans the full card width on its own line so geocoded "A -> B" names stay one line
            // instead of wrapping (the scores now sit on the meta row below, not beside the title).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectionMode) {
                    // Display-only: taps fall through to the card's onClick, which toggles selection.
                    Checkbox(checked = checked, onCheckedChange = null)
                } else {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp)
                    )
                }
                if (isWalk) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Walk",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = shownName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
                        fraction = BarScale.fillFraction(trip.durationS, maxDurationS, minVisible = 0.03f),
                        modifier = Modifier.weight(1f)
                    )
                    if (isWalk) {
                        // Walk: driving scores don't apply — show moving-average speed (idle excluded).
                        Text(
                            Format.avgSpeedKmh(trip.avgMovingSpeedMps),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else if (scores != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(SCORE_COL_GAP)) {
                            MiniScore(scores.safety)
                            MiniScore(scores.comfort)
                            MiniScore(scores.speed)
                        }
                    }
                }
            } else {
                Text(
                    text = "Not finished",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected && finished) {
                val vsGoogle = if (trip.googleEtaTrafficS > 0.0 && trip.durationS > 0.0) {
                    val d = ((trip.durationS - trip.googleEtaTrafficS) / 60.0).roundToInt()
                    when {
                        d > 0 -> " · +$d min vs Google"
                        d < 0 -> " · $d min vs Google"
                        else -> " · on par vs Google"
                    }
                } else ""
                Text(
                    "${Format.relativeDay(trip.startTime)}, ${Format.timeOfDay(trip.startTime)}$vsGoogle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

private val SCORE_COL_WIDTH = 34.dp
private val SCORE_COL_GAP = 6.dp

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
private fun SectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    showScoreLabels: Boolean = false
) {
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
        if (showScoreLabels) {
            Spacer(modifier = Modifier.weight(1f))
            // end=6.dp lines these up with the trip rows' score columns (12.dp card pad - 6.dp here).
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(SCORE_COL_GAP)
            ) {
                HeaderCol("Safe")
                HeaderCol("Comf")
                HeaderCol("Pace")
            }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * A lightweight always-visible scrollbar thumb on the right edge, shown only when the list overflows — so
 * the user can see at a glance that there are more trips to scroll to (and roughly where they are).
 */
private fun Modifier.drawVerticalScrollbar(state: LazyListState, color: Color): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.size >= total) return@drawWithContent
        val trackH = size.height
        val thumbFrac = visible.size.toFloat() / total
        val offFrac = visible.first().index.toFloat() / total
        val thumbH = (trackH * thumbFrac).coerceAtLeast(24.dp.toPx())
        // A transient zero-height (or sub-thumb-height) draw frame makes trackH - thumbH negative, which
        // crashes coerceIn with an empty range. Nothing useful to draw then, so bail.
        if (trackH <= thumbH) return@drawWithContent
        val thumbY = (trackH * offFrac).coerceIn(0f, trackH - thumbH)
        val w = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - w - 1.dp.toPx(), thumbY),
            size = Size(w, thumbH),
            cornerRadius = CornerRadius(w / 2f, w / 2f)
        )
    }

@Composable
private fun MiniScore(value: Int?) {
    val color = value?.let { ScoreColors.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.width(SCORE_COL_WIDTH), contentAlignment = Alignment.Center) {
        Text(
            value?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
