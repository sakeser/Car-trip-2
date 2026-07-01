package com.cartrip.uinext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.RoutePoint
import com.cartrip.engine.api.TripEvent
import com.cartrip.engine.api.TripEventKind
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map tab (spec's 4th tab) — a **Map Hub** for the spatial-intelligence surface. Real, read-only data: it
 * overlays the recent drives' routes (via `getRoute`) and, when the Events layer is on, the trouble spots
 * (hard brake/accel/corner + rough-road events, via the position-enriched `getEvents`) as coloured pins, framed
 * to fit. Layer chips toggle Routes + Events; Speeding / Heatmap are disabled placeholders for later aggregate
 * gateways. Rough, read-only. ASCII source (Cp1252 trap).
 */
@Composable
internal fun MapHubScreen(trips: List<TripSummary>?, onOpenTrip: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val recentIds = remember(trips) { trips.orEmpty().filter { it.isDrive }.take(RECENT_LIMIT).map { it.id } }

    var showRoutes by remember { mutableStateOf(true) }
    var showEvents by remember { mutableStateOf(false) }
    var showSpeeding by remember { mutableStateOf(false) }
    // Each trip's Smoothness (0..100, or null) so routes can be coloured green=smooth -> red=rough.
    val smoothById = remember(trips) { trips.orEmpty().associate { it.id to it.smoothnessScore } }

    // Keep each route paired with its trip id so a tap can open that trip.
    val routes by produceState<List<Pair<Long, List<LatLng>>>?>(initialValue = null, recentIds) {
        value = recentIds.mapNotNull { id ->
            val r = repo.getRoute(id)
            if (r.size < 2) null else id to r.downsample(MAX_POINTS_PER_ROUTE).map { p -> LatLng(p.lat, p.lon) }
        }
    }
    // Positioned events across the recent trips — loaded lazily only when the layer is on (getEvents reads the
    // analysis track to place each event, so it isn't free). Capped so a busy history can't flood the map.
    val events by produceState<List<TripEvent>>(initialValue = emptyList(), recentIds, showEvents) {
        value = if (!showEvents) emptyList()
        else recentIds.flatMap { repo.getEvents(it) }.filter { it.hasPosition }.take(MAX_EVENT_PINS)
    }
    // Over-limit route segments, tiered minor/major — lazy (getTrack reads the analysis track), capped.
    val speeding by produceState<List<SpeedingSegment>>(initialValue = emptyList(), recentIds, showSpeeding) {
        value = if (!showSpeeding) emptyList()
        else recentIds.flatMap { repo.getTrack(it).speedingSegments() }.take(MAX_SPEEDING_SEGMENTS)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = showRoutes, onClick = { showRoutes = !showRoutes }, label = { Text("Routes") })
            FilterChip(selected = showEvents, onClick = { showEvents = !showEvents }, label = { Text("Events") })
            FilterChip(selected = showSpeeding, onClick = { showSpeeding = !showSpeeding }, label = { Text("Speeding") })
            FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Heatmap") })
        }
        Text(
            when (val r = routes) {
                null -> "Loading recent routes..."
                else -> buildString {
                    append("${r.size} recent ${if (r.size == 1) "route" else "routes"}")
                    if (showEvents) append(" $MIDDOT ${events.size} events")
                    if (showSpeeding) append(" $MIDDOT ${speeding.size} speeding")
                    if (!showEvents && !showSpeeding) append(" $MIDDOT tap a route to open it")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (showRoutes) {
            // Routes are coloured by Smoothness; a compact legend so the colour reads.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LegendDot(SmoothGreen, "Smooth")
                LegendDot(SmoothAmber, "OK")
                LegendDot(SmoothRed, "Rough")
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val r = routes
            when {
                r == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                r.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "No routes yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> RouteOverlayMap(
                    routes = r,
                    events = if (showEvents) events else emptyList(),
                    speeding = if (showSpeeding) speeding else emptyList(),
                    showRoutes = showRoutes,
                    smoothById = smoothById,
                    onOpenTrip = onOpenTrip,
                )
            }
        }
    }
}

@Composable
private fun RouteOverlayMap(
    routes: List<Pair<Long, List<LatLng>>>,
    events: List<TripEvent>,
    speeding: List<SpeedingSegment>,
    showRoutes: Boolean,
    smoothById: Map<Long, Int?>,
    onOpenTrip: (Long) -> Unit,
) {
    // Small round pins for events (the default teardrop markers overlap into an unreadable wall at density).
    val dotIcons = remember { TripEventKind.values().associateWith { dotIcon(eventArgb(it)) } }
    // Frame to the routes (loaded regardless of the toggle) so the view is stable as layers turn on/off.
    val all = remember(routes) { routes.flatMap { it.second } }
    val bounds = remember(all) {
        val b = LatLngBounds.builder()
        all.forEach(b::include)
        b.build()
    }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(all.firstOrNull() ?: LatLng(43.7, -79.4), 10f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded, bounds) {
        if (mapLoaded) runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 100)) }
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        onMapLoaded = { mapLoaded = true },
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
    ) {
        if (showRoutes) {
            routes.forEach { (id, line) ->
                Polyline(
                    points = line,
                    color = routeColor(smoothById[id]),
                    width = 8f,
                    jointType = JointType.ROUND,
                    clickable = true,
                    tag = id,
                    onClick = { poly -> (poly.tag as? Long)?.let(onOpenTrip) },
                )
            }
        }
        // Over-limit segments drawn above the routes (yellow = minor, red = major).
        speeding.forEach { seg ->
            Polyline(
                points = seg.points.map { LatLng(it.lat, it.lon) },
                color = if (seg.tier == SpeedTier.MAJOR) SpeedRed else SpeedYellow,
                width = 12f,
                jointType = JointType.ROUND,
                zIndex = 1f,
            )
        }
        events.forEach { e ->
            Marker(
                state = MarkerState(LatLng(e.lat, e.lon)),
                icon = dotIcons[e.kind],
                anchor = Offset(0.5f, 0.5f),
                title = eventTitle(e.kind),
            )
        }
    }
}

/** A small colour swatch + label for the route-colour legend. */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Route colour by the trip's Smoothness (green=smooth -> red=rough); unknown falls back to route blue. Mirrors
 *  the ScoreChip thresholds (80 / 60) so the map reads consistently with the rest of :ui-next. */
private fun routeColor(smoothness: Int?): Color = when {
    smoothness == null -> RouteBlue
    smoothness >= 80 -> SmoothGreen
    smoothness >= 60 -> SmoothAmber
    else -> SmoothRed
}

/** Keep at most [max] evenly-spaced points (endpoints preserved) so the hub stays light with many routes. */
private fun List<RoutePoint>.downsample(max: Int): List<RoutePoint> {
    if (size <= max) return this
    val step = size / max
    return filterIndexed { i, _ -> i % step == 0 || i == lastIndex }
}

/** ARGB colour per event kind (matches the Trip Line event palette). */
private fun eventArgb(kind: TripEventKind): Int = when (kind) {
    TripEventKind.HARD_BRAKE -> 0xFFEF4444.toInt()
    TripEventKind.HARD_ACCEL -> 0xFFF59E0B.toInt()
    TripEventKind.HARD_CORNER -> 0xFF6366F1.toInt()
    TripEventKind.ROUGH_ROAD -> 0xFF9CA3AF.toInt()
    TripEventKind.OTHER -> 0xFF9CA3AF.toInt()
}

/** A small round marker bitmap (white ring + coloured centre) — far more legible than the default teardrop when
 *  many events overlap. */
private fun dotIcon(argb: Int): BitmapDescriptor {
    val size = 30
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = size / 2f
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(c, c, c, paint)
    paint.color = argb
    canvas.drawCircle(c, c, c - 4f, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun eventTitle(kind: TripEventKind): String = when (kind) {
    TripEventKind.HARD_BRAKE -> "Hard brake"
    TripEventKind.HARD_ACCEL -> "Hard accel"
    TripEventKind.HARD_CORNER -> "Hard corner"
    TripEventKind.ROUGH_ROAD -> "Rough road"
    TripEventKind.OTHER -> "Event"
}

private val RouteBlue = Color(0xFF4285F4)
private val SmoothGreen = Color(0xFF22C55E)
private val SmoothAmber = Color(0xFFF59E0B)
private val SmoothRed = Color(0xFFEF4444)
private val SpeedYellow = Color(0xFFEAB308)   // minor: 0..10 km/h over
private val SpeedRed = Color(0xFFDC2626)       // major: 10+ km/h over
private const val RECENT_LIMIT = 10
private const val MAX_POINTS_PER_ROUTE = 120
private const val MAX_EVENT_PINS = 250
private const val MAX_SPEEDING_SEGMENTS = 400
