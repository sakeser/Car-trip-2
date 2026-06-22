package com.cartrip.analyzer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun TripMap(
    points: List<TrackPoint>,
    events: List<DriveEvent>,
    selectedPoint: TrackPoint? = null,
    focusKey: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!GoogleMapConfig.hasApiKey(context)) {
        MapUnavailable(modifier)
        return
    }

    val mapId = GoogleMapConfig.mapId(context)
    val route = remember(points) { points.map { LatLng(it.lat, it.lon) } }
    val bounds = remember(route) { boundsFor(route) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(TORONTO, 11f)
    }

    LaunchedEffect(bounds) {
        bounds?.let {
            runCatching {
                camera.animate(CameraUpdateFactory.newLatLngBounds(it, 56))
            }
        }
    }

    // Zoom in on the selected event/point when the user taps an event (focusKey changes).
    LaunchedEffect(focusKey) {
        if (focusKey > 0) {
            selectedPoint?.let { p ->
                runCatching {
                    camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 15f))
                }
            }
        }
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
        googleMapOptionsFactory = {
            GoogleMapOptions().apply {
                mapId?.let(::mapId)
            }
        }
    ) {
        if (route.size >= 2) {
            Polyline(
                points = route,
                color = Color(0xFF0EA5E9),
                width = 9f,
                jointType = JointType.ROUND
            )

            Marker(
                state = MarkerState(route.first()),
                title = "Start",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
            Marker(
                state = MarkerState(route.last()),
                title = "End",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )

            val byTime = remember(points) { points.associateBy { it.tMs } }
            events.forEach { event ->
                val point = byTime[event.tMs] ?: return@forEach
                Marker(
                    state = MarkerState(LatLng(point.lat, point.lon)),
                    title = event.title(),
                    icon = BitmapDescriptorFactory.defaultMarker(event.markerHue())
                )
            }

            selectedPoint?.let { point ->
                Marker(
                    state = MarkerState(LatLng(point.lat, point.lon)),
                    title = "Replay ${"%.0f".format(point.speedKmh)} km/h",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }
    }
}

private fun DriveEvent.title(): String =
    when (type) {
        EventType.BRAKE -> "Hard brake ${"%.1f".format(magnitude)} m/s2"
        EventType.ACCEL -> "Hard accel ${"%.1f".format(magnitude)} m/s2"
        EventType.CORNER -> "Hard corner ${"%.1f".format(magnitude)} m/s2"
    }

private fun DriveEvent.markerHue(): Float =
    when (type) {
        EventType.BRAKE -> BitmapDescriptorFactory.HUE_RED
        EventType.ACCEL -> BitmapDescriptorFactory.HUE_ORANGE
        EventType.CORNER -> BitmapDescriptorFactory.HUE_YELLOW
    }
