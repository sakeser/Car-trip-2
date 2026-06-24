package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import com.cartrip.analyzer.analysis.GnssQuality
import com.cartrip.analyzer.data.TripEntity

/**
 * How much to trust a trip's analysis, derived from capture stats we already store: motion
 * sample rate, GPS fix rate, and GPS signal-loss gaps.
 */
enum class QualityLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

data class TripDataQuality(
    val level: QualityLevel,
    val motionHz: Double,
    val gpsHz: Double,
    val gapCount: Int,
    val gnssLevel: GnssQuality.Level,
    val gnssSummary: String?
) {
    fun color(): Color = when (level) {
        QualityLevel.HIGH -> Color(0xFF22C55E)
        QualityLevel.MEDIUM -> Color(0xFFF59E0B)
        QualityLevel.LOW -> Color(0xFFEF4444)
    }

    fun detail(): String = buildString {
        if (motionHz < 1.0) append("Motion missing") else append("${motionHz.toInt()} Hz motion")
        append(" - ${"%.1f".format(gpsHz)}/s GPS")
        if (gapCount > 0) append(" - $gapCount GPS gap${if (gapCount == 1) "" else "s"}")
        gnssSummary?.let { append(" - $it") }
    }

    companion object {
        fun from(trip: TripEntity): TripDataQuality {
            val dur = trip.durationS.coerceAtLeast(1.0)
            val motionHz = trip.motionSampleCount / dur
            val gpsHz = trip.locationSampleCount / dur
            val gaps = trip.gpsGapCount
            val base = when {
                motionHz >= 15.0 && gpsHz >= 0.7 && gaps <= 1 -> QualityLevel.HIGH
                motionHz >= 6.0 && gpsHz >= 0.4 && gaps <= 4 -> QualityLevel.MEDIUM
                else -> QualityLevel.LOW
            }
            val gnss = GnssQuality.level(trip.gnssAvgSatsUsed, trip.gnssAvgCn0, trip.gnssSampleCount)
            // Weak satellite geometry/signal undercuts an otherwise-clean capture: cap High at Medium.
            val level = if (gnss == GnssQuality.Level.WEAK && base == QualityLevel.HIGH) {
                QualityLevel.MEDIUM
            } else {
                base
            }
            val summary = GnssQuality.summary(
                trip.gnssAvgSatsUsed, trip.gnssAvgCn0, trip.gnssL5Seen, trip.gnssSampleCount
            )
            return TripDataQuality(level, motionHz, gpsHz, gaps, gnss, summary)
        }
    }
}
