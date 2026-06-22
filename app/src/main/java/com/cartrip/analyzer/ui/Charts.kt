package com.cartrip.analyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Lightweight time-series line chart drawn on a Canvas (no third-party chart lib).
 * If [zeroCentered] is true a baseline is drawn at value 0.
 */
@Composable
fun TimeSeriesChart(
    title: String,
    values: List<Float>,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    zeroCentered: Boolean = false,
    selectedIndex: Int? = null
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (values.size < 2) {
            Text(
                "Not enough data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        var minV = values.min()
        var maxV = values.max()
        if (zeroCentered) {
            val a = max(maxV, -minV)
            maxV = a
            minV = -a
        }
        if (maxV - minV < 0.001f) {
            maxV += 1f
            minV -= 1f
        }

        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
        val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
        val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .padding(top = 6.dp)
        ) {
            val w = size.width
            val h = size.height
            fun xAt(i: Int) = w * i / (values.size - 1)
            fun yAt(v: Float) = h - (v - minV) / (maxV - minV) * h

            drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), 1f)
            drawLine(gridColor, Offset(0f, h), Offset(w, h), 1f)

            if (minV < 0f && maxV > 0f) {
                val y0 = yAt(0f)
                drawLine(baselineColor, Offset(0f, y0), Offset(w, y0), 1.5f)
            }

            val path = Path()
            path.moveTo(xAt(0), yAt(values[0]))
            for (i in 1 until values.size) {
                path.lineTo(xAt(i), yAt(values[i]))
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.10f))
            drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = 4.5f, center = Offset(xAt(values.lastIndex), yAt(values.last())))

            selectedIndex?.coerceIn(0, values.size - 1)?.let { selected ->
                val x = xAt(selected)
                drawLine(
                    color = cursorColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 2f
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                text = "min ${"%.1f".format(values.min())} $unit    max ${"%.1f".format(values.max())} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One named series for [MultiSeriesChart]. */
class Series(val label: String, val color: Color, val values: List<Float>)

/** A tiny inline line, no axes - for small-multiples and hero cards. */
@Composable
fun MiniSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    zeroBaseline: Boolean = false
) {
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(34.dp))
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().height(34.dp)) {
        var mn = values.min()
        var mx = values.max()
        if (zeroBaseline) {
            val a = max(mx, -mn)
            mx = a
            mn = -a
        }
        if (mx - mn < 0.001f) {
            mx += 1f
            mn -= 1f
        }
        val w = size.width
        val h = size.height
        fun xAt(i: Int) = w * i / (values.size - 1)
        fun yAt(v: Float) = h - (v - mn) / (mx - mn) * h
        if (zeroBaseline && mn < 0f && mx > 0f) {
            val y0 = yAt(0f)
            drawLine(baselineColor, Offset(0f, y0), Offset(w, y0), 1f)
        }
        val path = Path()
        path.moveTo(xAt(0), yAt(values[0]))
        for (i in 1 until values.size) path.lineTo(xAt(i), yAt(values[i]))
        val fill = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.11f))
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.6f, center = Offset(xAt(values.lastIndex), yAt(values.last())))
    }
}

/**
 * Overlays several series on a shared, fixed y-range ([yMin]..[yMax]) - built for the 0-100
 * driving scores so Safety/Comfort/Pace can be compared on one set of axes over time.
 */
@Composable
fun MultiSeriesChart(
    title: String,
    series: List<Series>,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            series.forEach { s ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(s.color.copy(alpha = 0.11f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(s.color)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        s.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        val maxLen = series.maxOfOrNull { it.values.size } ?: 0
        if (maxLen < 2) {
            Text(
                "Not enough data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
        Canvas(
            modifier = Modifier.fillMaxWidth().height(164.dp).padding(top = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val span = (yMax - yMin).coerceAtLeast(0.001f)
            fun yAt(v: Float) = h - (v - yMin) / span * h

            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val y = h * frac
                drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
            }

            series.forEach { s ->
                if (s.values.size < 2) return@forEach
                fun xAt(i: Int) = w * i / (s.values.size - 1)
                val path = Path()
                path.moveTo(xAt(0), yAt(s.values[0]))
                for (i in 1 until s.values.size) path.lineTo(xAt(i), yAt(s.values[i]))
                drawPath(path, s.color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(s.color, radius = 4f, center = Offset(xAt(s.values.lastIndex), yAt(s.values.last())))
            }
        }
    }
}
