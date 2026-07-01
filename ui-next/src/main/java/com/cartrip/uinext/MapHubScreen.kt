package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.RoutePoint
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map tab (spec's 4th tab) — a **Map Hub** roughing in the spatial-intelligence surface. Real, read-only data:
 * it overlays the routes of the recent drives (via the engine-api `getRoute`), framed to fit. Placeholder layer
 * chips show where a route heatmap + trouble-spots layer will go (those need new read gateways; disabled for
 * now). Routes are downsampled for a light overview. ASCII source (Cp1252 trap).
 */
@Composable
internal fun MapHubScreen(trips: List<TripSummary>?, onOpenTrip: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val recentIds = remember(trips) { trips.orEmpty().filter { it.isDrive }.take(RECENT_LIMIT).map { it.id } }
    // Keep each route paired with its trip id so a tap can open that trip.
    val routes by produceState<List<Pair<Long, List<LatLng>>>?>(initialValue = null, recentIds) {
        value = recentIds.mapNotNull { id ->
            val r = repo.getRoute(id)
            if (r.size < 2) null else id to r.downsample(MAX_POINTS_PER_ROUTE).map { p -> LatLng(p.lat, p.lon) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = true, onClick = {}, label = { Text("Routes") })
            FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Trouble spots") })
            FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Heatmap") })
        }
        Text(
            when (val r = routes) {
                null -> "Loading recent routes..."
                else -> "${r.size} recent ${if (r.size == 1) "route" else "routes"} $MIDDOT tap a route to open it"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
                else -> RouteOverlayMap(r, onOpenTrip)
            }
        }
    }
}

@Composable
private fun RouteOverlayMap(routes: List<Pair<Long, List<LatLng>>>, onOpenTrip: (Long) -> Unit) {
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
        routes.forEach { (id, line) ->
            Polyline(
                points = line,
                color = RouteBlue,
                width = 8f,
                jointType = JointType.ROUND,
                clickable = true,
                tag = id,
                onClick = { poly -> (poly.tag as? Long)?.let(onOpenTrip) },
            )
        }
    }
}

/** Keep at most [max] evenly-spaced points (endpoints preserved) so the hub stays light with many routes. */
private fun List<RoutePoint>.downsample(max: Int): List<RoutePoint> {
    if (size <= max) return this
    val step = size / max
    return filterIndexed { i, _ -> i % step == 0 || i == lastIndex }
}

private val RouteBlue = Color(0xFF4285F4)
private const val RECENT_LIMIT = 10
private const val MAX_POINTS_PER_ROUTE = 120
