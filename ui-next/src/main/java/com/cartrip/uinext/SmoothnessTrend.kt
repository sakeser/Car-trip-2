package com.cartrip.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A compact **Smoothness trend** sparkline for the Health tab: a 0..100 line of each scorable drive's Smoothness
 * over time (oldest -> newest), with faint guides at the band thresholds (60 / 80). Pure visualization of the
 * engine's existing `smoothnessScore` values — no scoring logic. Renders nothing for fewer than 2 points.
 * Colours are a :ui-next presentation concern. ASCII source (Cp1252 trap).
 */
@Composable
internal fun SmoothnessTrend(values: List<Int>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillTop = lineColor.copy(alpha = 0.20f)
    val fillBottom = lineColor.copy(alpha = 0.02f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SMOOTHNESS TREND",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "latest ${values.last()}",
                style = MaterialTheme.typography.labelMedium,
                color = lineColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int): Float = if (values.size == 1) 0f else w * i / (values.size - 1)
            fun y(v: Int): Float = h - (v.coerceIn(0, 100) / 100f) * h

            // Band-threshold guides (60 / 80) so the line reads against the smoothness bands.
            listOf(60, 80).forEach { t ->
                drawLine(
                    color = gridColor.copy(alpha = 0.15f),
                    start = Offset(0f, y(t)),
                    end = Offset(w, y(t)),
                    strokeWidth = 1f,
                )
            }

            val area = Path().apply {
                moveTo(x(0), h)
                values.forEachIndexed { i, v -> lineTo(x(i), y(v)) }
                lineTo(x(values.lastIndex), h)
                close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(fillTop, fillBottom), startY = 0f, endY = h))

            val line = Path().apply {
                moveTo(x(0), y(values.first()))
                values.drop(1).forEachIndexed { i, v -> lineTo(x(i + 1), y(v)) }
            }
            drawPath(line, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }
}
