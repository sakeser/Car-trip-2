package com.cartrip.analyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/**
 * Map of cross-trip recurring-event hotspots (Rev BS): each is a labelled pin coloured by kind (braking,
 * acceleration, sharp turn, hard stop). Tapping a pin opens a detail sheet listing every occurrence — which
 * drive, when, and how hard (g-force) — and you can jump into a trip. Rough spots are no longer shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleSpotsMap(
    hotspots: List<EventHotspots.Hotspot>,
    onOpenTrip: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!GoogleMapConfig.hasApiKey(context)) {
        MapUnavailable(modifier)
        return
    }
    val mapId = GoogleMapConfig.mapId(context)
    val pts = remember(hotspots) { hotspots.map { LatLng(it.lat, it.lon) } }
    val bounds = remember(pts) { boundsFor(pts) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(TORONTO, 10f)
    }
    LaunchedEffect(bounds) {
        bounds?.let { runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(it, 56)) } }
    }
    var selected by remember { mutableStateOf<EventHotspots.Hotspot?>(null) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false
        ),
        googleMapOptionsFactory = { GoogleMapOptions().apply { mapId?.let(::mapId) } }
    ) {
        hotspots.forEach { h ->
            Marker(
                state = MarkerState(LatLng(h.lat, h.lon)),
                title = if (h.where.isNotEmpty()) "${h.kind} · ${h.where}" else h.kind,
                snippet = "on ${h.trips} drives — tap for details",
                icon = BitmapDescriptorFactory.defaultMarker(hueFor(h.kind)),
                onClick = { selected = h; true }
            )
        }
    }

    selected?.let { h ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            HotspotDetail(h, onOpenTrip = { tripId -> selected = null; onOpenTrip(tripId) })
        }
    }
}

@Composable
private fun HotspotDetail(h: EventHotspots.Hotspot, onOpenTrip: (Long) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (h.where.isNotEmpty()) "${h.kind} · ${h.where}" else h.kind,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${h.count} times across ${h.trips} drives" +
                (h.instances.maxOfOrNull { it.gForce }?.let { String.format(Locale.US, " · peak %.2fg", it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Occurrences (tap to open the drive)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        h.instances.forEach { inst ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrip(inst.tripId) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (inst.tripStartWall > 0) Format.dateTime(inst.tripStartWall) else "Drive ${inst.tripId}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    String.format(Locale.US, "%.2fg", inst.gForce),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun hueFor(kind: String): Float = when (kind) {
    "Hard braking" -> BitmapDescriptorFactory.HUE_RED
    "Hard acceleration" -> BitmapDescriptorFactory.HUE_AZURE
    "Sharp turn" -> BitmapDescriptorFactory.HUE_VIOLET
    "Hard stop" -> BitmapDescriptorFactory.HUE_ROSE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
