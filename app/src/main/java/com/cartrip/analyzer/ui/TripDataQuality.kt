package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import com.cartrip.analyzer.data.TripEntity

/**
 * How much to trust a trip's analysis, derived from the capture stats we already store — motion
 * sample rate, GPS fix rate, and GPS signal-loss gaps. This makes the sensor layer visible so we
 * know which trips are good ground truth for calibration before leaning on them.
 */
enum class QualityLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

data class TripDataQuality(
    val level: QualityLevel,
    val motionHz: Double,
    val gpsHz: Double,
    val gapCount: Int
) {
    fun color(): Color = when (level) {
        QualityLevel.HIGH -> Color(0xFF22C55E)
        QualityLevel.MEDIUM -> Color(0xFFF59E0B)
        QualityLevel.LOW -> Color(0xFFEF4444)
    }

    fun detail(): String =
        "${motionHz.toInt()} Hz motion · ${"%.1f".format(gpsHz)}/s GPS" +
            (if (gapCount > 0) " · $gapCount GPS gap${if (gapCount == 1) "" else "s"}" else "")

    companion object {
        fun from(trip: TripEntity): TripDataQuality {
            val dur = trip.durationS.coerceAtLeast(1.0)
            val motionHz = trip.motionSampleCount / dur
            val gpsHz = trip.locationSampleCount / dur
            val gaps = trip.gpsGapCount
            val level = when {
                motionHz >= 15.0 && gpsHz >= 0.7 && gaps <= 1 -> QualityLevel.HIGH
                motionHz >= 6.0 && gpsHz >= 0.4 && gaps <= 4 -> QualityLevel.MEDIUM
                else -> QualityLevel.LOW
            }
            return TripDataQuality(level, motionHz, gpsHz, gaps)
        }
    }
}
