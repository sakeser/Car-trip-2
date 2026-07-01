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
 * Green=good score badge for the :ui-next screens — used for the Driving Intelligence **Smoothness** pillar
 * (and any other 0..100 "higher is better" score). The SCORE is engine-domain (`analysis.DrivingIntelligence` /
 * `analysis.TripScores`); the green -> amber -> red PALETTE here is a :ui-next presentation concern (the UI
 * module owns its own colours and never imports the legacy `ui.ScoreColors`). Thresholds mirror the legacy
 * `ScoreColors` (80 / 60) so the two stay visually consistent. Inverse-scale sibling: [StressChip].
 * ASCII-only source (this Windows build mojibakes non-ASCII literals in BOM-less .kt files).
 */
@Composable
internal fun ScoreChip(score: Int, modifier: Modifier = Modifier) {
    val color = scoreColor(score)
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

/** Green (good) -> amber -> red by 0..100 score; mirrors the legacy ui.ScoreColors thresholds. */
private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 60 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}
