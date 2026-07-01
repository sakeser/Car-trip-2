package com.cartrip.uinext

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.RoutePoint
import com.cartrip.engine.api.TripEvent
import com.cartrip.engine.api.TripEventKind
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary
import com.cartrip.engine.api.TripTrackPoint

/**
 * :ui-next trip detail — a **map-first** trip story: the route map hero, a clean summary headline, then the
 * Driving Intelligence read (conditional Drive-Quality headline + Smoothness / Demand pillars). Efficiency
 * needs a vehicle profile the engine-api mapper doesn't hold, so it's added later via a vehicle gateway.
 * Engine access via com.cartrip.engine.api.* only. ASCII source (Cp1252 trap).
 */
@Composable
fun TripDetailNextScreen(tripId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trip by produceState<TripSummary?>(initialValue = null, tripId) { value = repo.getTrip(tripId) }
    val route by produceState<List<RoutePoint>>(initialValue = emptyList(), tripId) { value = repo.getRoute(tripId) }
    val track by produceState<List<TripTrackPoint>>(initialValue = emptyList(), tripId) { value = repo.getTrack(tripId) }
    val events by produceState<List<TripEvent>>(initialValue = emptyList(), tripId) { value = repo.getEvents(tripId) }

    NextScaffold(title = "Trip", onBack = onBack) { padding ->
        val t = trip
        if (t == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            // Pause page scroll while a finger is on the map so the map pans with ONE finger (matches legacy).
            // Without this the parent verticalScroll steals single-finger drags and the map needs two fingers.
            var mapTouched by remember { mutableStateOf(false) }
            // Trip Line scrubber <-> map sync: the selected track index drives a marker on the route. Reset when
            // the trip changes. The selected sample only marks the map when it has a usable fix (hasPosition).
            var selectedIndex by remember(tripId) { mutableStateOf<Int?>(null) }
            val selectedOnMap = selectedIndex?.let { track.getOrNull(it) }
                ?.takeIf { it.hasPosition }?.let { RoutePoint(it.lat, it.lon) }
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState(), enabled = !mapTouched),
            ) {
                if (route.size >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            // Watch presses on the Initial pass so scroll re-enables on lift (the map's
                            // AndroidView would otherwise swallow the release event).
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent(PointerEventPass.Initial)
                                        mapTouched = e.changes.any { it.pressed }
                                    }
                                }
                            },
                    ) {
                        TripMapHero(route = route, selected = selectedOnMap, modifier = Modifier.fillMaxSize())
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Summary headline
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                formatStart(t.startEpochMs),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Stat("Distance", formatKm(t.distanceMeters))
                                Stat("Duration", formatDuration(t.durationSeconds))
                            }
                        }
                    }

                    // Trip Line: speed-vs-time story with the posted limit + events.
                    if (track.size >= 2) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            TripLine(
                                track = track,
                                events = events,
                                selectedIndex = selectedIndex,
                                onScrub = { selectedIndex = it },
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                            )
                        }
                    }

                    // You vs Traffic: actual time against Google's with-traffic ETA (read-only; drives with a
                    // fetched ETA only -> the engine exposes etaTrafficSeconds as null otherwise).
                    t.etaTrafficSeconds?.let { typical ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            YouVsTraffic(
                                actualSeconds = t.durationSeconds,
                                typicalSeconds = typical,
                                freeFlowSeconds = t.etaFreeFlowSeconds,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                            )
                        }
                    }

                    // Driving Intelligence
                    val dq = t.driveQuality
                    if (dq != null) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "DRIVE QUALITY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(dq, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider()
                                t.smoothnessScore?.let { s ->
                                    PillarRow("Smoothness", t.smoothnessBand) { ScoreChip(s) }
                                }
                                t.stressScore?.let { s ->
                                    PillarRow("Demand", t.stressBand) { StressChip(s) }
                                }
                                // Efficiency is the 3rd Driving-Intelligence pillar; it needs a vehicle profile
                                // the engine-api mapper can't hold yet. Rough placeholder so the shape is visible.
                                PillarRow("Efficiency", "Coming soon") {
                                    Text(
                                        "--",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Events: the notable driving events on this trip (real data via getEvents).
                    if (events.isNotEmpty()) EventsCard(events)
                }
            }
        }
    }
}

/** A rough list of the trip's driving events (kind + time offset). Real, read-only engine-api data. */
@Composable
private fun EventsCard(events: List<TripEvent>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "EVENTS (${events.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (e in events) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(eventLabel(e.kind), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatDuration(e.offsetSeconds.toDouble()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** UI label for an event kind (presentation; the kinds are the engine-api enum). */
private fun eventLabel(kind: TripEventKind): String = when (kind) {
    TripEventKind.HARD_BRAKE -> "Hard brake"
    TripEventKind.HARD_ACCEL -> "Hard accel"
    TripEventKind.HARD_CORNER -> "Hard corner"
    TripEventKind.ROUGH_ROAD -> "Rough road"
    TripEventKind.OTHER -> "Event"
}

/** A stacked label + value stat (used in the summary headline row). */
@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** A Driving Intelligence pillar row: label on the left, a score chip + band word on the right. Shared with
 *  the Health screen ([InsightsContent]). */
@Composable
internal fun PillarRow(label: String, band: String?, chip: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            chip()
            band?.let {
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
