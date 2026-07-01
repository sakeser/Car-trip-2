package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color

/**
 * The green=good -> amber -> red colour for the 0..100 driver scores (Safety / Comfort / Pace and the
 * [com.cartrip.analyzer.analysis.DrivingIntelligence] Smoothness / Efficiency pillars). Rendering only; the
 * score logic lives in `analysis.TripScores` / `analysis.DrivingIntelligence`. The inverse-scale (green=calm)
 * sibling for the demand/stress side is `ui.StressColors`. Split out of `TripScores` in the Rev CX move.
 */
object ScoreColors {
    fun color(score: Int): Color = when {
        score >= 80 -> Color(0xFF22C55E)
        score >= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}
