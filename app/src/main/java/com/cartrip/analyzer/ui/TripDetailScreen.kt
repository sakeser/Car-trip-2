package com.cartrip.analyzer.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    var etaRefresh by remember { mutableStateOf(0) }
    var fetchingEta by remember { mutableStateOf(false) }
    var fetchingLimits by remember { mutableStateOf(false) }
    var fetchingSync by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val fuelProfile by VehicleFuelState.state.collectAsStateWithLifecycle()
    val trip by produceState<TripEntity?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadTrip(tripId)
    }
    val analysis by produceState<TripAnalysis?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadAnalysis(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.let { it.name.ifBlank { Format.dateOnly(it.startTime) } } ?: "Trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share Excel") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val t = trip; val an = analysis
                                if (t != null && an != null) actionScope.launch { TripActions.shareExcel(ctx, t, an) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-analyze") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                actionScope.launch {
                                    val ok = viewModel.reanalyzeTrip(tripId)
                                    etaRefresh++
                                    CloudState.set {
                                        it.copy(
                                            lastMessage = if (ok) "Re-analyzed with the latest detector."
                                            else "Raw sensor data was already purged — can't re-analyze."
                                        )
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete trip") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { showMenu = false; viewModel.deleteTrip(tripId); onDeleted() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val a = analysis
        if (a == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val m = trip?.displayMetrics(a.metrics) ?: a.metrics
        val scores = trip?.let { TripScores.from(it) }
        var selectedIndex by remember(a.points) { mutableStateOf(0) }
        var focusKey by remember(a.points) { mutableStateOf(0) }
        var mapAnchorY by remember(a.points) { mutableStateOf(0) }
        val scrollState = rememberScrollState()
        val maxPointIndex = (a.points.size - 1).coerceAtLeast(0)
        val safeSelectedIndex = selectedIndex.coerceIn(0, maxPointIndex)
        val selectedPoint = a.points.getOrNull(safeSelectedIndex)
        // GPS/pothole + Rev D fused events, shown together on the map, replay and Events tab.
        val shownEvents = remember(a) { (a.events + a.fusedEvents).sortedBy { it.tMs } }
        val jumpToEvent: (DriveEvent) -> Unit = { event ->
            selectedIndex = nearestPointIndex(a.points, event.tMs).coerceIn(0, maxPointIndex)
            focusKey++
            actionScope.launch { scrollState.animateScrollTo(mapAnchorY) }
        }
        val fetchTypicalEta: () -> Unit = {
            fetchingEta = true
            actionScope.launch {
                val error = viewModel.fetchTypicalEstimate(tripId)
                fetchingEta = false
                if (error == null) etaRefresh++
                else Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val startPt = a.points.firstOrNull()
            val endPt = a.points.lastOrNull()

            // --- Partial banner ---
            trip?.takeIf { it.isPartialRecording() }?.let { PartialBanner(it) }

            // --- Result story: actual vs expected, score, and the main trip outcome. ---
            val currentTrip = trip
            if (currentTrip != null) {
                TripHero(
                    trip = currentTrip,
                    m = m,
                    scores = scores,
                    fetchingEta = fetchingEta,
                    onFetchEta = fetchTypicalEta
                )
                TripResultDriversCard(
                    trip = currentTrip,
                    metrics = m,
                    scores = scores
                )
                TripCostSummaryCard(
                    metrics = m,
                    fuelProfile = fuelProfile
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ScoreRing(m.smoothness)
                }
            }

            // --- Map + replay timeline ---
            if (a.points.size >= 2) {
                ResultSectionHeader(
                    title = "Route replay",
                    subtitle = "Scrub the timeline or tap events to see where the drive changed."
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onGloballyPositioned { mapAnchorY = it.positionInParent().y.roundToInt() }
                ) {
                    TripMap(
                        points = a.points,
                        events = shownEvents,
                        selectedPoint = selectedPoint,
                        focusKey = focusKey,
                        modifier = Modifier.fillMaxSize()
                    )
                    MapActions(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        onStart = { startPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Start") } },
                        onStop = { endPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Stop") } },
                        onRoute = {
                            if (startPt != null && endPt != null) {
                                TripActions.routeInMaps(ctx, startPt.lat, startPt.lon, endPt.lat, endPt.lon)
                            }
                        }
                    )
                }

                ReplayTimeline(
                    points = a.points,
                    events = shownEvents,
                    selectedIndex = safeSelectedIndex,
                    onSelectedIndex = { selectedIndex = it.coerceIn(0, maxPointIndex) },
                    onEventJump = jumpToEvent
                )
            } else {
                Text(
                    "No GPS track recorded for this trip.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Driving factors + road & ride (quiet, visual) ---
            ResultSectionHeader(
                title = "Driver and road signals",
                subtitle = "The details behind score, comfort, speed, and route confidence."
            )
            trip?.let { t ->
                SafetyFactorsCard(
                    trip = t,
                    checking = fetchingLimits,
                    onCheckLimits = {
                        fetchingLimits = true
                        actionScope.launch {
                            val error = viewModel.fetchSpeedLimits(tripId)
                            fetchingLimits = false
                            etaRefresh++
                            if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            trip?.let { t -> RoadRideCard(t) }

            // --- Events (collapsed): tap any event to jump to its spot on the map ---
            if (shownEvents.isNotEmpty()) {
                EventsSection(events = shownEvents, points = a.points, onEventJump = jumpToEvent)
            }

            // --- Advanced (collapsed): charts, raw metrics, detector comparison ---
            AdvancedSection(trip = trip, metrics = m, fusedEvents = a.fusedEvents, points = a.points)

            // --- Sync footer ---
            trip?.let { t ->
                SyncStatusRow(
                    trip = t,
                    syncing = fetchingSync,
                    onSync = {
                        fetchingSync = true
                        actionScope.launch {
                            val error = viewModel.resyncTrip(tripId)
                            fetchingSync = false
                            etaRefresh++
                            if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PartialBanner(trip: TripEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = Color(0xFFD97706))
            Column {
                Text("Partial recording", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                Text(trip.partialReasonText(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A3412))
            }
        }
    }
}

@Composable
private fun TripHero(
    trip: TripEntity,
    m: DriveMetrics,
    scores: TripScores?,
    fetchingEta: Boolean,
    onFetchEta: () -> Unit
) {
    val endMs = if (trip.endTime > 0) trip.endTime else trip.startTime + (m.durationS * 1000).toLong()
    val comparison = expectedDriveComparison(trip, m.durationS)
    val score = scores?.overall ?: m.smoothness
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ResultInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "TRIP RESULT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                    Text(
                        comparison.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = comparison.color
                    )
                    Text(
                        "${Format.timeOfDay(trip.startTime)} - ${Format.timeOfDay(endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.74f)
                    )
                }
                ScoreRing(score, ringSize = 76.dp)
            }

            Text(
                comparison.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.84f)
            )

            if (comparison.hasEstimate) {
                ResultTimeComparisonBars(
                    actualS = m.durationS,
                    expectedS = trip.googleEtaTrafficS,
                    freeFlowS = trip.googleEtaFreeFlowS,
                    actualColor = comparison.color
                )
            } else {
                OutlinedButton(
                    onClick = onFetchEta,
                    enabled = !fetchingEta,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (fetchingEta) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Compare to expected drive", color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultMetricTile("Actual time", Format.tripMinutes(m.durationS), Modifier.weight(1f))
                ResultMetricTile("Distance", Format.tripDistance(m.distanceM), Modifier.weight(1f))
                ResultMetricTile("Avg speed", Format.speedKmh(m.avgMovingSpeedMps * 3.6), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultHeroScore("Safety", scores?.safety)
                ResultHeroScore("Comfort", scores?.comfort)
                ResultHeroScore("Pace", scores?.speed)
            }
        }
    }
}

@Composable
private fun ResultTimeComparisonBars(
    actualS: Double,
    expectedS: Double,
    freeFlowS: Double,
    actualColor: Color
) {
    val rows = buildList {
        if (freeFlowS > 0.0) add(ResultTimeBar("Free-flow", freeFlowS, ResultBlue))
        add(ResultTimeBar("Expected", expectedS, Color.White.copy(alpha = 0.58f)))
        add(ResultTimeBar("You", actualS, actualColor))
    }
    val maxS = rows.maxOf { it.seconds }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.width(66.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((row.seconds / maxS).toFloat().coerceIn(0.05f, 1f))
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(row.color)
                    )
                }
                Text(
                    Format.tripMinutes(row.seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.width(42.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ResultMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResultHeroScore(label: String, value: Int?) {
    val color = value?.let { TripScores.color(it) } ?: Color.White.copy(alpha = 0.58f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(
            "$label ${value ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun TripResultDriversCard(
    trip: TripEntity,
    metrics: DriveMetrics,
    scores: TripScores?
) {
    val comparison = expectedDriveComparison(trip, metrics.durationS)
    val idle = idleSummary(metrics)
    val inputs = drivingInputSummary(trip, scores)
    val quality = if (!trip.isSample) TripDataQuality.from(trip) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "What affected this result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ResultDriverRow(
                title = "Pace vs expected",
                detail = comparison.driverDetail,
                color = comparison.color
            )
            ResultDriverRow(
                title = "Stops and idle",
                detail = idle.detail,
                color = idle.color
            )
            ResultDriverRow(
                title = "Driving inputs",
                detail = inputs.detail,
                color = inputs.color
            )
            quality?.let {
                ResultDriverRow(
                    title = "Data confidence",
                    detail = "${it.level.label}: ${it.detail()}",
                    color = it.color()
                )
            }
        }
    }
}

@Composable
private fun TripCostSummaryCard(
    metrics: DriveMetrics,
    fuelProfile: VehicleFuelProfile
) {
    val estimate = FuelCost.estimate(metrics.distanceM, fuelProfile) ?: return
    val costPerKm = if (metrics.distanceM > 0.0) {
        estimate.cost / (metrics.distanceM / 1000.0)
    } else {
        0.0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ResultGreen)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estimated trip cost",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        fuelProfile.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    FuelCost.money(estimate.cost),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ResultGreen
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CostFact(
                    label = "Fuel used",
                    value = FuelCost.fuel(estimate.fuelAmount, estimate.fuelUnitLabel),
                    modifier = Modifier.weight(1f)
                )
                CostFact(
                    label = "Cost / km",
                    value = FuelCost.money(costPerKm),
                    modifier = Modifier.weight(1f)
                )
                CostFact(
                    label = "Profile",
                    value = fuelProfile.unit.label,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CostFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResultDriverRow(title: String, detail: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripHero(trip: TripEntity, m: DriveMetrics, scores: TripScores) {
    val endMs = if (trip.endTime > 0) trip.endTime else trip.startTime + (m.durationS * 1000).toLong()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScoreRing(scores.overall, ringSize = 78.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${Format.timeOfDay(trip.startTime)} – ${Format.timeOfDay(endMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${Format.tripMinutes(m.durationS)}  ·  ${Format.tripDistance(m.distanceM)}  ·  ${Format.speedKmh(m.avgMovingSpeedMps * 3.6)} avg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroScore("Safety", scores.safety)
                    HeroScore("Comfort", scores.comfort)
                    HeroScore("Pace", scores.speed)
                }
            }
        }
    }
}

@Composable
private fun HeroScore(label: String, value: Int?) {
    val color = value?.let { TripScores.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(
            "$label ${value ?: "—"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small map-action chips over the map: Start / Route / End in Google Maps. */
@Composable
private fun MapActions(modifier: Modifier = Modifier, onStart: () -> Unit, onStop: () -> Unit, onRoute: () -> Unit) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MapChip(Icons.Filled.MyLocation, "Start in Maps", onStart)
        MapChip(Icons.Filled.Route, "Route in Maps", onRoute)
        MapChip(Icons.Filled.Place, "End in Maps", onStop)
    }
}

@Composable
private fun MapChip(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Color(0xFF185FA5), modifier = Modifier.size(19.dp))
    }
}

/**
 * Waze-style replay strip: the speed curve is the timeline, event glyphs sit on it at their moment
 * (tap to jump), and a scrubber + a one-line readout sit below. Speed chart and replay in one.
 */
@Composable
private fun ReplayTimeline(
    points: List<TrackPoint>,
    events: List<DriveEvent>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    onEventJump: (DriveEvent) -> Unit
) {
    val t0 = points.first().tMs
    val tN = points.last().tMs.coerceAtLeast(t0 + 1)
    val speeds = remember(points) { points.map { it.speedKmh.toFloat() } }
    // Per-point speeding flag (needs OSM limits looked up): used to paint the line red.
    val over = remember(points) { points.map { it.speedLimitKmh > 0.0 && it.speedKmh > it.speedLimitKmh + 1.0 } }
    val anySpeeding = over.any { it }
    val maxSpeed = (speeds.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val selFrac = if (points.size > 1) selectedIndex.toFloat() / (points.size - 1) else 0f
    val lineColor = MaterialTheme.colorScheme.primary
    val speedingColor = Color(0xFFEF4444)
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val density = LocalDensity.current
    val point = points.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildString {
                    append(if (events.isEmpty()) "Replay" else "Replay · ${events.size} event${if (events.size == 1) "" else "s"}")
                    if (anySpeeding) append(" · speeding in red")
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (anySpeeding) speedingColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (point != null) {
                Text(
                    "${Format.duration((point.tMs - t0).coerceAtLeast(0) / 1000.0)} · ${Format.speedKmh(point.speedKmh)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(60.dp)) {
            val wPx = with(density) { maxWidth.toPx() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                if (speeds.size >= 2) {
                    fun xAt(i: Int) = w * i / (speeds.size - 1)
                    fun yAt(v: Float) = h - (v / maxSpeed) * h * 0.88f - h * 0.06f
                    val path = Path()
                    path.moveTo(0f, yAt(speeds[0]))
                    for (i in 1 until speeds.size) path.lineTo(xAt(i), yAt(speeds[i]))
                    val fill = Path().apply { addPath(path); lineTo(w, h); lineTo(0f, h); close() }
                    drawPath(fill, lineColor.copy(alpha = 0.12f))
                    // Stroke segment-by-segment so speeding stretches show red.
                    for (i in 1 until speeds.size) {
                        val speeding = over[i] || over[i - 1]
                        drawLine(
                            if (speeding) speedingColor else lineColor,
                            Offset(xAt(i - 1), yAt(speeds[i - 1])),
                            Offset(xAt(i), yAt(speeds[i])),
                            strokeWidth = 3f
                        )
                    }
                }
                drawLine(cursorColor, Offset(selFrac * w, 0f), Offset(selFrac * w, h), 2f)
            }
            events.forEach { event ->
                val frac = ((event.tMs - t0).toFloat() / (tN - t0)).coerceIn(0f, 1f)
                val style = eventStyle(event)
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { (frac * wPx).toDp() } - 9.dp, y = (-2).dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(style.color)
                        .clickable { onEventJump(event) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(style.icon, contentDescription = style.label, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { onSelectedIndex(it.roundToInt()) },
            valueRange = 0f..(points.size - 1).coerceAtLeast(1).toFloat()
        )
    }
}

@Composable
private fun AdvancedSection(trip: TripEntity?, metrics: DriveMetrics, fusedEvents: List<DriveEvent>, points: List<TrackPoint>) {
    var expanded by remember { mutableStateOf(false) }
    val m = metrics
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Advanced & charts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatGrid(
                        stats = listOf(
                            Stat("Max speed", Format.speedKmh(m.maxSpeedMps * 3.6)),
                            Stat("Idle time", Format.duration(m.idleS)),
                            Stat("Peak g-force", Format.gforce(m.peakGForce)),
                            Stat("Max braking", Format.accelG(m.maxBrakeMps2), Color(0xFFEF4444)),
                            Stat("Max accel", Format.accelG(m.maxAccelMps2), Color(0xFFF59E0B)),
                            Stat("Max lateral", Format.accelG(m.maxLateralMps2))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Used ${m.usedFixes} of ${m.rawFixes} GPS fixes after filtering.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (trip != null && (fusedEvents.isNotEmpty() || trip.motionTurnCount > 0 ||
                            trip.motionBrakeCount > 0 || trip.motionAccelCount > 0)) {
                        Text("Detector comparison (beta)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "GPS:  ${trip.hardBrakeCount} brake · ${trip.hardAccelCount} accel · ${trip.hardCornerCount} turn",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Sensors:  ${trip.motionBrakeCount} brake · ${trip.motionAccelCount} accel · ${trip.motionTurnCount} turn",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B5CF6)
                        )
                        if (metrics.maxHorizGForce > 0.0) {
                            Text(
                                "Peak horizontal ${"%.2fg".format(metrics.maxHorizGForce)} · sustained ${"%.2fg".format(metrics.peakGForce)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Timestamped fused events (magnitude-first detector). conf shows GPS-sign certainty.
                        val firstT = points.firstOrNull()?.tMs ?: 0L
                        fusedEvents.sortedBy { it.tMs }.take(12).forEach { e ->
                            val rel = ((e.tMs - firstT) / 1000).coerceAtLeast(0)
                            Text(
                                "  %d:%02d  %-7s %.2fg  ·  %.0f%% conf".format(
                                    rel / 60, rel % 60, e.type.name.lowercase(), e.magnitude / 9.80665, e.confidence * 100
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B5CF6)
                            )
                        }
                        Text(
                            "Magnitude-first sensor detector · brake/accel classified from GPS speed · not scored yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Collapsible per-event list. Tapping a row jumps the map/replay to that event's spot. */
@Composable
private fun EventsSection(events: List<DriveEvent>, points: List<TrackPoint>, onEventJump: (DriveEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val firstT = points.firstOrNull()?.tMs ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Events · ${events.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val counts = events.groupingBy { eventStyle(it).label }.eachCount()
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        counts.forEach { (label, count) ->
                            val color = events.first { eventStyle(it).label == label }.let { eventStyle(it).color }
                            CountPill(label = label, count = count, color = color)
                        }
                    }
                    Text(
                        "Tap an event to see where it happened on the map.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    events.sortedBy { it.tMs }.forEach { event ->
                        EventRow(event, points, firstT, onClick = { onEventJump(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyFactorsCard(trip: TripEntity, checking: Boolean, onCheckLimits: () -> Unit) {
    val hasLimits = trip.limitCoverage >= 0.4
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Driving", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FactorBar("Braking", trip.hardBrakePct, Color(0xFFEF4444))
            FactorBar("Turns", trip.aggressiveTurnPct, Color(0xFFF59E0B))
            FactorBar("Accel", trip.hardAccelPct, Color(0xFFF59E0B))
            FactorBar("Jerky", trip.jerkyPct, Color(0xFF8B5CF6))
            if (hasLimits) {
                val notable = trip.speedingPct > 0.05
                Text(
                    if (notable) "Speeding ${"%.0f".format(trip.speedingPct * 100)}% of the way" +
                        (if (trip.maxOverLimitKmh >= 1) " · peak ${"%.0f".format(trip.maxOverLimitKmh)} over" else "")
                    else "No notable speeding · red on map = over limit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (notable) FontWeight.Bold else FontWeight.Normal,
                    color = if (notable) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedButton(onClick = onCheckLimits, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check speed limits")
                    }
                }
            }
        }
    }
}

/** Quiet factor bar: muted when the value is low/safe, accented only when notable (≥1% of time). */
@Composable
private fun FactorBar(label: String, fraction: Double, accent: Color) {
    val notable = fraction >= 0.01
    val barFrac = (fraction / 0.06).coerceIn(0.015, 1.0).toFloat()
    val color = if (notable) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(54.dp))
        Box(
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
        ) {
            Box(modifier = Modifier.fillMaxWidth(barFrac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(
            "${"%.1f".format(fraction * 100)}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun SyncStatusRow(trip: TripEntity, syncing: Boolean, onSync: () -> Unit) {
    if (trip.isSample) {
        Text(
            "Sample trip — not synced to Google Sheets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val (label, color) = when {
        trip.syncedAt > 0L -> "Synced to Google Sheets" to Color(0xFF22C55E)
        trip.syncError.isNotEmpty() -> "Sync failed — tap to retry" to Color(0xFFEF4444)
        else -> "Not synced to Google Sheets" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onSync, enabled = !syncing) {
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
            } else {
                Text(if (trip.syncedAt > 0L) "Re-sync" else "Sync now")
            }
        }
    }
}

@Composable
private fun RoadRideCard(trip: TripEntity) {
    // Only meaningful for real trips that carried accelerometer + gravity data.
    val hasData = trip.potholeCount > 0 || trip.roughRoadPct > 0.0 || trip.harshStopCount > 0
    if (!hasData) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Road & ride", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "From the accelerometer — the road's condition and how the car settled.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoadCell("Potholes / bumps", trip.potholeCount.toString(), Color(0xFF78716C), Modifier.weight(1f))
                RoadCell("Rough road", "${"%.0f".format(trip.roughRoadPct * 100)}%", Color(0xFFF59E0B), Modifier.weight(1f))
                RoadCell("Harsh stops", trip.harshStopCount.toString(), Color(0xFFEF4444), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoadCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FactorCell(label: String, fraction: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "${"%.1f".format(fraction * 100)}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TripLinksCard(
    startEnabled: Boolean,
    stopEnabled: Boolean,
    routeEnabled: Boolean,
    exportEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRoute: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Map links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionButton("Start", Icons.Filled.PlayArrow, Modifier.weight(1f), startEnabled, onStart)
                ActionButton("Stop", Icons.Filled.StopCircle, Modifier.weight(1f), stopEnabled, onStop)
                ActionButton("Route", Icons.Filled.Route, Modifier.weight(1f), routeEnabled, onRoute)
                ActionButton("Excel", Icons.Filled.Share, Modifier.weight(1f), exportEnabled, onExport)
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val iconColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .height(66.dp)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun EtaComparisonCard(
    trip: TripEntity,
    actualS: Double,
    fetching: Boolean,
    onFetch: () -> Unit
) {
    val hasEta = trip.etaSource.isNotEmpty() && trip.googleEtaTrafficS > 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You vs traffic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!hasEta) {
                Text(
                    "No traffic comparison for this trip yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onFetch, enabled = !fetching) {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Compare to traffic")
                    }
                }
                return@Column
            }

            val youColor = if (actualS <= trip.googleEtaTrafficS) Color(0xFF22C55E) else Color(0xFFEF4444)
            val deltaS = actualS - trip.googleEtaTrafficS
            val absMin = (kotlin.math.abs(deltaS) / 60.0).roundToInt()
            val (verdict, color) = when {
                deltaS < -45 -> "$absMin min faster than usual traffic" to Color(0xFF22C55E)
                deltaS > 45 -> "$absMin min slower than usual traffic" to Color(0xFFEF4444)
                else -> "About the same as usual traffic" to MaterialTheme.colorScheme.onSurface
            }
            Text(verdict, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)

            // Three bars, shortest on top, widths to scale — a compact at-a-glance comparison.
            val bars = listOf(
                Triple("Free-flow", trip.googleEtaFreeFlowS, Color(0xFF38BDF8)),
                Triple("Typical", trip.googleEtaTrafficS, MaterialTheme.colorScheme.onSurfaceVariant),
                Triple("You", actualS, youColor)
            ).sortedBy { it.second }
            val maxS = bars.maxOf { it.second }.coerceAtLeast(1.0)
            bars.forEach { (label, s, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(58.dp))
                    Box(modifier = Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))) {
                        Box(modifier = Modifier.fillMaxWidth((s / maxS).toFloat().coerceIn(0.04f, 1f)).height(13.dp).clip(RoundedCornerShape(4.dp)).background(c))
                    }
                    Text(Format.tripMinutes(s), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(44.dp))
                }
            }

            Text(
                (if (trip.etaSource == "live") "Live traffic at trip end" else "Typical traffic for this time") + " · via Google",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EtaCell(label: String, seconds: Double, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            Format.tripMinutes(seconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A shared time axis with three markers — free-flow, Google's ETA, and your actual time —
 * so the comparison is visual. The "you" marker is green when you matched/beat Google, red when slower.
 */
@Composable
private fun EtaTimeline(freeFlowS: Double, googleS: Double, youS: Double, youColor: Color) {
    val freeColor = Color(0xFF38BDF8)
    val googleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val scaleMax = maxOf(freeFlowS, googleS, youS, 1.0) * 1.12
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        fun xFor(v: Double) = with(density) { (v / scaleMax * widthPx).toFloat().toDp() }
        val lineY = 34.dp

        // baseline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .offset(y = lineY)
                .clip(RoundedCornerShape(2.dp))
                .background(track)
        )
        EtaMarker(xFor(freeFlowS), freeColor, lineY)
        EtaMarker(xFor(googleS), googleColor, lineY)
        EtaMarker(xFor(youS), youColor, lineY, big = true)
    }
}

@Composable
private fun EtaMarker(x: androidx.compose.ui.unit.Dp, color: Color, lineY: androidx.compose.ui.unit.Dp, big: Boolean = false) {
    val size = if (big) 16.dp else 11.dp
    Box(
        modifier = Modifier
            .offset(x = x - size / 2, y = lineY + 1.5.dp - size / 2)
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(color)
    )
}

@Composable
private fun ScorePanel(scores: TripScores) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScoreRing(scores.overall)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreMeter("Safety", scores.safety)
            ScoreMeter("Comfort", scores.comfort)
            ScoreMeter("Speed", scores.speed)
        }
    }
}

@Composable
private fun ScoreMeter(label: String, value: Int?) {
    val color = value?.let { TripScores.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value?.toString() ?: "-",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value ?: 0) / 100f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ReplayControls(
    points: List<TrackPoint>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit
) {
    val point = points.getOrNull(selectedIndex) ?: return
    val firstT = points.firstOrNull()?.tMs ?: point.tMs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Replay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { onSelectedIndex(it.roundToInt()) },
            valueRange = 0f..(points.size - 1).toFloat()
        )
        StatGrid(
            stats = listOf(
                Stat("Time", Format.duration((point.tMs - firstT) / 1000.0)),
                Stat("Speed", Format.speedKmh(point.speedKmh)),
                Stat("Long accel", Format.accel(point.longAccel)),
                Stat("Lateral", Format.accel(point.latAccel))
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private class EventStyle(val label: String, val color: Color, val icon: ImageVector)

/** Human-readable name, severity colour and icon for a drive event, by type + magnitude. */
private fun eventStyle(event: DriveEvent): EventStyle = when (event.type) {
    EventType.BRAKE ->
        if (event.magnitude >= 4.5) EventStyle("Very hard brake", Color(0xFFDC2626), Icons.Filled.CrisisAlert)
        else EventStyle("Hard brake", Color(0xFFEF4444), Icons.Filled.StopCircle)
    EventType.ACCEL ->
        if (event.magnitude >= 3.5) EventStyle("Very hard acceleration", Color(0xFFDC2626), Icons.Filled.CrisisAlert)
        else EventStyle("Hard acceleration", Color(0xFFF59E0B), Icons.Filled.Speed)
    EventType.CORNER ->
        if (event.magnitude >= 5.0) EventStyle("Very sharp turn", Color(0xFFDC2626), Icons.Filled.CrisisAlert)
        else EventStyle("Sharp turn", Color(0xFFF59E0B), Icons.Filled.Route)
    EventType.SWERVE ->
        EventStyle("Swerve", Color(0xFFF59E0B), Icons.Filled.Route)
    EventType.POTHOLE ->
        EventStyle("Pothole / big bump", Color(0xFF78716C), Icons.Filled.ReportProblem)
}

/** A plain-language explanation of what the magnitude means. */
private fun eventExplanation(event: DriveEvent): String {
    val g = event.magnitude / 9.81
    val intensity = when (event.type) {
        EventType.BRAKE -> if (event.magnitude >= 4.5) "Sudden, heavy braking - passengers thrown forward."
        else "Firm braking, harder than a smooth stop."
        EventType.ACCEL -> if (event.magnitude >= 3.5) "Aggressive launch - heavy throttle."
        else "Brisk acceleration, firmer than normal."
        EventType.CORNER -> if (event.magnitude >= 5.0) "Tight, fast cornering - strong sideways force."
        else "Quick cornering with noticeable lean."
        EventType.SWERVE -> "A quick side-to-side direction change."
        EventType.POTHOLE -> "A sharp vertical jolt - pothole, speed bump, or rough patch."
    }
    return "$intensity  (${"%.2f".format(g)} g)"
}

private data class ExpectedDriveComparison(
    val hasEstimate: Boolean,
    val title: String,
    val detail: String,
    val driverDetail: String,
    val color: Color
)

private data class ResultTimeBar(
    val label: String,
    val seconds: Double,
    val color: Color
)

private data class ResultDriverSummary(
    val detail: String,
    val color: Color
)

private fun expectedDriveComparison(trip: TripEntity, actualS: Double): ExpectedDriveComparison {
    val expectedS = trip.googleEtaTrafficS
    if (trip.etaSource.isEmpty() || expectedS <= 0.0 || actualS <= 0.0) {
        return ExpectedDriveComparison(
            hasEstimate = false,
            title = "Compare this drive",
            detail = "Fetch a typical route estimate to see whether this trip lost or saved time.",
            driverDetail = "Expected-route comparison has not been fetched for this trip yet.",
            color = ResultAmber
        )
    }

    val deltaS = actualS - expectedS
    val absMin = (kotlin.math.abs(deltaS) / 60.0).roundToInt().coerceAtLeast(1)
    val source = if (trip.etaSource == "live") "traffic at trip end" else "typical traffic for this time"
    return when {
        deltaS > 45.0 -> ExpectedDriveComparison(
            hasEstimate = true,
            title = "+$absMin min slower",
            detail = "Actual ${Format.tripMinutes(actualS)} vs expected ${Format.tripMinutes(expectedS)} using $source.",
            driverDetail = "This trip took $absMin min longer than expected.",
            color = ResultRed
        )
        deltaS < -45.0 -> ExpectedDriveComparison(
            hasEstimate = true,
            title = "$absMin min saved",
            detail = "Actual ${Format.tripMinutes(actualS)} vs expected ${Format.tripMinutes(expectedS)} using $source.",
            driverDetail = "This trip beat the expected time by $absMin min.",
            color = ResultGreen
        )
        else -> ExpectedDriveComparison(
            hasEstimate = true,
            title = "On expected pace",
            detail = "Actual ${Format.tripMinutes(actualS)} closely matched expected ${Format.tripMinutes(expectedS)}.",
            driverDetail = "Your drive landed within about a minute of the expected route time.",
            color = ResultGreen
        )
    }
}

private fun idleSummary(metrics: DriveMetrics): ResultDriverSummary {
    val idleS = metrics.idleS.coerceAtLeast(0.0)
    val idlePct = if (metrics.durationS > 0.0) idleS / metrics.durationS else 0.0
    val pct = (idlePct * 100).roundToInt()
    return when {
        idleS < 60.0 -> ResultDriverSummary("Very little stopped or crawling time was recorded.", ResultGreen)
        idlePct >= 0.25 -> ResultDriverSummary(
            "${Format.tripMinutes(idleS)} stopped or crawling, about $pct% of the trip.",
            ResultRed
        )
        idlePct >= 0.10 -> ResultDriverSummary(
            "${Format.tripMinutes(idleS)} stopped or crawling, about $pct% of the trip.",
            ResultAmber
        )
        else -> ResultDriverSummary(
            "${Format.tripMinutes(idleS)} stopped or crawling, a modest part of the trip.",
            ResultGreen
        )
    }
}

private fun drivingInputSummary(trip: TripEntity, scores: TripScores?): ResultDriverSummary {
    val events = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
    val speeding = trip.limitCoverage >= 0.4 && trip.speedingPct > 0.05
    val color = scores?.safety?.let { TripScores.color(it) } ?: when {
        events >= 6 -> ResultRed
        events >= 2 -> ResultAmber
        else -> ResultGreen
    }
    val eventText = if (events == 0) {
        "No hard braking, acceleration, or cornering events stood out."
    } else {
        "$events hard input${if (events == 1) "" else "s"}: " +
            "${trip.hardBrakeCount} brake, ${trip.hardAccelCount} accel, ${trip.hardCornerCount} corner."
    }
    val speedText = if (speeding) {
        " Speeding covered ${"%.0f".format(trip.speedingPct * 100)}% of matched speed-limit time."
    } else {
        ""
    }
    return ResultDriverSummary(eventText + speedText, color)
}

private val ResultInk = Color(0xFF10211F)
private val ResultGreen = Color(0xFF22C55E)
private val ResultAmber = Color(0xFFF59E0B)
private val ResultRed = Color(0xFFEF4444)
private val ResultBlue = Color(0xFF38BDF8)


@Composable
private fun CountPill(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EventRow(event: DriveEvent, points: List<TrackPoint>, firstT: Long, onClick: () -> Unit) {
    val style = eventStyle(event)
    val point = points.getOrNull(nearestPointIndex(points, event.tMs))
    val intoTrip = Format.duration((event.tMs - firstT).coerceAtLeast(0) / 1000.0)
    val speed = point?.let { Format.speedKmh(it.speedKmh) } ?: "-"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(style.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, contentDescription = null, tint = style.color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(style.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = style.color)
                Text(
                    eventExplanation(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$intoTrip into trip | at $speed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.source == "fused") {
                    Text(
                        "sensor-detected | ${"%.0f".format(event.confidence * 100)}% confidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = "Show on map",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun downsample(values: List<Float>, max: Int): List<Float> {
    if (values.size <= max) return values
    val step = values.size.toFloat() / max
    return (0 until max).map { values[(it * step).toInt()] }
}

private fun nearestPointIndex(points: List<TrackPoint>, tMs: Long): Int {
    val next = points.indexOfFirst { it.tMs >= tMs }
    if (next <= 0) return next.coerceAtLeast(0)
    if (next == -1) return points.lastIndex
    val prev = next - 1
    return if (tMs - points[prev].tMs <= points[next].tMs - tMs) prev else next
}

private fun scaledIndex(selectedIndex: Int, sourceSize: Int, targetSize: Int): Int? {
    if (sourceSize <= 1 || targetSize <= 1) return null
    return (selectedIndex.toDouble() / (sourceSize - 1) * (targetSize - 1)).roundToInt()
}

private fun TripEntity.displayMetrics(fallback: DriveMetrics): DriveMetrics {
    if (!analyzed) return fallback
    return fallback.copy(
        distanceM = distanceM,
        durationS = durationS,
        movingS = movingS,
        idleS = idleS,
        maxSpeedMps = maxSpeedMps,
        avgMovingSpeedMps = avgMovingSpeedMps,
        maxAccelMps2 = maxAccelMps2,
        maxBrakeMps2 = maxBrakeMps2,
        maxLateralMps2 = maxLateralMps2,
        peakGForce = peakGForce,
        hardAccelCount = hardAccelCount,
        hardBrakeCount = hardBrakeCount,
        hardCornerCount = hardCornerCount,
        smoothness = smoothness
    )
}
