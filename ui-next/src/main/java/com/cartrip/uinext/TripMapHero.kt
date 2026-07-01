package com.cartrip.uinext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cartrip.engine.api.RoutePoint
import com.google.android.gms.maps.CameraUpdateFactory
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
 * The map hero for the :ui-next trip detail — a Google map with the trip's route polyline and start/end
 * markers, framed to the route once the map is loaded. Renders nothing for a routeless trip (walk / purged
 * raw data). The Maps API key comes from the host :app's merged manifest. Engine access is the engine-API
 * [RoutePoint] only (boundary-clean); the map SDK imports are allowed.
 */
@Composable
internal fun TripMapHero(route: List<RoutePoint>, modifier: Modifier = Modifier) {
    if (route.size < 2) return
    val latLngs = remember(route) { route.map { LatLng(it.lat, it.lon) } }
    val bounds = remember(latLngs) {
        val b = LatLngBounds.builder()
        latLngs.forEach(b::include)
        b.build()
    }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLngs.first(), 12f)
    }
    // Frame the whole route, but only once the map is measured (newLatLngBounds throws before layout).
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded, bounds) {
        if (mapLoaded) runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_PADDING_PX)) }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        onMapLoaded = { mapLoaded = true },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
    ) {
        Polyline(points = latLngs, color = RouteBlue, width = 12f, jointType = JointType.ROUND)
        Marker(state = MarkerState(latLngs.first()), title = "Start")
        Marker(state = MarkerState(latLngs.last()), title = "End")
    }
}

/** Google Maps' driving-route blue. */
private val RouteBlue = Color(0xFF4285F4)
private const val ROUTE_PADDING_PX = 120
