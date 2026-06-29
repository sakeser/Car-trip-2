package com.cartrip.analyzer.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.settings.VehiclePrefs
import com.cartrip.analyzer.analysis.SpeedTier
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.analysis.TripKind
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    var autoSpeedLimitsAttempted by remember(tripId) { mutableStateOf(false) }
    val ctx = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val trip by produceState<TripEntity?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadTrip(tripId)
    }
    val analysis by produceState<TripAnalysis?>(initialValue = null, tripId, etaRefresh) {
        value = viewModel.loadAnalysis(tripId)
    }
    val tripTitle by produceState("Trip", trip) {
        value = trip?.let { viewModel.loadTripTitle(it) } ?: "Trip"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tripTitle) },
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
                        trip?.let { t ->
                            val nonDrive = TripKind.isLikelyNonDrive(t)
                            DropdownMenuItem(
                                text = { Text(if (nonDrive) "Mark as a drive" else "Mark as walk / not a drive") },
                                leadingIcon = {
                                    Icon(
                                        if (nonDrive) Icons.Filled.DirectionsCar else Icons.Filled.DirectionsWalk,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    actionScope.launch {
                                        // Flip the effective kind: if it reads as a non-drive now, force drive, and vice versa.
                                        viewModel.setTripIsDrive(tripId, nonDrive)
                                        etaRefresh++
                                        CloudState.set {
                                            it.copy(lastMessage = if (nonDrive) "Marked as a drive." else "Marked as a walk / non-drive.")
                                        }
                                    }
                                }
                            )
                            if (t.userIsDrive != null) {
                                DropdownMenuItem(
                                    text = { Text("Reset to automatic") },
                                    leadingIcon = { Icon(Icons.Filled.Autorenew, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        actionScope.launch {
                                            // Clear the manual override; drive/walk reverts to the top-speed heuristic.
                                            viewModel.setTripIsDrive(tripId, null)
                                            etaRefresh++
                                            CloudState.set {
                                                it.copy(lastMessage = "Drive/walk set back to automatic.")
                                            }
                                        }
                                    }
                                )
                            }
                        }
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
        var cameraResetKey by remember(a.points) { mutableStateOf(0) }
        var mapAnchorY by remember(a.points) { mutableStateOf(0) }
        val scrollState = rememberScrollState()
        var mapTouched by remember { mutableStateOf(false) }
        // True only while the replay is actively advancing (after the on-open settle), so the map can
        // follow the car with a speed-based zoom. Pauses while a finger is on the map (manual pan).
        var replayPlaying by remember(a.points) { mutableStateOf(false) }
        val maxPointIndex = (a.points.size - 1).coerceAtLeast(0)
        val safeSelectedIndex = selectedIndex.coerceIn(0, maxPointIndex)
        val selectedPoint = a.points.getOrNull(safeSelectedIndex)
        // Replay-follow is for walks only. Drives keep the original whole-route replay (no camera follow),
        // since following + speed-zoom doesn't read well over a long, fast drive.
        val isWalkTrip = trip?.let { TripKind.isLikelyNonDrive(it) } == true
        val rawEvents = remember(a) { (a.events + a.fusedEvents).sortedBy { it.tMs } }
        // Cleaned presentation events: raw GPS/motion/fused signals remain available in Advanced,
        // export and storage, but the main map/list should show one marker per real-world moment.
        val shownEvents = remember(a) { DisplayEvents.clean(rawEvents, a.points) }
        val routeLimitCoverage = remember(a.points) { routeLimitCoverage(a.points) }
        val hasRouteSpeedLimits = routeLimitCoverage >= 0.4
        val shouldAutoFetchSpeedLimits =
            trip?.let { t ->
                !t.isSample &&
                    a.points.size >= 5 &&
                    !hasRouteSpeedLimits &&
                    !autoSpeedLimitsAttempted
            } == true
        LaunchedEffect(shouldAutoFetchSpeedLimits, tripId) {
            if (shouldAutoFetchSpeedLimits) {
                autoSpeedLimitsAttempted = true
                fetchingLimits = true
                try {
                    val error = viewModel.fetchSpeedLimits(tripId)
                    if (error == null) etaRefresh++
                } finally {
                    fetchingLimits = false
                }
            }
        }
        // Default to a clean map: only the start/end markers, no event icons. The user turns on
        // brake/accel/turn/bump layers from the filter chips when they want them.
        var eventFilters by remember(a) { mutableStateOf(emptySet<EventFilter>()) }
        var selectedEvent by remember(a) { mutableStateOf<DriveEvent?>(null) }
        var eventDetailAnchorY by remember(a) { mutableStateOf(0) }
        val visibleEvents = remember(shownEvents, eventFilters) {
            shownEvents.filter { eventFilterFor(it.type) in eventFilters }
        }
        // The Driving "All events" list shows every cleaned event EXCEPT bumps/potholes by default (they're
        // noisy / low-interest); selecting the Bumps filter chip reveals them. Other chips only gate markers.
        val listEventsForDriving = remember(shownEvents, eventFilters) {
            shownEvents.filter { eventFilterFor(it.type) != EventFilter.BUMPS || EventFilter.BUMPS in eventFilters }
        }
        val jumpToEvent: (DriveEvent) -> Unit = { event ->
            val sameEvent = selectedEvent == event
            selectedEvent = event
            selectedIndex = nearestPointIndex(a.points, event.tMs).coerceIn(0, maxPointIndex)
            focusKey++
            actionScope.launch {
                scrollState.animateScrollTo(if (sameEvent && eventDetailAnchorY > 0) eventDetailAnchorY else mapAnchorY)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Pause page scroll while a finger is on the map so it pans with one finger.
                .verticalScroll(scrollState, enabled = !mapTouched)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val startPt = a.points.firstOrNull()
            val endPt = a.points.lastOrNull()

            // --- Partial banner ---
            trip?.takeIf { it.isPartialRecording() }?.let { PartialBanner(it) }

            // --- Hero: overall score + clean time/duration/distance, sub-scores ---
            if (scores != null && trip != null) {
                TripHero(trip!!, m, scores)
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ScoreRing(m.smoothness)
                }
            }

            // --- You vs traffic ---
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

            // --- Map + replay timeline ---
            if (a.points.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onGloballyPositioned { mapAnchorY = it.positionInParent().y.roundToInt() }
                        .pointerInput(Unit) {
                            // Track press on the Initial pass so page scroll re-enables when the finger
                            // leaves the map (the map's AndroidView would otherwise swallow the lift).
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    mapTouched = e.changes.any { it.pressed }
                                }
                            }
                        }
                ) {
                    TripMap(
                        points = a.points,
                        events = visibleEvents,
                        selectedPoint = selectedPoint,
                        focusKey = focusKey,
                        resetKey = cameraResetKey,
                        replayFollow = replayPlaying && !mapTouched && isWalkTrip,
                        onEventClick = jumpToEvent,
                        onStartOpen = { startPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Start") } },
                        onStopOpen = { endPt?.let { TripActions.openInMaps(ctx, it.lat, it.lon, "Stop") } },
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
                    events = visibleEvents,
                    selectedIndex = safeSelectedIndex,
                    onSelectedIndex = { selectedIndex = it.coerceIn(0, maxPointIndex) },
                    onEventJump = jumpToEvent,
                    // Drives: reset to the whole route on play (original behavior). Walks: the map follows
                    // the walker instead, so skip the reset.
                    onReplayStart = { if (!isWalkTrip) cameraResetKey++ },
                    onPlayingChange = { replayPlaying = it }
                )
                // Filter chips sit below the scrubber: they gate which event markers show on the
                // map and timeline above.
                if (shownEvents.isNotEmpty()) {
                    EventFilterBar(
                        events = shownEvents,
                        selected = eventFilters,
                        onToggle = { filter ->
                            eventFilters = if (filter in eventFilters) eventFilters - filter else eventFilters + filter
                        }
                    )
                }
                selectedEvent?.let { event ->
                    EventDetailCard(
                        event = event,
                        rawEvents = rawEvents,
                        points = a.points,
                        modifier = Modifier.onGloballyPositioned {
                            eventDetailAnchorY = it.positionInParent().y.roundToInt()
                        }
                    )
                }
            } else {
                Text(
                    "No GPS track recorded for this trip.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Driving summary + road & ride ---
            trip?.let { t ->
                SafetyFactorsCard(
                    trip = t,
                    points = a.points,
                    events = shownEvents,
                    // All-events list defaults open and shows every event EXCEPT bumps/potholes; the Bumps
                    // chip reveals them. Other filter chips only gate the map markers (owner request, Rev CV).
                    listEvents = listEventsForDriving,
                    rawEventCount = rawEvents.size,
                    routeLimitCoverage = routeLimitCoverage,
                    checking = fetchingLimits,
                    onCheckLimits = {
                        fetchingLimits = true
                        actionScope.launch {
                            try {
                                val error = viewModel.fetchSpeedLimits(tripId)
                                if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                            } finally {
                                fetchingLimits = false
                                etaRefresh++
                            }
                        }
                    },
                    onEventJump = jumpToEvent
                )
            }
            trip?.let { t -> RoadRideCard(t) }
            // No fuel/cost card for non-drives (a walk burns no fuel).
            trip?.let { t -> if (!TripKind.isLikelyNonDrive(t)) FuelCostCard(t) }

            // --- More stats (collapsed) ---
            AdvancedSection(metrics = m)

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
private fun TripHero(trip: TripEntity, m: DriveMetrics, scores: TripScores) {
    val endMs = if (trip.endTime > 0) trip.endTime else trip.startTime + (m.durationS * 1000).toLong()
    // Headline fuel cost: recomputed live from the current vehicle profile + this trip's aggregates
    // (no Re-analyze needed). Matches the Fuel & cost card below.
    val distanceKm = m.distanceM / 1000.0
    val cost = if (distanceKm >= 0.05 && !TripKind.isLikelyNonDrive(trip)) {
        val v = VehiclePrefs.load(LocalContext.current)
        FuelEstimator.cost(FuelEstimator.litres(distanceKm, m.avgMovingSpeedMps * 3.6, m.idleS, v), v)
    } else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Date (eyebrow) + time range, grouped tightly — the screen title is time-only, so this is
            // the only place the day a past trip happened is shown.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Format.relativeDay(trip.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${Format.timeOfDay(trip.startTime)} – ${Format.timeOfDay(endMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroStat(Icons.Filled.Schedule, Format.tripMinutes(m.durationS))
                HeroStat(Icons.Filled.Route, Format.tripDistance(m.distanceM))
                cost?.let { HeroStat(Icons.Filled.LocalGasStation, String.format(java.util.Locale.US, "$%.2f", it)) }
            }
            if (TripKind.isLikelyNonDrive(trip)) {
                // A walk / non-drive: driving scores (Safety/Comfort/Pace) are meaningless. Mirror the
                // Past-trips list — flag it as a walk and show moving-average speed instead of the rings.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DirectionsWalk,
                        contentDescription = "Walk",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "  Walk / non-drive · ${Format.avgSpeedKmh(m.avgMovingSpeedMps)} avg",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniScoreRing("Safety", scores.safety)
                    MiniScoreRing("Comfort", scores.comfort)
                    MiniScoreRing("Pace", scores.speed)
                }
                // Drive Stress Score: higher = MORE demanding, the inverse of the green=good rings above, so
                // it gets its own pill + green->red scale rather than a fourth ring (which would read as good).
                StressScore.from(trip)?.let { s -> StressHeroPill(s) }
            }
        }
    }
}

/**
 * Drive Stress Score as a distinct hero pill. It's the inverse of Safety/Comfort/Pace (higher = more
 * stressful), so it deliberately does NOT use a score ring — a labelled pill on the green->red stress scale
 * keeps the "green = good" convention from being misread.
 */
@Composable
private fun StressHeroPill(s: StressScore.Result) {
    val color = StressColors.color(s.score)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(
            "Drive stress",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${s.band} (${s.score})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun HeroStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/** Small labelled score ring for the Safety / Comfort / Pace trio. */
@Composable
private fun MiniScoreRing(label: String, value: Int?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (value != null) {
            ScoreRing(value, ringSize = 52.dp)
        } else {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onEventJump: (DriveEvent) -> Unit,
    onReplayStart: () -> Unit = {},
    onPlayingChange: (Boolean) -> Unit = {}
) {
    val t0 = points.first().tMs
    val tN = points.last().tMs.coerceAtLeast(t0 + 1)
    val speeds = remember(points) { points.map { it.speedKmh.toFloat() } }
    // Per-point over-limit tier (needs OSM limits): yellow 0-10 over, red 10+ over.
    val tiers = remember(points) { points.map { SpeedTier.of(it.speedKmh, it.speedLimitKmh) } }
    val maxSpeed = (speeds.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val selFrac = if (points.size > 1) selectedIndex.toFloat() / (points.size - 1) else 0f
    val lineColor = MaterialTheme.colorScheme.primary
    val redColor = Color(0xFFEF4444)
    val yellowColor = Color(0xFFF59E0B)
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val density = LocalDensity.current
    // The cursor/map track selectedIndex at 60fps, but the speed + clock readouts sample at ~5 Hz
    // (and the speed eases between samples) so the digits stay readable during autoplay.
    val currentIndex by rememberUpdatedState(selectedIndex)
    var displayIndex by remember(points) { mutableStateOf(selectedIndex.coerceAtMost(points.lastIndex)) }
    LaunchedEffect(points) {
        while (true) { displayIndex = currentIndex; delay(200) }
    }
    val point = points.getOrNull(displayIndex)

    // Auto-play the scrubber on load; manual scrubbing pauses it. Replay length scales with the trip:
    // ~50% longer than before — 5 s up to ~20 min of driving, then ~1 s per 4 min, capped at 45 s.
    var playing by remember(points) { mutableStateOf(true) }
    var didInitialDelay by remember(points) { mutableStateOf(false) }
    // Tell the map to follow the car only once replay is actually moving (after the on-open settle), so
    // the initial whole-route fit isn't immediately overridden.
    LaunchedEffect(playing, didInitialDelay) { onPlayingChange(playing && didInitialDelay) }
    LaunchedEffect(playing, points) {
        if (!playing || points.size <= 1) return@LaunchedEffect
        // Let the screen settle for ~1 s before the replay starts moving (once, on open).
        if (!didInitialDelay) {
            delay(1000)
            didInitialDelay = true
            if (!playing) return@LaunchedEffect
        }
        val lastIdx = points.size - 1
        val tripMs = (points.last().tMs - points.first().tMs).coerceAtLeast(1L)
        val totalMs = (tripMs / 240f).coerceIn(5000f, 45000f)
        val startFrac = (if (selectedIndex >= lastIdx) 0 else selectedIndex).toFloat() / lastIdx
        val t0 = System.currentTimeMillis()
        while (playing) {
            val frac = (startFrac + (System.currentTimeMillis() - t0) / totalMs).coerceAtMost(1f)
            onSelectedIndex((lastIdx * frac).roundToInt())
            if (frac >= 1f) { playing = false } else delay(16)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val curTier = tiers.getOrNull(displayIndex) ?: SpeedTier.Tier.NONE
        val gaugeColor = when (curTier) {
            SpeedTier.Tier.RED -> redColor
            SpeedTier.Tier.YELLOW -> yellowColor
            SpeedTier.Tier.NONE -> MaterialTheme.colorScheme.primary
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (playing) {
                                playing = false
                            } else {
                                if (selectedIndex >= points.size - 1) onSelectedIndex(0)
                                playing = true
                                onReplayStart()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause replay" else "Play replay",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (point != null) {
                    Text(
                        "${Format.clock(((point.tMs - t0).coerceAtLeast(0) / 1000))} / ${Format.clock((tN - t0) / 1000)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (point != null) {
                // Average neighbours, then ease toward it, so the gauge glides instead of jittering.
                val lo = (displayIndex - 3).coerceAtLeast(0)
                val hi = (displayIndex + 3).coerceAtMost(points.lastIndex)
                val smoothed = (lo..hi).sumOf { points[it].speedKmh } / (hi - lo + 1)
                val animSpeed by animateFloatAsState(smoothed.toFloat(), label = "replaySpeed")
                SpeedGauge(animSpeed.toDouble(), maxSpeed.toDouble(), gaugeColor)
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
                    // Stroke segment-by-segment so over-limit stretches show yellow/red.
                    for (i in 1 until speeds.size) {
                        val tier = SpeedTier.worse(tiers[i], tiers[i - 1])
                        val segColor = when (tier) {
                            SpeedTier.Tier.RED -> redColor
                            SpeedTier.Tier.YELLOW -> yellowColor
                            SpeedTier.Tier.NONE -> lineColor
                        }
                        drawLine(
                            segColor,
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
            onValueChange = { playing = false; onSelectedIndex(it.roundToInt()) },
            valueRange = 0f..(points.size - 1).coerceAtLeast(1).toFloat()
        )
    }
}

/** Live speed readout + a bar that fills with speed (relative to the trip max), coloured by tier. */
@Composable
private fun SpeedGauge(speedKmh: Double, maxSpeedKmh: Double, color: Color) {
    val frac = (speedKmh / maxSpeedKmh.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${speedKmh.roundToInt()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                " km/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Box(
            modifier = Modifier.width(96.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        ) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
private fun EventFilterBar(
    events: List<DriveEvent>,
    selected: Set<EventFilter>,
    onToggle: (EventFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FilterList,
            contentDescription = "Filter events",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        EventFilter.values().forEach { filter ->
            val count = events.count { eventFilterFor(it.type) == filter }
            if (count > 0) {
                FilterChip(
                    selected = filter in selected,
                    onClick = { onToggle(filter) },
                    modifier = Modifier.height(28.dp),
                    leadingIcon = {
                        Icon(filterIcon(filter), contentDescription = filter.label, modifier = Modifier.size(13.dp))
                    },
                    label = {
                        Text("${filter.label} $count", style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }
    }
}

private fun filterIcon(filter: EventFilter): ImageVector = when (filter) {
    EventFilter.BRAKING -> Icons.Filled.StopCircle
    EventFilter.ACCEL -> Icons.Filled.Speed
    EventFilter.TURNS -> Icons.Filled.Route
    EventFilter.BUMPS -> BumpGlyph
    EventFilter.STOPS -> Icons.Filled.PriorityHigh
}

@Composable
private fun EventDetailCard(
    event: DriveEvent,
    rawEvents: List<DriveEvent>,
    points: List<TrackPoint>,
    modifier: Modifier = Modifier
) {
    val style = eventStyle(event)
    val point = points.getOrNull(nearestPointIndex(points, event.tMs))
    val firstT = points.firstOrNull()?.tMs ?: event.tMs
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp))
                        .background(style.color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(style.icon, contentDescription = null, tint = style.color)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(style.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = style.color)
                    Text(
                        "${Format.duration((event.tMs - firstT).coerceAtLeast(0) / 1000.0)} into trip" +
                            (point?.let { " | ${Format.speedKmh(it.speedKmh)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(eventExplanation(event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatGrid(
                stats = listOf(
                    Stat("G-force", "%.2fg".format(event.magnitude / 9.80665), style.color),
                    Stat("Speed", point?.let { Format.speedKmh(it.speedKmh) } ?: "-")
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AdvancedSection(metrics: DriveMetrics) {
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
                Text("More stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                }
            }
        }
    }
}


@Composable
private fun SafetyFactorsCard(
    trip: TripEntity,
    points: List<TrackPoint>,
    events: List<DriveEvent>,
    listEvents: List<DriveEvent>,
    rawEventCount: Int,
    routeLimitCoverage: Double,
    checking: Boolean,
    onCheckLimits: () -> Unit,
    onEventJump: (DriveEvent) -> Unit
) {
    val hasScoredLimits = trip.limitCoverage >= 0.4
    val hasRouteLimits = routeLimitCoverage >= 0.4
    val hasLimits = hasScoredLimits && hasRouteLimits
    val needsMapRefresh = hasScoredLimits && !hasRouteLimits
    val speedingSummary = remember(points) { speedingSummary(points) }
    val eventSummaries = remember(events) { drivingEventSummaries(events) }
    // Events show by default (owner request) — the full tap-to-jump list is open, collapsible if long.
    var listExpanded by remember { mutableStateOf(true) }
    val firstT = points.firstOrNull()?.tMs ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Driving", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // (Drive Stress Score now headlines the hero card — see StressHeroPill in TripHero.)
            if (eventSummaries.isEmpty()) {
                Text(
                    "No major braking, acceleration, or turn events detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                eventSummaries.forEach { summary ->
                    DrivingEventSummaryRow(summary, points)
                }
            }
            if (hasLimits) {
                // Speed limits auto-fetch on open and cache by OSM way id, so no manual "refresh" button.
                val notable = speedingSummary != null && speedingSummary.speedingDurationS >= 1.0
                SpeedingSummaryRow(if (notable) speedingSummary else null)
            } else {
                if (needsMapRefresh) {
                    Text(
                        "Speed limits need refreshing to color the route.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onCheckLimits, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (needsMapRefresh) "Restore route colors" else "Check speed limits")
                    }
                }
            }

            // Drawdowns: forced cruise-then-slow-then-recover events (traffic / stop-and-go). Feeds the
            // Drive Stress Score; surfaced here as a compact line when present.
            if (trip.drawdownCount > 0) {
                Text(
                    "Forced slowdowns: ${trip.drawdownCount} (traffic / stop-and-go)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Consolidated event detail: the full tap-to-jump list lives here (no separate card).
            if (listEvents.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { listExpanded = !listExpanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All events · ${listEvents.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Icon(
                        if (listExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (listExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (listExpanded) {
                    listEvents.sortedBy { it.tMs }.forEach { event ->
                        EventRow(event, points, firstT, onClick = { onEventJump(event) })
                    }
                }
            }
        }
    }
}

private val SPEED_BLUE = Color(0xFF0EA5E9)   // matches the map route polyline
private val SPEED_RED = Color(0xFFEF4444)

@Composable
private fun SpeedingSummaryRow(speedingSummary: SpeedingSummary?) {
    val color = if (speedingSummary != null) SPEED_RED else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = color)
            }
            Text(
                "Speeding",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.weight(1f)
            )
        }
        if (speedingSummary == null) {
            Text(
                "No notable speeding on covered roads.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 1) Share of moving time spent over the limit. 2) Top speed vs the limit, overage in red.
            SpeedingShareBar(speedingSummary)
            PeakLimitBar(speedingSummary.peakSpeedKmh, speedingSummary.peakLimitKmh)
        }
    }
}

/** Horizontal bar: red fill = fraction of covered moving time spent over the limit. */
@Composable
private fun SpeedingShareBar(s: SpeedingSummary) {
    val frac = if (s.coveredMovingDurationS > 0.0) {
        (s.speedingDurationS / s.coveredMovingDurationS).toFloat().coerceIn(0f, 1f)
    } else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Over the limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${s.percentText()} of moving time", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SPEED_RED)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(frac.coerceAtLeast(0.015f)).fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp)).background(SPEED_RED)
            )
        }
    }
}

/** Horizontal speed bar: blue up to the limit, red for the overage. Labels for limit, peak, and over. */
@Composable
private fun PeakLimitBar(peakKmh: Double, limitKmh: Double) {
    val over = (peakKmh - limitKmh).coerceAtLeast(0.0)
    val maxV = (peakKmh * 1.15).coerceAtLeast(1.0)
    val limitW = limitKmh.toFloat().coerceAtLeast(0.001f)
    val overW = over.toFloat().coerceAtLeast(0.001f)
    val restW = (maxV - peakKmh).toFloat().coerceAtLeast(0.001f)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Top speed vs limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${peakKmh.roundToInt()} km/h", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SPEED_RED)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(limitW).fillMaxHeight().background(SPEED_BLUE))
                Box(modifier = Modifier.weight(overW).fillMaxHeight().background(SPEED_RED))
                Box(modifier = Modifier.weight(restW))   // empty headroom shows the track
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Limit ${limitKmh.roundToInt()} km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+${over.roundToInt().coerceAtLeast(1)} over", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SPEED_RED)
        }
    }
}

private data class DrivingEventSummary(
    val label: String,
    val count: Int,
    val strongest: DriveEvent,
    val color: Color,
    val icon: ImageVector
)

@Composable
private fun DrivingEventSummaryRow(summary: DrivingEventSummary, points: List<TrackPoint>) {
    val point = points.getOrNull(nearestPointIndex(points, summary.strongest.tMs))
    val speed = point?.let { Format.speedKmh(it.speedKmh) }
    val g = summary.strongest.magnitude / 9.80665
    val frac = (g / drivingRefG(summary.strongest.type)).toFloat().coerceIn(0.06f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                .background(summary.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(summary.icon, contentDescription = null, tint = summary.color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(summary.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = summary.color)
                CountPips(summary.count, summary.color)
            }
            // Intensity bar: strongest event's g relative to a "very hard" reference for its type.
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(summary.color))
            }
            Text(
                "strongest ${"%.2fg".format(g)}" + (speed?.let { " at $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Count shown as up to 5 dots plus the number, in the event's colour. */
@Composable
private fun CountPips(count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count.coerceAtMost(5)) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/** "Very hard" g reference per event type — the point at which the intensity bar reads full. */
private fun drivingRefG(type: EventType): Double = when (type) {
    EventType.BRAKE -> 0.65
    EventType.ACCEL -> 0.55
    EventType.CORNER, EventType.SWERVE -> 0.65
    EventType.POTHOLE -> 0.6
    EventType.HARSH_STOP -> 0.6
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
    val hasData = trip.potholeCount > 0 || trip.roughStretchCount > 0 || trip.harshStopCount > 0 ||
        trip.roughRoadPct > 0.0
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
                RoadCell("Rough stretches", trip.roughStretchCount.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
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
private fun FuelCostCard(trip: TripEntity) {
    val distanceKm = trip.distanceM / 1000.0
    if (distanceKm < 0.05) return
    val v = VehiclePrefs.load(LocalContext.current)
    val litres = FuelEstimator.litres(distanceKm, trip.avgMovingSpeedMps * 3.6, trip.idleS, v)
    val cost = FuelEstimator.cost(litres, v)
    val l100 = FuelEstimator.tripL100(distanceKm, litres)
    val ratedCombined = FuelEstimator.combinedL100(v) * v.calibration   // effective combined rating
    val loc = java.util.Locale.US
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fuel & cost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                String.format(loc, "Estimated for your %s at $%.2f/L. Tap the fuel icon on Home to tune.", v.label, v.pricePerL),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelCell(Icons.Filled.LocalGasStation, String.format(loc, "%.2f", litres), "litres", Color(0xFF0EA5E9), Modifier.weight(1f))
                FuelCell(Icons.Filled.AttachMoney, String.format(loc, "%.2f", cost), "cost", Color(0xFF22C55E), Modifier.weight(1f))
                FuelCell(Icons.Filled.Speed, String.format(loc, "%.1f", l100), "L/100km", Color(0xFF8B5CF6), Modifier.weight(1f))
            }
            // Highlighted economy rating for this drive vs the vehicle's (effective) combined rating.
            // Skip it on very short trips: cold-start + idle make L/100km meaninglessly high (a 600 m
            // errand isn't "212% worse than rated"), so the red chip would only mislead.
            if (ratedCombined > 0.0 && distanceKm >= 3.0) {
                val deltaFrac = (l100 - ratedCombined) / ratedCombined
                val better = deltaFrac <= 0.0
                val pct = (abs(deltaFrac) * 100).roundToInt()
                val chip = if (better) Color(0xFF22C55E) else Color(0xFFEF4444)
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(chip.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (better) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null, tint = chip, modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (pct < 1) String.format(loc, "On par with rated %.1f L/100km", ratedCombined)
                        else String.format(loc, "%d%% %s fuel vs rated %.1f", pct, if (better) "less" else "more", ratedCombined),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = chip,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A fuel stat with an icon and a split value/unit so long units (L/100km) don't wrap the number. */
@Composable
private fun FuelCell(icon: ImageVector, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (TripKind.isLikelyNonDrive(trip)) {
                // A walk / non-drive: a driving traffic ETA would be meaningless, so don't offer it.
                Text("You vs traffic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "This looks like a walk or non-drive - no traffic comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            if (!hasEta) {
                Text("You vs traffic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            val typicalS = trip.googleEtaTrafficS
            val freeFlowS = trip.googleEtaFreeFlowS.takeIf { it > 0.0 } ?: typicalS
            val youColor = when {
                actualS <= typicalS -> ETA_GREEN
                actualS <= typicalS * 1.15 -> ETA_AMBER
                else -> ETA_RED
            }
            val deltaS = actualS - typicalS
            val deltaMin = (deltaS / 60.0).roundToInt()
            val verdict = when {
                deltaMin <= -1 -> "${-deltaMin} min faster"
                deltaMin >= 1 -> "$deltaMin min slower"
                else -> "On pace"
            }
            val deltaFrac = if (typicalS > 0) deltaS / typicalS else 0.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("You vs traffic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(etaAnimalEmoji(deltaFrac), style = MaterialTheme.typography.titleMedium)
                    Text(verdict, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = youColor)
                }
            }
            // Google shown as a best-case..typical RANGE band; You is a bar contrasted against it,
            // coloured by deficit vs Google and ordered shortest-first.
            EtaCompare(freeFlowS = freeFlowS, typicalS = typicalS, actualS = actualS, youColor = youColor)
        }
    }
}

private val ETA_GREEN = Color(0xFF22C55E)
private val ETA_AMBER = Color(0xFFF59E0B)
private val ETA_RED = Color(0xFFEF4444)
private val ETA_TYPICAL = Color(0xFF64748B)  // "no traffic" portion of the estimate — neutral slate
private val ETA_TRAFFIC = Color(0xFFB91C1C)  // "traffic delay" portion of the estimate — red
private val ETA_MAPS = Color(0xFFEA4335)     // approximated Google Maps pin tint

// Built from code points so the source stays ASCII (see GeoNamer.ARROW / the Cp1252 encoding note).
private val ETA_TURTLE = String(Character.toChars(0x1F422))
private val ETA_DOLPHIN = String(Character.toChars(0x1F42C))
private val ETA_RABBIT = String(Character.toChars(0x1F407))
private val ETA_HORSE = String(Character.toChars(0x1F40E))

/**
 * Playful animal for how the drive compared with Google's typical time, where
 * deltaFrac = (actual - typical) / typical. Slower = turtle, about-on-pace = dolphin, faster =
 * rabbit, much faster = horse. Thresholds are intentionally simple to re-tune later.
 */
private fun etaAnimalEmoji(deltaFrac: Double): String = when {
    deltaFrac >= 0.12 -> ETA_TURTLE     // 12%+ slower than Google
    deltaFrac >= -0.03 -> ETA_DOLPHIN   // roughly on pace
    deltaFrac >= -0.12 -> ETA_RABBIT    // a bit faster
    else -> ETA_HORSE                   // 12%+ faster
}

/**
 * One to-scale time axis (0..max). The outlined box is Google's estimate: a light "no traffic" portion
 * up to the free-flow time, then a red "traffic delay" portion out to the typical/with-traffic time.
 * "You" is a marker that can land anywhere on the axis — left of the box = beat free-flow, inside the
 * red = beat the traffic, past the box = slower than Google expected. The box wipes in and the marker
 * slides to place on load.
 */
@Composable
private fun EtaCompare(freeFlowS: Double, typicalS: Double, actualS: Double, youColor: Color) {
    val hasFree = freeFlowS >= 1.0 && freeFlowS < typicalS
    // Scale to a "nice" axis max ~25% above the largest value, so the longest bar fills ~80% of the width
    // (not edge-to-edge) and the scale reads in round minutes.
    val axisMaxMin = niceEtaAxisMaxMin(maxOf(typicalS, actualS, freeFlowS) / 60.0)
    val axisMaxS = axisMaxMin * 60.0
    val fFree = (freeFlowS / axisMaxS).toFloat().coerceIn(0f, 1f)
    val fTyp = (typicalS / axisMaxS).toFloat().coerceIn(0f, 1f)
    val fYou = (actualS / axisMaxS).toFloat().coerceIn(0f, 1f)

    var loaded by remember(freeFlowS, typicalS, actualS) { mutableStateOf(false) }
    LaunchedEffect(freeFlowS, typicalS, actualS) { loaded = true }
    val p by animateFloatAsState(if (loaded) 1f else 0f, animationSpec = tween(800), label = "etaWipe")

    val freeMin = (freeFlowS / 60.0).roundToInt()
    val typMin = (typicalS / 60.0).roundToInt()
    val youMin = (actualS / 60.0).roundToInt()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = Color(0xFF2563EB)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // "you" callout, sitting above the marker.
        Box(modifier = Modifier.fillMaxWidth().height(15.dp)) {
            Text(
                "you  $youMin min",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = markerColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(BiasAlignment(2f * fYou - 1f, 0f)).alpha(p)
            )
        }
        // Time axis + estimate box + "you" marker.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val w = maxWidth
            Box(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp)
                    .background(axis.copy(alpha = 0.18f))
            )
            Row(
                modifier = Modifier.align(Alignment.CenterStart).height(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.5.dp, axis.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
            ) {
                Box(modifier = Modifier.width(w * fFree * p).fillMaxHeight().background(ETA_TYPICAL))
                Box(modifier = Modifier.width(w * (fTyp - fFree).coerceAtLeast(0f) * p).fillMaxHeight().background(ETA_TRAFFIC))
            }
            // The marker: a single crisp youColor line with a thin dark outline (not a white halo)
            // so it reads cleanly on top of any band without the white edge artifact.
            Box(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = w * fYou * p - 2.dp)
                    .width(4.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(youColor)
                    .border(1.dp, Color.Black.copy(alpha = 0.30f), RoundedCornerShape(2.dp))
            )
        }
        // Scale legend — fixed left/right ends so the labels never overlap, whatever the times are.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (hasFree) "no traffic  $freeMin min" else "Google  $typMin min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            if (hasFree) {
                Text(
                    "with traffic  $typMin min",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ETA_TRAFFIC,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        // Minute scale (0..axisMax) so the bar lengths have a clear reference, evenly spaced under the bar.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEachIndexed { i, frac ->
                Text(
                    if (i == 4) "${(axisMaxMin * frac).roundToInt()} min" else "${(axisMaxMin * frac).roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

/** A round-number axis max ~25% above the largest ETA so the longest bar fills ~80% and ticks are tidy. */
private fun niceEtaAxisMaxMin(dataMin: Double): Double = BarScale.niceAxisMax(dataMin, headroom = 1.25)

private enum class EventFilter(val label: String) {
    BRAKING("Brakes"),
    ACCEL("Accel"),
    TURNS("Turns"),
    BUMPS("Bumps"),
    STOPS("Stops")
}

private fun eventFilterFor(type: EventType): EventFilter = when (type) {
    EventType.BRAKE -> EventFilter.BRAKING
    EventType.ACCEL -> EventFilter.ACCEL
    EventType.CORNER, EventType.SWERVE -> EventFilter.TURNS
    EventType.POTHOLE -> EventFilter.BUMPS
    EventType.HARSH_STOP -> EventFilter.STOPS
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
        // Yellow hump, matching the map's bump marker (TripMap MarkerGlyph.BUMP, rgb 245,158,11).
        EventStyle("Pothole / big bump", Color(0xFFF59E0B), BumpGlyph)
    EventType.HARSH_STOP ->
        // Magenta, matching the map's harsh-stop marker (TripMap rgb 219,39,119).
        if (event.magnitude >= 4.5) EventStyle("Very harsh stop", Color(0xFFBE123C), Icons.Filled.PriorityHigh)
        else EventStyle("Harsh stop", Color(0xFFDB2777), Icons.Filled.PriorityHigh)
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
        EventType.HARSH_STOP -> if (event.magnitude >= 4.5) "Braked hard right to a stop - passengers thrown forward."
        else "Firm braking all the way to a stop."
    }
    return "$intensity  (${"%.2f".format(g)} g)"
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
                } else if (event.source == "summary") {
                    Text(
                        "nearby detector signals grouped",
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

private fun drivingEventSummaries(events: List<DriveEvent>): List<DrivingEventSummary> {
    val driving = events.filter {
        it.type == EventType.BRAKE || it.type == EventType.ACCEL ||
            it.type == EventType.CORNER || it.type == EventType.SWERVE
    }
    fun strongest(candidates: List<DriveEvent>): DriveEvent? =
        candidates.maxByOrNull { eventSummaryStrength(it) }

    val brakes = driving.filter { it.type == EventType.BRAKE }
    val accels = driving.filter { it.type == EventType.ACCEL }
    val turns = driving.filter { it.type == EventType.CORNER || it.type == EventType.SWERVE }
    val harshStops = events.filter { it.type == EventType.HARSH_STOP }
    return listOfNotNull(
        strongest(brakes)?.let {
            DrivingEventSummary("Hard braking", brakes.size, it, Color(0xFFEF4444), Icons.Filled.StopCircle)
        },
        strongest(accels)?.let {
            DrivingEventSummary("Hard acceleration", accels.size, it, Color(0xFFF59E0B), Icons.Filled.Speed)
        },
        strongest(turns)?.let {
            DrivingEventSummary("Sharp turns", turns.size, it, Color(0xFFF59E0B), Icons.Filled.Route)
        },
        strongest(harshStops)?.let {
            DrivingEventSummary("Harsh stops", harshStops.size, it, Color(0xFFDB2777), Icons.Filled.PriorityHigh)
        }
    )
}

private fun eventSummaryStrength(event: DriveEvent): Double =
    when (event.type) {
        EventType.SWERVE -> maxOf(event.magnitude / 9.80665, if (event.confidence >= 0.8) 0.25 else 0.0)
        else -> event.magnitude / 9.80665
    }

private data class SpeedingSummary(
    val speedingDurationS: Double,
    val coveredMovingDurationS: Double,
    val peakSpeedKmh: Double,
    val peakLimitKmh: Double
) {
    fun percentText(): String {
        val pct = if (coveredMovingDurationS > 0.0) {
            (speedingDurationS / coveredMovingDurationS * 100.0).roundToInt()
        } else 0
        return "$pct%"
    }
}

/** O8: the speeding peak must sit within an over-limit run sustained this long, not a single GPS snap. */
private const val MIN_PEAK_RUN_S = 2.0

private fun speedingSummary(points: List<TrackPoint>): SpeedingSummary? {
    if (points.size < 2) return null
    var speedingS = 0.0
    var coveredMovingS = 0.0
    var peak: TrackPoint? = null         // best within a sustained over-limit run
    var fallbackPeak: TrackPoint? = null // best overall, used only if no run is sustained
    // Current run of consecutive over-limit samples.
    var runS = 0.0
    var runBest: TrackPoint? = null

    fun flushRun() {
        val rb = runBest
        if (rb != null && runS >= MIN_PEAK_RUN_S && (peak == null || isWorseSpeedingPoint(rb, peak!!))) {
            peak = rb
        }
        runS = 0.0
        runBest = null
    }

    for (i in 1 until points.size) {
        val p = points[i]
        if (p.speedKmh < 8.0 || p.speedLimitKmh <= 0.0) { flushRun(); continue }

        val dt = ((p.tMs - points[i - 1].tMs) / 1000.0).coerceIn(0.0, 3.0)
        coveredMovingS += dt

        val over = p.speedKmh - p.speedLimitKmh
        if (over <= 3.0) { flushRun(); continue }

        speedingS += dt
        if (fallbackPeak == null || isWorseSpeedingPoint(p, fallbackPeak)) fallbackPeak = p
        runS += dt
        if (runBest == null || isWorseSpeedingPoint(p, runBest!!)) runBest = p
    }
    flushRun()

    // Prefer a peak from a sustained run; fall back to the worst point only if speeding was all snaps.
    val peakPoint = peak ?: fallbackPeak ?: return null
    if (coveredMovingS <= 0.0 || speedingS <= 0.0) return null
    return SpeedingSummary(
        speedingDurationS = speedingS,
        coveredMovingDurationS = coveredMovingS,
        peakSpeedKmh = peakPoint.speedKmh,
        peakLimitKmh = peakPoint.speedLimitKmh
    )
}

private fun isWorseSpeedingPoint(a: TrackPoint, b: TrackPoint): Boolean {
    val aOver = a.speedKmh - a.speedLimitKmh
    val bOver = b.speedKmh - b.speedLimitKmh
    if (abs(aOver - bOver) >= 0.5) return aOver > bOver

    val aRatio = a.speedKmh / a.speedLimitKmh
    val bRatio = b.speedKmh / b.speedLimitKmh
    return aRatio > bRatio
}

private fun routeLimitCoverage(points: List<TrackPoint>): Double {
    val moving = points.count { it.speedKmh >= 8.0 }
    if (moving == 0) return 0.0
    val covered = points.count { it.speedKmh >= 8.0 && it.speedLimitKmh > 0.0 }
    return covered.toDouble() / moving
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
