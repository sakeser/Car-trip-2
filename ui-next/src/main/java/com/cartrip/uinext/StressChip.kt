package com.cartrip.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Drive Stress badge for the :ui-next screens. The SCORE is engine-domain (`TripSummary.stressScore`, derived
 * from `analysis.StressScore`); the green -> amber -> red PALETTE here is a :ui-next presentation concern - the
 * UI module owns its own colours and never imports the legacy `ui.StressColors` / its `Color`. The thresholds
 * mirror the legacy StressColors (25 / 45 / 65) so the two stay visually consistent. ASCII-only source
 * (this Windows build mojibakes non-ASCII literals in BOM-less .kt files).
 */
@Composable
internal fun StressChip(score: Int, modifier: Modifier = Modifier) {
    val color = stressColor(score)
    Text(
        text = "$score",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Calm green -> lime -> amber -> red by stress score; mirrors the legacy ui.StressColors thresholds. */
private fun stressColor(score: Int): Color = when {
    score < 25 -> Color(0xFF22C55E)
    score < 45 -> Color(0xFF84CC16)
    score < 65 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}
