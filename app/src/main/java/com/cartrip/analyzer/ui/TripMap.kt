package com.cartrip.analyzer.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
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
import com.google.android.gms.maps.model.BitmapDescriptor
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
    onEventClick: (DriveEvent) -> Unit = {},
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
    // Runs of consecutive points where speed exceeded the matched limit (+ noise tolerance).
    val speedingSegments = remember(points) {
        val segs = ArrayList<List<LatLng>>()
        var cur = ArrayList<LatLng>()
        for (i in points.indices) {
            val p = points[i]
            val over = p.speedLimitKmh > 0.0 && p.speedKmh > p.speedLimitKmh + 3.0
            if (over) {
                if (cur.isEmpty() && i > 0) cur.add(LatLng(points[i - 1].lat, points[i - 1].lon))
                cur.add(LatLng(p.lat, p.lon))
            } else {
                if (cur.size >= 2) segs.add(cur)
                cur = ArrayList()
            }
        }
        if (cur.size >= 2) segs.add(cur)
        segs
    }
    val startIcon = remember { markerIcon(MarkerGlyph.START, AndroidColor.rgb(34, 197, 94)) }
    val stopIcon = remember { markerIcon(MarkerGlyph.STOP, AndroidColor.rgb(239, 68, 68)) }
    val brakeIcon = remember { markerIcon(MarkerGlyph.BRAKE, AndroidColor.rgb(220, 38, 38)) }
    val accelIcon = remember { markerIcon(MarkerGlyph.ACCEL, AndroidColor.rgb(245, 158, 11)) }
    val cornerIcon = remember { markerIcon(MarkerGlyph.TURN, AndroidColor.rgb(234, 179, 8)) }
    val swerveIcon = remember { markerIcon(MarkerGlyph.SWERVE, AndroidColor.rgb(147, 51, 234)) }
    val potholeIcon = remember { markerIcon(MarkerGlyph.BUMP, AndroidColor.rgb(120, 113, 108)) }
    val replayIcon = remember { markerIcon(MarkerGlyph.REPLAY, AndroidColor.rgb(14, 165, 233)) }
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

            // Red overlay where the driver was over the posted limit.
            speedingSegments.forEach { seg ->
                Polyline(
                    points = seg,
                    color = Color(0xFFEF4444),
                    width = 12f,
                    jointType = JointType.ROUND
                )
            }

            Marker(
                state = MarkerState(route.first()),
                title = "Start",
                icon = startIcon
            )
            Marker(
                state = MarkerState(route.last()),
                title = "Stop",
                icon = stopIcon
            )

            // Place each event at the nearest route point by time. analysis points are downsampled
            // (≥1s gaps) and pothole events come from the 50 Hz motion clock, so an exact timestamp
            // match would silently drop most markers. Only skip if the nearest point is absurdly far.
            events.forEach { event ->
                val point = points.minByOrNull { kotlin.math.abs(it.tMs - event.tMs) } ?: return@forEach
                if (kotlin.math.abs(point.tMs - event.tMs) > 15_000L) return@forEach
                Marker(
                    state = MarkerState(LatLng(point.lat, point.lon)),
                    title = event.title(),
                    icon = when (event.type) {
                        EventType.BRAKE -> brakeIcon
                        EventType.ACCEL -> accelIcon
                        EventType.CORNER -> cornerIcon
                        EventType.SWERVE -> swerveIcon
                        EventType.POTHOLE -> potholeIcon
                    },
                    onClick = {
                        onEventClick(event)
                        true
                    }
                )
            }

            selectedPoint?.let { point ->
                Marker(
                    state = MarkerState(LatLng(point.lat, point.lon)),
                    title = "Replay ${"%.0f".format(point.speedKmh)} km/h",
                    icon = replayIcon
                )
            }
        }
    }
}

private fun DriveEvent.title(): String =
    when (type) {
        EventType.BRAKE -> "Hard brake ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.ACCEL -> "Hard accel ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.CORNER -> "Hard corner ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.SWERVE -> "Swerve"
        EventType.POTHOLE -> "Pothole ${"%.2fg".format(magnitude / 9.80665)}"
    }

private enum class MarkerGlyph { START, STOP, BRAKE, ACCEL, TURN, SWERVE, BUMP, REPLAY }

private fun markerIcon(glyph: MarkerGlyph, fill: Int): BitmapDescriptor {
    val size = 96
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = AndroidColor.argb(70, 0, 0, 0)
    when (glyph) {
        MarkerGlyph.BRAKE -> canvas.drawPath(octagonPath(center, center + 4f, 32f), paint)
        MarkerGlyph.TURN, MarkerGlyph.SWERVE, MarkerGlyph.BUMP -> canvas.drawPath(diamondPath(center, center + 4f, 31f), paint)
        else -> canvas.drawCircle(center, center + 4f, 31f, paint)
    }

    paint.color = fill
    when (glyph) {
        MarkerGlyph.BRAKE -> canvas.drawPath(octagonPath(center, center, 32f), paint)
        MarkerGlyph.TURN, MarkerGlyph.SWERVE, MarkerGlyph.BUMP -> canvas.drawPath(diamondPath(center, center, 31f), paint)
        else -> canvas.drawCircle(center, center, 31f, paint)
    }

    paint.color = AndroidColor.WHITE
    when (glyph) {
        MarkerGlyph.START -> {
            val play = Path().apply {
                moveTo(center - 9f, center - 15f)
                lineTo(center - 9f, center + 15f)
                lineTo(center + 16f, center)
                close()
            }
            canvas.drawPath(play, paint)
        }
        MarkerGlyph.STOP -> {
            canvas.drawRoundRect(
                RectF(center - 14f, center - 14f, center + 14f, center + 14f),
                5f,
                5f,
                paint
            )
        }
        MarkerGlyph.BRAKE, MarkerGlyph.ACCEL, MarkerGlyph.TURN, MarkerGlyph.SWERVE, MarkerGlyph.BUMP -> {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = if (glyph == MarkerGlyph.SWERVE) 30f else 36f
            val baseline = center - (paint.descent() + paint.ascent()) / 2f - 1f
            canvas.drawText(
                when (glyph) {
                    MarkerGlyph.BRAKE -> "B"
                    MarkerGlyph.ACCEL -> "A"
                    MarkerGlyph.TURN -> "T"
                    MarkerGlyph.SWERVE -> "SW"
                    MarkerGlyph.BUMP -> "!"
                    else -> ""
                },
                center,
                baseline,
                paint
            )
        }
        MarkerGlyph.REPLAY -> {
            canvas.drawCircle(center, center, 13f, paint)
        }
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun diamondPath(cx: Float, cy: Float, radius: Float): Path =
    Path().apply {
        moveTo(cx, cy - radius)
        lineTo(cx + radius, cy)
        lineTo(cx, cy + radius)
        lineTo(cx - radius, cy)
        close()
    }

private fun octagonPath(cx: Float, cy: Float, radius: Float): Path {
    val k = radius * 0.42f
    return Path().apply {
        moveTo(cx - k, cy - radius)
        lineTo(cx + k, cy - radius)
        lineTo(cx + radius, cy - k)
        lineTo(cx + radius, cy + k)
        lineTo(cx + k, cy + radius)
        lineTo(cx - k, cy + radius)
        lineTo(cx - radius, cy + k)
        lineTo(cx - radius, cy - k)
        close()
    }
}
