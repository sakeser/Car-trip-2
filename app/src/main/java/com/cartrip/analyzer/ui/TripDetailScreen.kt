package com.cartrip.analyzer.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripAnalysis
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
    val trip by produceState<TripEntity?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadTrip(tripId)
    }
    val analysis by produceState<TripAnalysis?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadAnalysis(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.let { Format.dateTime(it.startTime) } ?: "Trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteTrip(tripId)
                        onDeleted()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
        val displayAnalysis = a.copy(metrics = m)
        val scores = trip?.let { TripScores.from(it) }
        var selectedIndex by remember(a.points) { mutableStateOf(0) }
        var focusKey by remember(a.points) { mutableStateOf(0) }
        var mapAnchorY by remember(a.points) { mutableStateOf(0) }
        val scrollState = rememberScrollState()
        val maxPointIndex = (a.points.size - 1).coerceAtLeast(0)
        val safeSelectedIndex = selectedIndex.coerceIn(0, maxPointIndex)
        val selectedPoint = a.points.getOrNull(safeSelectedIndex)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val ctx = LocalContext.current
            val actionScope = rememberCoroutineScope()
            val startPt = a.points.firstOrNull()
            val endPt = a.points.lastOrNull()
            val primaryStats = listOf(
                Stat("Distance", Format.distance(m.distanceM)),
                Stat("Duration", Format.duration(m.durationS)),
                Stat("Max speed", Format.speedKmh(m.maxSpeedMps * 3.6)),
                Stat("Avg moving", Format.speedKmh(m.avgMovingSpeedMps * 3.6))
            )
            val detailedStats = listOf(
                Stat("Idle time", Format.duration(m.idleS)),
                Stat("Peak g-force", Format.gforce(m.peakGForce)),
                Stat("Hard brakes", "${m.hardBrakeCount}", Color(0xFFEF4444)),
                Stat("Hard accels", "${m.hardAccelCount}", Color(0xFFF59E0B)),
                Stat("Hard corners", "${m.hardCornerCount}", Color(0xFFF59E0B)),
                Stat("Max braking", Format.accel(m.maxBrakeMps2), Color(0xFFEF4444)),
                Stat("Max accel", Format.accel(m.maxAccelMps2), Color(0xFFF59E0B)),
                Stat("Max lateral", Format.accel(m.maxLateralMps2))
            )

            trip?.takeIf { it.isPartialRecording() }?.let { partialTrip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = Color(0xFFD97706))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Partial recording",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9A3412)
                            )
                            Text(
                                "${partialTrip.partialReasonText()} Distance, route, and end time may be estimated from saved samples.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }
                }
            }

            trip?.let { t ->
                val endMs = if (t.endTime > 0) t.endTime else t.startTime + (m.durationS * 1000).toLong()
                Text(
                    "${Format.dateOnly(t.startTime)}   |   ${Format.timeOfDay(t.startTime)} -> ${Format.timeOfDay(endMs)}   |   ${Format.duration(m.durationS)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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

            Text("Route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Used ${m.usedFixes} of ${m.rawFixes} GPS fixes after filtering",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (a.points.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onGloballyPositioned { mapAnchorY = it.positionInParent().y.roundToInt() }
                ) {
                    TripMap(
                        points = a.points,
                        events = a.events,
                        selectedPoint = selectedPoint,
                        focusKey = focusKey,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ReplayControls(
                    points = a.points,
                    selectedIndex = safeSelectedIndex,
                    onSelectedIndex = { selectedIndex = it.coerceIn(0, maxPointIndex) }
                )
            } else {
                Text(
                    "No GPS track recorded for this trip.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TripLinksCard(
                startEnabled = startPt != null,
                stopEnabled = endPt != null,
                routeEnabled = startPt != null && endPt != null,
                exportEnabled = trip != null,
                onStart = { startPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Start") } },
                onStop = { endPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Stop") } },
                onRoute = {
                    if (startPt != null && endPt != null) {
                        TripActions.routeInMaps(ctx, startPt.lat, startPt.lon, endPt.lat, endPt.lon)
                    }
                },
                onExport = { trip?.let { t -> actionScope.launch { TripActions.shareExcel(ctx, t, displayAnalysis) } } }
            )

            if (scores != null) {
                ScorePanel(scores)
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ScoreRing(m.smoothness)
                }
            }

            StatGrid(
                stats = primaryStats,
                modifier = Modifier.fillMaxWidth()
            )

            trip?.let { t ->
                EtaComparisonCard(
                    trip = t,
                    actualS = m.durationS,
                    fetching = fetchingEta,
                    onFetch = {
                        fetchingEta = true
                        actionScope.launch {
                            val error = viewModel.fetchTypicalEstimate(tripId)
                            fetchingEta = false
                            if (error == null) etaRefresh++
                            else Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            EventsSection(
                points = a.points,
                events = a.events,
                onEventClick = { event ->
                    selectedIndex = nearestPointIndex(a.points, event.tMs).coerceIn(0, maxPointIndex)
                    focusKey++
                    actionScope.launch { scrollState.animateScrollTo(mapAnchorY) }
                }
            )

            val speed = downsample(a.points.map { it.speedKmh.toFloat() }, 700)
            TimeSeriesChart(
                title = "Speed (km/h)",
                values = speed,
                unit = "",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                selectedIndex = scaledIndex(safeSelectedIndex, a.points.size, speed.size)
            )

            Text("Detailed metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatGrid(
                stats = detailedStats,
                modifier = Modifier.fillMaxWidth()
            )
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
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Safety factors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Share of moving time spent in each behaviour (lower is safer).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactorCell("Braking", trip.hardBrakePct, Color(0xFFEF4444), Modifier.weight(1f))
                FactorCell("Turns", trip.aggressiveTurnPct, Color(0xFFF59E0B), Modifier.weight(1f))
                FactorCell("Accel", trip.hardAccelPct, Color(0xFFF59E0B), Modifier.weight(1f))
                FactorCell("Jerky", trip.jerkyPct, Color(0xFF8B5CF6), Modifier.weight(1f))
            }
            if (trip.maxJerk > 0) {
                Text(
                    "Peak jerk ${"%.1f".format(trip.maxJerk)} m/s³ — how abruptly acceleration changed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasLimits) {
                val pct = trip.speedingPct * 100
                val color = if (trip.speedingPct > 0.15) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                Text(
                    "Speeding: ${"%.0f".format(pct)}% of the way over the posted limit" +
                        (if (trip.maxOverLimitKmh >= 1) " (peak ${"%.0f".format(trip.maxOverLimitKmh)} km/h over)" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    "Red on the map = over the limit. Limits matched for " +
                        "${"%.0f".format(trip.limitCoverage * 100)}% of the route (OpenStreetMap).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Speeding: no posted-limit data looked up for this route yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onCheckLimits, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check speed limits (OSM)")
                    }
                }
            }
        }
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("vs Google Maps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (!hasEta) {
                Text(
                    "No Google estimate for this trip yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onFetch, enabled = !fetching) {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Get typical estimate")
                    }
                }
                return@Column
            }

            val faster = actualS <= trip.googleEtaTrafficS
            val youColor = if (faster) Color(0xFF22C55E) else Color(0xFFEF4444)

            EtaTimeline(
                freeFlowS = trip.googleEtaFreeFlowS,
                googleS = trip.googleEtaTrafficS,
                youS = actualS,
                youColor = youColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EtaCell("Free-flow", trip.googleEtaFreeFlowS, Color(0xFF38BDF8), Modifier.weight(1f))
                EtaCell("Google ETA", trip.googleEtaTrafficS, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                EtaCell("You", actualS, youColor, Modifier.weight(1f))
            }

            val deltaS = actualS - trip.googleEtaTrafficS
            val absMin = (kotlin.math.abs(deltaS) / 60.0).roundToInt()
            val (verdict, color) = when {
                deltaS < -45 -> "$absMin min faster than Google's estimate" to Color(0xFF22C55E)
                deltaS > 45 -> "$absMin min slower than Google's estimate" to Color(0xFFEF4444)
                else -> "About the same as Google's estimate" to MaterialTheme.colorScheme.onSurface
            }
            Text(verdict, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
            Text(
                if (trip.etaSource == "live") "Estimate captured live when the trip ended."
                else "Typical traffic for this day & time.",
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
        EventType.POTHOLE -> "A sharp vertical jolt - pothole, speed bump, or rough patch."
    }
    return "$intensity  (${"%.2f".format(g)} g)"
}

@Composable
private fun EventsSection(
    points: List<TrackPoint>,
    events: List<DriveEvent>,
    onEventClick: (DriveEvent) -> Unit
) {
    Text("What happened", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (events.isEmpty()) {
        Text(
            "Smooth trip - no hard braking, acceleration or cornering recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Severity counts.
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

    val firstT = points.firstOrNull()?.tMs ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.sortedBy { it.tMs }.forEach { event ->
            EventRow(event, points, firstT, onClick = { onEventClick(event) })
        }
    }
}

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
