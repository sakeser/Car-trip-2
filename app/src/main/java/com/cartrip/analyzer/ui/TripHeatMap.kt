package com.cartrip.analyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun TripHeatMap(
    points: List<AnalysisPointEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!GoogleMapConfig.hasApiKey(context)) {
        MapUnavailable(modifier)
        return
    }

    val mapId = GoogleMapConfig.mapId(context)
    val groups = remember(points) {
        points.groupBy { it.tripId }
            .values
            .filter { it.size >= 2 }
            .map { tripPoints -> tripPoints.map { LatLng(it.lat, it.lon) } }
    }
    val bounds = remember(groups) { boundsFor(groups.flatten()) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(TORONTO, 10f)
    }

    LaunchedEffect(bounds) {
        bounds?.let {
            runCatching {
                camera.animate(CameraUpdateFactory.newLatLngBounds(it, 42))
            }
        }
    }

    val satellite = UiPrefs.rememberSatelliteMap(context)
    Box(modifier = modifier) {
    GoogleMap(
        modifier = Modifier.matchParentSize(),
        cameraPositionState = camera,
        properties = MapProperties(mapType = mapTypeFor(satellite)),
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
        groups.forEach { route ->
            Polyline(
                points = route,
                color = Color(0x54EF4444),
                width = 22f,
                jointType = JointType.ROUND
            )
            Polyline(
                points = route,
                color = Color(0xB00EA5E9),
                width = 5f,
                jointType = JointType.ROUND
            )
        }
    }
        MapTypeToggle(
            satellite = satellite,
            onToggle = { UiPrefs.setSatelliteMap(context, !satellite) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
    }
}

fun boundsFor(points: List<LatLng>): LatLngBounds? {
    if (points.isEmpty()) return null
    val builder = LatLngBounds.builder()
    points.forEach(builder::include)
    return builder.build()
}

internal val TORONTO = LatLng(43.7100, -79.3900)
