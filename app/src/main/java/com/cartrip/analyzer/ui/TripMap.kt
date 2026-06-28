package com.cartrip.analyzer.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.SpeedTier
import com.cartrip.analyzer.analysis.TrackPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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
    resetKey: Int = 0,
    replayFollow: Boolean = false,
    onEventClick: (DriveEvent) -> Unit = {},
    onStartOpen: () -> Unit = {},
    onStopOpen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!GoogleMapConfig.hasApiKey(context)) {
        MapUnavailable(modifier)
        return
    }

    val mapId = GoogleMapConfig.mapId(context)
    val route = remember(points) { points.map { LatLng(it.lat, it.lon) } }
    val bounds = remember(route) { boundsFor(route)?.let { relaxedBounds(it) } }
    // Runs of consecutive over-limit points, tiered: yellow for 0-10 km/h over, red for 10+ over.
    val speedingSegments = remember(points) {
        val segs = ArrayList<Pair<SpeedTier.Tier, List<LatLng>>>()
        var curTier = SpeedTier.Tier.NONE
        var cur = ArrayList<LatLng>()
        fun flush() {
            if (cur.size >= 2 && curTier != SpeedTier.Tier.NONE) segs.add(curTier to cur)
            cur = ArrayList()
        }
        for (i in points.indices) {
            val p = points[i]
            val tier = SpeedTier.of(p.speedKmh, p.speedLimitKmh)
            if (tier != curTier) {
                flush()
                curTier = tier
                if (tier != SpeedTier.Tier.NONE && i > 0) cur.add(LatLng(points[i - 1].lat, points[i - 1].lon))
            }
            if (tier != SpeedTier.Tier.NONE) cur.add(LatLng(p.lat, p.lon))
        }
        flush()
        segs
    }
    // Start/end are smaller (72) and drawn semi-transparent (alpha below) so the route detail shows
    // through; the "you" car stays prominent and on top. Brake & turn markers are ~30% smaller again.
    val startIcon = remember { markerIcon(MarkerGlyph.START, AndroidColor.rgb(34, 197, 94), 72) }
    val stopIcon = remember { markerIcon(MarkerGlyph.FINISH, AndroidColor.rgb(239, 68, 68), 72) }
    val brakeIcon = remember { markerIcon(MarkerGlyph.BRAKE, AndroidColor.rgb(220, 38, 38), 67) }
    val accelIcon = remember { markerIcon(MarkerGlyph.ACCEL, AndroidColor.rgb(245, 158, 11), 84) }
    val cornerIcon = remember { markerIcon(MarkerGlyph.TURN, AndroidColor.rgb(234, 179, 8), 67) }
    val swerveIcon = remember { markerIcon(MarkerGlyph.SWERVE, AndroidColor.rgb(147, 51, 234), 67) }
    val potholeIcon = remember { markerIcon(MarkerGlyph.BUMP, AndroidColor.rgb(245, 158, 11), 84) }
    val harshStopIcon = remember { markerIcon(MarkerGlyph.HARSH_STOP, AndroidColor.rgb(219, 39, 119), 72) }
    // The "you" replay marker glyph is user-selectable (Options -> Your trip icon). Read it reactively
    // so changing the pref actually updates the marker (a no-key remember would cache the first value).
    val youGlyph = when (UiPrefs.rememberYouIcon(context)) {
        UiPrefs.YouIcon.CAR -> MarkerGlyph.CAR
        UiPrefs.YouIcon.ARROW -> MarkerGlyph.ARROW
        UiPrefs.YouIcon.PERSON -> MarkerGlyph.PERSON
        UiPrefs.YouIcon.DOT -> MarkerGlyph.DOT
    }
    val replayIcon = remember(youGlyph) { markerIcon(youGlyph, AndroidColor.rgb(14, 165, 233), 92) }
    // Start/end markers act like the bottom-left chips: first tap shows the label, a second tap opens
    // Google Maps. Tapping the map clears the "armed" state so the next tap shows the label again.
    var armedMarker by remember(points) { mutableStateOf("") }
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

    // Pressing Play resets the camera to the whole route, undoing any manual pan/zoom or event-zoom.
    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            bounds?.let { runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(it, 56)) } }
        }
    }

    // Dynamic replay camera: while the replay is playing, follow the car and set the zoom from its speed
    // (zoomed out on fast highway stretches, zoomed in for the slow last mile). Sampled a few times a
    // second rather than per frame so the camera glides instead of thrashing.
    val followingPoint by rememberUpdatedState(selectedPoint)
    LaunchedEffect(replayFollow) {
        if (!replayFollow) return@LaunchedEffect
        while (isActive) {
            followingPoint?.let { p ->
                runCatching {
                    camera.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), replayZoom(p.speedKmh)),
                        300
                    )
                }
            }
            delay(300)
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
        onMapClick = { armedMarker = "" },
        googleMapOptionsFactory = {
            GoogleMapOptions().apply {
                mapId?.let(::mapId)
            }
        }
    ) {
        if (route.size >= 2) {
            Polyline(
                points = route,
                color = GoogleRouteBlue,
                width = 10f,
                jointType = JointType.ROUND
            )

            // Over-limit overlay: yellow for 0-10 km/h over, red for 10+ over.
            speedingSegments.forEach { (tier, seg) ->
                Polyline(
                    points = seg,
                    color = if (tier == SpeedTier.Tier.RED) Color(0xFFEF4444) else Color(0xFFF59E0B),
                    width = 12f,
                    jointType = JointType.ROUND
                )
            }

            Marker(
                state = MarkerState(route.first()),
                title = "Start",
                snippet = "Tap again to open in Maps",
                icon = startIcon,
                alpha = 0.7f,
                onClick = {
                    if (armedMarker == "start") { armedMarker = ""; onStartOpen(); true }
                    else { armedMarker = "start"; false }
                },
                onInfoWindowClick = { onStartOpen() }
            )
            Marker(
                state = MarkerState(route.last()),
                title = "Stop",
                snippet = "Tap again to open in Maps",
                icon = stopIcon,
                alpha = 0.7f,
                onClick = {
                    if (armedMarker == "stop") { armedMarker = ""; onStopOpen(); true }
                    else { armedMarker = "stop"; false }
                },
                onInfoWindowClick = { onStopOpen() }
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
                        EventType.HARSH_STOP -> harshStopIcon
                    },
                    onClick = {
                        onEventClick(event)
                        true
                    }
                )
            }

            selectedPoint?.let { point ->
                // The replay car rides above the route and every other marker (start/end/events).
                Marker(
                    state = MarkerState(LatLng(point.lat, point.lon)),
                    title = "Replay ${"%.0f".format(point.speedKmh)} km/h",
                    icon = replayIcon,
                    zIndex = 10f
                )
            }
        }
    }
        MapTypeToggle(
            satellite = satellite,
            onToggle = { UiPrefs.setSatelliteMap(context, !satellite) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
    }
}

private fun DriveEvent.title(): String =
    when (type) {
        EventType.BRAKE -> "Hard brake ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.ACCEL -> "Hard accel ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.CORNER -> "Hard corner ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.SWERVE -> "Swerve"
        EventType.POTHOLE -> "Pothole ${"%.2fg".format(magnitude / 9.80665)}"
        EventType.HARSH_STOP -> "Harsh stop ${"%.2fg".format(magnitude / 9.80665)}"
    }

private fun relaxedBounds(bounds: LatLngBounds, visibleRouteFraction: Double = 0.66): LatLngBounds {
    val latSpan = (bounds.northeast.latitude - bounds.southwest.latitude).coerceAtLeast(0.002)
    val lonSpan = (bounds.northeast.longitude - bounds.southwest.longitude).coerceAtLeast(0.002)
    val expand = ((1.0 / visibleRouteFraction.coerceIn(0.1, 1.0)) - 1.0) / 2.0
    val latPad = latSpan * expand
    val lonPad = lonSpan * expand
    return LatLngBounds(
        LatLng((bounds.southwest.latitude - latPad).coerceAtLeast(-85.0), bounds.southwest.longitude - lonPad),
        LatLng((bounds.northeast.latitude + latPad).coerceAtMost(85.0), bounds.northeast.longitude + lonPad)
    )
}

/**
 * Replay camera zoom from the car's current speed: zoom in for the slow last mile (~16.5), zoom out on
 * fast highway stretches (~13.5), linearly interpolated between 20 and 100 km/h.
 */
private fun replayZoom(speedKmh: Double): Float = when {
    speedKmh <= 20.0 -> 16.5f
    speedKmh >= 100.0 -> 13.5f
    else -> 16.5f - ((speedKmh - 20.0) / 80.0).toFloat() * 3.0f
}

/** The route line colour Google Maps uses for a driving route (Google blue), shared by all maps. */
internal val GoogleRouteBlue = Color(0xFF4285F4)

internal enum class MarkerGlyph { START, FINISH, BRAKE, ACCEL, TURN, SWERVE, BUMP, HARSH_STOP, CAR, ARROW, PERSON, DOT }

/**
 * Draws a map marker as a [size]px bitmap: a coloured pin shape (octagon for the stop-sign brake,
 * diamond for the warning-style turn/swerve/bump, circle otherwise) under a white symbolic glyph.
 * Symbols are drawn with vector paths (no text), so they read at small sizes and stay ASCII-safe.
 */
internal fun markerIcon(glyph: MarkerGlyph, fill: Int, size: Int = 96): BitmapDescriptor {
    val c = size / 2f
    val r = size * 0.33f
    val shadow = size * 0.045f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawShape(dy: Float) {
        when (glyph) {
            MarkerGlyph.BRAKE, MarkerGlyph.HARSH_STOP -> canvas.drawPath(octagonPath(c, c + dy, r), paint)
            MarkerGlyph.TURN, MarkerGlyph.SWERVE, MarkerGlyph.BUMP -> canvas.drawPath(diamondPath(c, c + dy, r), paint)
            else -> canvas.drawCircle(c, c + dy, r, paint)
        }
    }
    paint.color = AndroidColor.argb(70, 0, 0, 0)
    drawShape(shadow)
    paint.color = fill
    drawShape(0f)

    paint.color = AndroidColor.WHITE
    when (glyph) {
        MarkerGlyph.START -> drawFlag(canvas, c, r, paint, fill, checkered = false)
        MarkerGlyph.FINISH -> drawFlag(canvas, c, r, paint, fill, checkered = true)
        MarkerGlyph.BRAKE -> drawStopRing(canvas, c, r, paint)
        MarkerGlyph.ACCEL -> drawUpChevrons(canvas, c, r, paint)
        MarkerGlyph.TURN -> drawTurnArrow(canvas, c, r, paint)
        MarkerGlyph.SWERVE -> drawSwerve(canvas, c, r, paint)
        MarkerGlyph.BUMP -> drawBump(canvas, c, r, paint)
        MarkerGlyph.HARSH_STOP -> drawHarshStop(canvas, c, r, paint)
        MarkerGlyph.CAR -> drawCar(canvas, c, r, paint, fill)
        MarkerGlyph.ARROW -> drawNavArrow(canvas, c, r, paint)
        MarkerGlyph.PERSON -> drawPerson(canvas, c, r, paint)
        MarkerGlyph.DOT -> { /* a plain coloured dot — the marker circle is the glyph */ }
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** White octagon outline inside the red octagon — reads as a stop sign without any text. */
private fun drawStopRing(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.16f
    canvas.drawPath(octagonPath(c, c, r * 0.66f), paint)
    paint.style = Paint.Style.FILL
}

/** White exclamation inside the octagon — an abrupt, hard stop (distinct from the plain brake ring). */
private fun drawHarshStop(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeWidth = r * 0.22f
    canvas.drawLine(c, c - r * 0.52f, c, c + r * 0.10f, paint)   // the bar
    paint.style = Paint.Style.FILL
    canvas.drawCircle(c, c + r * 0.46f, r * 0.13f, paint)        // the dot
}

/** Start = solid pennant, Finish = checkered flag (its dark squares show the marker [fill] through). */
private fun drawFlag(canvas: Canvas, c: Float, r: Float, paint: Paint, fill: Int, checkered: Boolean) {
    val staffX = c - r * 0.42f
    val top = c - r * 0.62f
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.13f
    paint.strokeCap = Paint.Cap.ROUND
    canvas.drawLine(staffX, top, staffX, c + r * 0.66f, paint)
    paint.style = Paint.Style.FILL
    val bw = r * 0.86f
    val bh = r * 0.58f
    if (!checkered) {
        val pennant = Path().apply {
            moveTo(staffX, top)
            lineTo(staffX + bw, top + bh * 0.5f)
            lineTo(staffX, top + bh)
            close()
        }
        canvas.drawPath(pennant, paint)
    } else {
        val cols = 3; val rows = 2
        val cw = bw / cols; val ch = bh / rows
        for (gx in 0 until cols) for (gy in 0 until rows) {
            paint.color = if ((gx + gy) % 2 == 0) AndroidColor.WHITE else fill
            canvas.drawRect(staffX + gx * cw, top + gy * ch, staffX + (gx + 1) * cw, top + (gy + 1) * ch, paint)
        }
        paint.color = AndroidColor.WHITE
    }
}

/** Two stacked up-chevrons — "fast / accelerating". */
private fun drawUpChevrons(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.16f
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    fun chevron(dy: Float) {
        val p = Path().apply {
            moveTo(c - r * 0.45f, c + dy + r * 0.12f)
            lineTo(c, c + dy - r * 0.28f)
            lineTo(c + r * 0.45f, c + dy + r * 0.12f)
        }
        canvas.drawPath(p, paint)
    }
    chevron(-r * 0.18f)
    chevron(r * 0.28f)
    paint.style = Paint.Style.FILL
}

/** Bent arrow (up, then turning right with an arrowhead) — a turn/corner. */
private fun drawTurnArrow(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.15f
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    val shaft = Path().apply {
        moveTo(c - r * 0.12f, c + r * 0.55f)
        lineTo(c - r * 0.12f, c - r * 0.05f)
        quadTo(c - r * 0.12f, c - r * 0.45f, c + r * 0.3f, c - r * 0.45f)
    }
    canvas.drawPath(shaft, paint)
    paint.style = Paint.Style.FILL
    val ax = c + r * 0.3f; val ay = c - r * 0.45f
    val head = Path().apply {
        moveTo(ax + r * 0.3f, ay)
        lineTo(ax - r * 0.02f, ay - r * 0.24f)
        lineTo(ax - r * 0.02f, ay + r * 0.24f)
        close()
    }
    canvas.drawPath(head, paint)
}

/** An S-curve — a swerve / quick side-to-side. */
private fun drawSwerve(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.15f
    paint.strokeCap = Paint.Cap.ROUND
    val s = Path().apply {
        moveTo(c - r * 0.45f, c + r * 0.5f)
        cubicTo(c + r * 0.45f, c + r * 0.18f, c - r * 0.45f, c - r * 0.18f, c + r * 0.45f, c - r * 0.5f)
    }
    canvas.drawPath(s, paint)
    paint.style = Paint.Style.FILL
}

/** A hump over a baseline — the road "bump" sign. */
private fun drawBump(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = r * 0.14f
    paint.strokeCap = Paint.Cap.ROUND
    canvas.drawLine(c - r * 0.55f, c + r * 0.32f, c + r * 0.55f, c + r * 0.32f, paint)
    canvas.drawArc(RectF(c - r * 0.42f, c - r * 0.1f, c + r * 0.42f, c + r * 0.74f), 180f, 180f, false, paint)
    paint.style = Paint.Style.FILL
}

/** A navigation cursor (kite) pointing up — the "you are here / heading" arrow. */
private fun drawNavArrow(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.FILL
    val p = Path().apply {
        moveTo(c, c - r * 0.6f)
        lineTo(c + r * 0.46f, c + r * 0.52f)
        lineTo(c, c + r * 0.22f)
        lineTo(c - r * 0.46f, c + r * 0.52f)
        close()
    }
    canvas.drawPath(p, paint)
}

/** A simple person silhouette — head + shoulders. */
private fun drawPerson(canvas: Canvas, c: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.FILL
    canvas.drawCircle(c, c - r * 0.34f, r * 0.24f, paint)
    canvas.drawArc(RectF(c - r * 0.46f, c - r * 0.04f, c + r * 0.46f, c + r * 0.92f), 180f, 180f, true, paint)
}

/** Side-view car silhouette (body + cabin), wheels cut out in the marker [fill] colour. */
private fun drawCar(canvas: Canvas, c: Float, r: Float, paint: Paint, fill: Int) {
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(c - r * 0.62f, c - r * 0.04f, c + r * 0.62f, c + r * 0.3f), r * 0.12f, r * 0.12f, paint)
    val cabin = Path().apply {
        moveTo(c - r * 0.34f, c - r * 0.02f)
        lineTo(c - r * 0.18f, c - r * 0.34f)
        lineTo(c + r * 0.2f, c - r * 0.34f)
        lineTo(c + r * 0.36f, c - r * 0.02f)
        close()
    }
    canvas.drawPath(cabin, paint)
    paint.color = fill
    canvas.drawCircle(c - r * 0.32f, c + r * 0.32f, r * 0.15f, paint)
    canvas.drawCircle(c + r * 0.32f, c + r * 0.32f, r * 0.15f, paint)
    paint.color = AndroidColor.WHITE
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
