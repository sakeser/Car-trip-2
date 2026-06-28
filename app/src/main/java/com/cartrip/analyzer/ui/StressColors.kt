package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color

/**
 * The green (calm) -> amber -> red (stressful) colour for the Drive Stress Score — the inverse of the
 * green=good TripScores.color. Rendering only; the score logic lives in `analysis.StressScore`.
 */
object StressColors {
    fun color(score: Int): Color = when {
        score < 25 -> Color(0xFF22C55E)
        score < 45 -> Color(0xFF84CC16)
        score < 65 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}
