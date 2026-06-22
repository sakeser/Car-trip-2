package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import com.cartrip.analyzer.data.TripEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Three independent driving scores derived from a trip's stored metrics (no per-point load,
 * so it's cheap enough for the trip list and trends too):
 *
 *  - [safety]  risk, modelled after Tesla's Safety Score: exposure-normalized factors — the
 *    fraction of moving time spent hard-braking (>0.30g), turning aggressively (>0.40g) and
 *    accelerating hard (>0.30g), plus speeding over OSM posted limits (when coverage is good).
 *  - [comfort] ride smoothness: all hard events per km, peak accel harshness, and stop-and-go.
 *  - [speed]   "you vs traffic": actual time against Google's traffic ETA. Null when no estimate.
 *
 * [overall] is a weighted blend (speed only counts when an estimate exists). These are heuristic
 * weights tuned for a sensible spread — not regressed against real collision data.
 */
data class TripScores(
    val overall: Int,
    val safety: Int,
    val comfort: Int,
    val speed: Int?
) {
    companion object {
        // Speeding only weighs in when OSM covered enough of the route to trust the number.
        private const val MIN_LIMIT_COVERAGE = 0.4

        fun from(trip: TripEntity): TripScores {
            val km = max(0.3, trip.distanceM / 1000.0)

            // Exposure-normalized factors as percentages of moving time.
            val brakePct = trip.hardBrakePct * 100.0
            val turnPct = trip.aggressiveTurnPct * 100.0
            val accelPct = trip.hardAccelPct * 100.0
            val speedingApplies = trip.limitCoverage >= MIN_LIMIT_COVERAGE
            val speedingPct = if (speedingApplies) trip.speedingPct * 100.0 else 0.0
            val severeOver = if (speedingApplies) max(0.0, trip.maxOverLimitKmh - 20.0) else 0.0

            val safetyPenalty =
                brakePct * 5.0 +
                    turnPct * 4.5 +
                    accelPct * 2.0 +
                    speedingPct * 0.8 +
                    severeOver * 0.5 +
                    max(0.0, trip.peakGForce - 0.60) * 25.0   // lenient g net (mount/bump tolerant)
            val safety = (100.0 - safetyPenalty).coerceIn(0.0, 100.0).roundToInt()

            val avgPeakAccel = (trip.maxBrakeMps2 + trip.maxAccelMps2 + trip.maxLateralMps2) / 3.0
            val idleRatio = if (trip.durationS > 0) trip.idleS / trip.durationS else 0.0
            val comfortPenalty =
                (trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount) / km * 7.0 +
                    max(0.0, avgPeakAccel - 2.0) * 8.0 +
                    idleRatio * 12.0 +
                    trip.jerkyPct * 100.0 * 1.6 + // abrupt, jerky driving is uncomfortable beyond the g level
                    trip.harshStopCount * 3.0      // jerky stops the driver controls (rough road isn't penalized)
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
