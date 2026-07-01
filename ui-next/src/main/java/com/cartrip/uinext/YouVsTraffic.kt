package com.cartrip.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The **You vs Traffic** read for the :ui-next trip detail: the driver's actual time against Google's
 * with-traffic "typical" ETA (and the free-flow best case), as a verdict headline plus proportional bars.
 * Read-only — it renders only when the engine-api [com.cartrip.engine.api.TripSummary.etaTrafficSeconds] is
 * present (a real drive with a fetched ETA); there's no fetch action here (that's a later write gateway).
 * Colours are a :ui-next presentation concern. ASCII-only source (Cp1252 build trap).
 */
@Composable
internal fun YouVsTraffic(
    actualSeconds: Double,
    typicalSeconds: Double,
    freeFlowSeconds: Double?,
    modifier: Modifier = Modifier,
) {
    if (typicalSeconds <= 0.0) return

    val deltaS = actualSeconds - typicalSeconds
    val deltaMin = (deltaS / 60.0).roundToInt()
    val verdict = when {
        deltaMin <= -1 -> "${-deltaMin} min faster"
        deltaMin >= 1 -> "$deltaMin min slower"
        else -> "On pace"
    }
    val youColor = when {
        actualSeconds <= typicalSeconds -> ETA_GREEN
        actualSeconds <= typicalSeconds * 1.15 -> ETA_AMBER
        else -> ETA_RED
    }
    // A meaningful free-flow only when it's a shade under the typical time (else it's noise / equal).
    val freeFlow = freeFlowSeconds?.takeIf { it > 0.0 && it < typicalSeconds }
    val scaleMax = max(actualSeconds, max(typicalSeconds, freeFlow ?: 0.0))

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "YOU VS TRAFFIC",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(verdict, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = youColor)
        }
        EtaBar("You", actualSeconds, scaleMax, youColor)
        EtaBar("Typical traffic", typicalSeconds, scaleMax, ETA_NEUTRAL)
        if (freeFlow != null) EtaBar("Free-flow", freeFlow, scaleMax, ETA_NEUTRAL.copy(alpha = 0.55f))
    }
}

/** One labeled proportional bar: label + a track-backed fill sized to [seconds]/[scaleMax], with the m:ss value. */
@Composable
private fun EtaBar(label: String, seconds: Double, scaleMax: Double, color: Color) {
    val fraction = if (scaleMax > 0) (seconds / scaleMax).toFloat().coerceIn(0.02f, 1f) else 0.02f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(seconds), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

private val ETA_GREEN = Color(0xFF22C55E)
private val ETA_AMBER = Color(0xFFF59E0B)
private val ETA_RED = Color(0xFFEF4444)
private val ETA_NEUTRAL = Color(0xFF64748B)
