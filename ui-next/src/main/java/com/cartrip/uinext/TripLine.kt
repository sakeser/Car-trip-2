package com.cartrip.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartrip.engine.api.TripEvent
import com.cartrip.engine.api.TripEventKind
import com.cartrip.engine.api.TripTrackPoint
import kotlin.math.max

/**
 * The **Trip Line** — a compact speed-vs-time story for a trip: the speed curve (filled), the OSM posted-limit
 * as a dashed reference where known, and hard brake/accel/corner/road events marked as ticks along the top.
 * This is the signature depth of the :ui-next trip detail (it reads the same x-axis, seconds-from-start, that
 * the engine-api [TripTrackPoint]/[TripEvent] expose). Colours are a :ui-next presentation concern; the data is
 * engine-domain. ASCII-only source (this Windows build mojibakes non-ASCII literals in BOM-less .kt files).
 *
 * Renders nothing for a track with fewer than 2 samples (the caller usually guards too).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripLine(
    track: List<TripTrackPoint>,
    events: List<TripEvent>,
    modifier: Modifier = Modifier,
) {
    if (track.size < 2) return

    val maxOffset = max(1, track.last().offsetSeconds - track.first().offsetSeconds)
    val originSec = track.first().offsetSeconds
    val peakSpeed = track.maxOf { it.speedKmh }
    val peakLimit = track.mapNotNull { it.speedLimitKmh }.maxOrNull() ?: 0.0
    // Y scale: headroom above the peak of speed/limit, with a sane floor so a slow crawl still fills the panel.
    val yMax = max(SPEED_FLOOR_KMH, max(peakSpeed, peakLimit) * Y_HEADROOM)

    val lineColor = MaterialTheme.colorScheme.primary
    val limitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillTop = lineColor.copy(alpha = 0.22f)
    val fillBottom = lineColor.copy(alpha = 0.02f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TRIP LINE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "peak ${peakSpeed.toInt()} km/h",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            val topPad = 14f            // room for the event ticks along the top
            val plotH = size.height - topPad
            val w = size.width

            fun x(offsetSec: Int): Float = w * (offsetSec - originSec) / maxOffset
            fun y(speed: Double): Float = topPad + (plotH - (speed / yMax * plotH)).toFloat()

            // Filled speed area.
            val area = Path().apply {
                moveTo(x(track.first().offsetSeconds), topPad + plotH)
                track.forEach { lineTo(x(it.offsetSeconds), y(it.speedKmh)) }
                lineTo(x(track.last().offsetSeconds), topPad + plotH)
                close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(fillTop, fillBottom), startY = topPad, endY = topPad + plotH))

            // Speed line.
            val speedPath = Path().apply {
                moveTo(x(track.first().offsetSeconds), y(track.first().speedKmh))
                track.drop(1).forEach { lineTo(x(it.offsetSeconds), y(it.speedKmh)) }
            }
            drawPath(speedPath, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Posted-limit reference (dashed), only across adjacent samples that both have a known limit.
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            for (i in 1 until track.size) {
                val a = track[i - 1].speedLimitKmh
                val b = track[i].speedLimitKmh
                if (a != null && b != null) {
                    drawLine(
                        color = limitColor.copy(alpha = 0.7f),
                        start = Offset(x(track[i - 1].offsetSeconds), y(a)),
                        end = Offset(x(track[i].offsetSeconds), y(b)),
                        strokeWidth = 2f,
                        pathEffect = dash,
                    )
                }
            }

            // Event ticks: a faint full-height guide + a coloured dot at the top.
            events.forEach { e ->
                val ex = x(e.offsetSeconds).coerceIn(0f, w)
                val color = eventColor(e.kind)
                drawLine(
                    color = color.copy(alpha = 0.28f),
                    start = Offset(ex, topPad),
                    end = Offset(ex, topPad + plotH),
                    strokeWidth = 1.5f,
                )
                drawCircle(color = color, radius = 4f, center = Offset(ex, topPad - 2f))
            }
        }

        // Legend: speed + limit always; event kinds only when they occurred on this trip.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendSwatch(lineColor, "Speed")
            LegendSwatch(limitColor, "Limit", dashed = true)
            val kinds = events.map { it.kind }.toSet()
            if (TripEventKind.HARD_BRAKE in kinds) LegendSwatch(eventColor(TripEventKind.HARD_BRAKE), "Hard brake")
            if (TripEventKind.HARD_ACCEL in kinds) LegendSwatch(eventColor(TripEventKind.HARD_ACCEL), "Hard accel")
            if (TripEventKind.HARD_CORNER in kinds) LegendSwatch(eventColor(TripEventKind.HARD_CORNER), "Hard corner")
            if (TripEventKind.ROUGH_ROAD in kinds) LegendSwatch(eventColor(TripEventKind.ROUGH_ROAD), "Rough road")
            if (TripEventKind.OTHER in kinds) LegendSwatch(eventColor(TripEventKind.OTHER), "Event")
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A dashed reference reads as a thin bar; a series/event reads as a dot.
        Box(
            modifier = Modifier
                .size(width = if (dashed) 12.dp else 8.dp, height = if (dashed) 3.dp else 8.dp)
                .clip(if (dashed) androidx.compose.foundation.shape.RoundedCornerShape(2.dp) else CircleShape)
                .background(color.copy(alpha = if (dashed) 0.7f else 1f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** :ui-next presentation palette for event kinds (matches the ScoreChip family: red brake / amber accel). */
private fun eventColor(kind: TripEventKind): Color = when (kind) {
    TripEventKind.HARD_BRAKE -> Color(0xFFEF4444)
    TripEventKind.HARD_ACCEL -> Color(0xFFF59E0B)
    TripEventKind.HARD_CORNER -> Color(0xFF6366F1)
    TripEventKind.ROUGH_ROAD -> Color(0xFF9CA3AF)
    TripEventKind.OTHER -> Color(0xFF9CA3AF)
}

private const val SPEED_FLOOR_KMH = 30.0
private const val Y_HEADROOM = 1.15
