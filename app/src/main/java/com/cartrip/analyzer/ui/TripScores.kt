package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import com.cartrip.analyzer.data.TripEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Three independent driving scores derived from a trip's stored metrics (no per-point load,
 * so it's cheap enough for the trip list and trends too):
 *
 *  - [safety]  risk: hard braking/cornering per km, a speeding proxy, and high peak-g.
 *  - [comfort] ride smoothness: all hard events per km, peak accel harshness, and stop-and-go.
 *  - [speed]   "you vs traffic": actual time against Google's traffic ETA. Null when no estimate.
 *
 * [overall] is a weighted blend (speed only counts when an estimate exists).
 */
data class TripScores(
    val overall: Int,
    val safety: Int,
    val comfort: Int,
    val speed: Int?
) {
    companion object {
        fun from(trip: TripEntity): TripScores {
            val km = max(0.3, trip.distanceM / 1000.0)
            val maxKmh = trip.maxSpeedMps * 3.6

            // A hard brake/corner is risky regardless of how long the trip was, so weight the
            // absolute count, then add a per-km term, a speeding term (>108 km/h), and peak-g.
            val weightedEvents = trip.hardBrakeCount * 1.3 + trip.hardCornerCount * 1.1 + trip.hardAccelCount * 0.6
            val safetyPenalty =
                weightedEvents * 3.0 +
                    weightedEvents / km * 4.0 +
                    max(0.0, maxKmh - 108.0) * 0.8 +
                    max(0.0, trip.peakGForce - 0.50) * 40.0
            val safety = (100.0 - safetyPenalty).coerceIn(0.0, 100.0).roundToInt()

            val avgPeakAccel = (trip.maxBrakeMps2 + trip.maxAccelMps2 + trip.maxLateralMps2) / 3.0
            val idleRatio = if (trip.durationS > 0) trip.idleS / trip.durationS else 0.0
            val comfortPenalty =
                (trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount) / km * 7.0 +
                    max(0.0, avgPeakAccel - 2.0) * 8.0 +
                    idleRatio * 12.0
            val comfort = (100.0 - comfortPenalty).coerceIn(0.0, 100.0).roundToInt()

            val speed: Int? =
                if (trip.googleEtaTrafficS > 0 && trip.durationS > 0) {
                    val ratio = trip.googleEtaTrafficS / trip.durationS // >1 => faster than Google
                    (75.0 + (ratio - 1.0) * 150.0).coerceIn(0.0, 100.0).roundToInt()
                } else null

            val overall = if (speed != null) {
                (safety * 0.42 + comfort * 0.33 + speed * 0.25).roundToInt()
            } else {
                (safety * 0.56 + comfort * 0.44).roundToInt()
            }
            return TripScores(overall, safety, comfort, speed)
        }

        fun color(score: Int): Color = when {
            score >= 80 -> Color(0xFF22C55E)
            score >= 60 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }
    }
}
