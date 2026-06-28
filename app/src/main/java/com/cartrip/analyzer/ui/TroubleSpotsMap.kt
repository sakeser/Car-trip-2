package com.cartrip.analyzer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map of cross-trip trouble spots (Rev BO): every individual **rough spot** as a small translucent circle
 * (so clusters of nearby potholes read as denser blobs — you can see how close they really are), and each
 * **recurring hotspot** as a labelled pin coloured by kind. Mirrors [TripHeatMap]'s embedded-map setup.
 */
@Composable
fun TroubleSpotsMap(
    hotspots: List<EventHotspots.Hotspot>,
    roughSpots: List<LatLng>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!GoogleMapConfig.hasApiKey(context)) {
        MapUnavailable(modifier)
        return
    }
    val mapId = GoogleMapConfig.mapId(context)
    val hotspotPts = remember(hotspots) { hotspots.map { LatLng(it.lat, it.lon) } }
    val bounds = remember(hotspotPts, roughSpots) { boundsFor(hotspotPts + roughSpots) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(TORONTO, 10f)
    }
    LaunchedEffect(bounds) {
        bounds?.let { runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(it, 48)) } }
    }

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
        // Rough spots: translucent circles so overlapping ones visibly darken (density = a rough stretch).
        roughSpots.forEach { p ->
            Circle(
                center = p,
                radius = 18.0,
                fillColor = Color(0x33F59E0B),
                strokeColor = Color(0x88F59E0B),
                strokeWidth = 1.5f
            )
        }
        // Recurring hotspots: labelled pins, coloured by kind.
        hotspots.forEach { h ->
            Marker(
                state = MarkerState(LatLng(h.lat, h.lon)),
                title = if (h.where.isNotEmpty()) "${h.kind} · ${h.where}" else h.kind,
                snippet = "on ${h.trips} drives",
                icon = BitmapDescriptorFactory.defaultMarker(hueFor(h.kind))
            )
        }
    }
}

private fun hueFor(kind: String): Float = when (kind) {
    "Hard braking" -> BitmapDescriptorFactory.HUE_RED
    "Hard acceleration" -> BitmapDescriptorFactory.HUE_AZURE
    "Sharp turn" -> BitmapDescriptorFactory.HUE_VIOLET
    "Hard stop" -> BitmapDescriptorFactory.HUE_ROSE
    "Rough spot" -> BitmapDescriptorFactory.HUE_ORANGE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
