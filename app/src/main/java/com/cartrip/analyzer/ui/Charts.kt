package com.cartrip.analyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    selectedIndex: Int? = null,
    average: Float? = null,
    yRange: ClosedFloatingPointRange<Float>? = null,
    xAxisLabel: String? = null
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
        if (yRange != null) {
            minV = yRange.start
            maxV = yRange.endInclusive
        }
        if (maxV - minV < 0.001f) {
            maxV += 1f
            minV -= 1f
        }

        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
        val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
        val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
        val midV = (minV + maxV) / 2f
        // Pick label precision from the range: whole numbers for wide ranges (e.g. 0..100 stress), more
        // decimals as the range narrows (e.g. ~$0.02 cost/km would round to a useless "0.1" at 1 decimal).
        fun fmt(v: Float): String = when {
            maxV - minV >= 10f -> v.roundToInt().toString()
            maxV - minV >= 1f -> "%.1f".format(v)
            else -> "%.2f".format(v)
        }

        Row(modifier = Modifier.fillMaxWidth().height(124.dp).padding(top = 6.dp)) {
            // Y-axis labels (max / mid / min) aligned to the gridlines — replaces the old min/max footer.
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(fmt(maxV), style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text(fmt(midV), style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text(fmt(minV), style = MaterialTheme.typography.labelSmall, color = axisColor)
            }
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                fun xAt(i: Int) = w * i / (values.size - 1)
                fun yAt(v: Float) = h - (v - minV) / (maxV - minV) * h

                // Gridlines at min / mid / max.
                for (gv in listOf(minV, midV, maxV)) {
                    drawLine(gridColor, Offset(0f, yAt(gv)), Offset(w, yAt(gv)), 1f)
                }
                if (minV < 0f && maxV > 0f) {
                    drawLine(baselineColor, Offset(0f, yAt(0f)), Offset(w, yAt(0f)), 1.5f)
                }
                // Average reference line (dashed) — e.g. the mean pace-vs-traffic across the window.
                average?.takeIf { it in minV..maxV }?.let { avg ->
                    val ya = yAt(avg)
                    drawLine(
                        color = color.copy(alpha = 0.55f),
                        start = Offset(0f, ya),
                        end = Offset(w, ya),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }
                val path = Path()
                path.moveTo(xAt(0), yAt(values[0].coerceIn(minV, maxV)))
                for (i in 1 until values.size) {
                    path.lineTo(xAt(i), yAt(values[i].coerceIn(minV, maxV)))
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(fill, color.copy(alpha = 0.10f))
                drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color, radius = 4.5f, center = Offset(xAt(values.lastIndex), yAt(values.last().coerceIn(minV, maxV))))

                selectedIndex?.coerceIn(0, values.size - 1)?.let { selected ->
                    drawLine(cursorColor, Offset(xAt(selected), 0f), Offset(xAt(selected), h), 2f)
                }
            }
        }
        xAxisLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = axisColor,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A line chart of **percent deviation from a baseline** (the 0% line), with the area below/above the
 * baseline filled in two colours by meaning. Built for "fuel economy vs your average": each point is how
 * far that drive's L/100km sat below/above your norm, so [belowIsBetter] paints the under-baseline side
 * green (you burned less) and the over-baseline side red.
 *
 * [referencePct] draws a dashed horizontal reference at a fixed percent — e.g. where the vehicle's factory
 * combined rating falls relative to the same baseline. The vertical scale is symmetric around 0 and sized
 * to the data (and the reference), floored at [minAmplitude] so a calm series still reads.
 */
@Composable
fun PercentChangeChart(
    title: String,
    pct: List<Float>,
    modifier: Modifier = Modifier,
    belowIsBetter: Boolean = true,
    referencePct: Float? = null,
    referenceLabel: String? = null,
    xAxisLabel: String? = null,
    minAmplitude: Float = 4f
) {
    val betterColor = Color(0xFF22C55E)
    val worseColor = Color(0xFFEF4444)
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (pct.size < 2) {
            Text(
                "Not enough data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        var amp = pct.maxOf { abs(it) }
        referencePct?.let { amp = max(amp, abs(it)) }
        amp = (amp * 1.2f).coerceAtLeast(minAmplitude)

        val aboveColor = if (belowIsBetter) worseColor else betterColor
        val belowColor = if (belowIsBetter) betterColor else worseColor
        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
        val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        val refColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
        val strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

        Row(modifier = Modifier.fillMaxWidth().height(124.dp).padding(top = 6.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text("+${amp.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text("0%", style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text("-${amp.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = axisColor)
            }
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                fun xAt(i: Int) = w * i / (pct.size - 1)
                fun yAt(v: Float) = h / 2f - (v.coerceIn(-amp, amp) / amp) * (h / 2f)
                val y0 = yAt(0f)

                drawLine(gridColor, Offset(0f, yAt(amp)), Offset(w, yAt(amp)), 1f)
                drawLine(gridColor, Offset(0f, yAt(-amp)), Offset(w, yAt(-amp)), 1f)

                // Area between the curve and the baseline, clipped above/below the 0% line so each side
                // takes its own meaning colour.
                val area = Path().apply {
                    moveTo(xAt(0), yAt(pct[0]))
                    for (i in 1 until pct.size) lineTo(xAt(i), yAt(pct[i]))
                    lineTo(xAt(pct.lastIndex), y0)
                    lineTo(xAt(0), y0)
                    close()
                }
                clipRect(left = 0f, top = 0f, right = w, bottom = y0) {
                    drawPath(area, aboveColor.copy(alpha = 0.20f))
                }
                clipRect(left = 0f, top = y0, right = w, bottom = h) {
                    drawPath(area, belowColor.copy(alpha = 0.20f))
                }

                drawLine(zeroColor, Offset(0f, y0), Offset(w, y0), 2f)

                val line = Path()
                line.moveTo(xAt(0), yAt(pct[0]))
                for (i in 1 until pct.size) line.lineTo(xAt(i), yAt(pct[i]))
                drawPath(line, strokeColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(strokeColor, radius = 4.5f, center = Offset(xAt(pct.lastIndex), yAt(pct.last())))

                referencePct?.takeIf { it in -amp..amp }?.let { r ->
                    val yr = yAt(r)
                    drawLine(
                        refColor, Offset(0f, yr), Offset(w, yr), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }
            }
        }
        referenceLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = refColor,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
            )
        }
        xAxisLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = axisColor,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

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
 * A per-item diverging bar chart around a zero baseline: each bar is coloured by sign
 * ([positiveColor] up, [negativeColor] down). Built for "minutes vs traffic per trip" — green =
 * faster, red = slower — with an optional dashed average.
 *
 * The vertical scale is **robust**: it's the [scalePercentile] of the absolute values (floored at
 * [minScale]) rather than the single max, so a couple of outliers don't crush every other bar to
 * near-zero. Bars past the scale clamp to full height and get a light cap to show they run off it.
 */
@Composable
fun DivergingBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    positiveColor: Color = Color(0xFF22C55E),
    negativeColor: Color = Color(0xFFEF4444),
    average: Float? = null,
    scalePercentile: Float = 0.85f,
    minScale: Float = 0.001f
) {
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val avgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val capColor = Color.White.copy(alpha = 0.75f)
    if (values.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(48.dp))
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val absSorted = values.map { abs(it) }.sorted()
        val pIdx = ((absSorted.size - 1) * scalePercentile).toInt().coerceIn(0, absSorted.lastIndex)
        val amp = absSorted[pIdx].coerceAtLeast(minScale)
        fun yAt(v: Float) = mid - (v.coerceIn(-amp, amp) / amp) * (mid * 0.88f)
        drawLine(baselineColor, Offset(0f, mid), Offset(w, mid), 1.5f)
        val slot = w / values.size
        val barW = (slot * 0.6f).coerceAtMost(16f)
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2f
            val y = yAt(v)
            val top = min(y, mid)
            val barH = abs(mid - y).coerceAtLeast(2f)
            val col = if (v >= 0f) positiveColor else negativeColor
            drawRect(col, topLeft = Offset(cx - barW / 2f, top), size = Size(barW, barH))
            // Off-the-scale marker: a light cap at the bar's outer tip.
            if (abs(v) > amp * 1.02f) {
                val tipY = if (v >= 0f) top else top + barH - 2.5f
                drawRect(capColor, topLeft = Offset(cx - barW / 2f, tipY), size = Size(barW, 2.5f))
            }
        }
        if (average != null) {
            val ay = yAt(average)
            drawLine(
                avgColor,
                Offset(0f, ay),
                Offset(w, ay),
                2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
            )
        }
    }
}

