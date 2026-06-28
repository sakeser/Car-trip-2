package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import com.cartrip.analyzer.data.TripEntity
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Drive Stress Score (Rev CJ): a per-trip "how demanding/stressful was this drive" rating, 0-100 where
 * **higher = more stressful** (the inverse of the green=good Safety/Comfort/Pace scores, so it gets its own
 * green->red scale). Heuristic + calibrated on the owner's real trips (DB-replay), like [TripScores] — not
 * regressed against ground truth.
 *
 * Factors (each normalized 0..1, then weighted): forced-slowdown rate + severity (drawdowns, the core
 * stop-and-go signal), hard-event rate, congestion vs free-flow (Routes), speeding exposure, trip length,
 * and a small time-of-day load (rush hour / late night). Pure + unit-testable from stored aggregates.
 */
object StressScore {
    data class Result(val score: Int, val band: String)

    // Normalizers (the value at which a factor is considered "maxed").
    private const val DD_RATE_PER_10MIN_MAX = 3.0
    private const val DD_SEV_PER_KM_MAX = 1500.0
    private const val HARD_EVENTS_PER_KM_MAX = 4.0
    private const val SPEEDING_SEV_MAX = 120.0
    private const val LOAD_MINUTES_MAX = 45.0
    // Final lift so the owner's worst real drives use the upper range (calibrated by DB-replay).
    private const val SCALE = 130.0

    fun from(trip: TripEntity): Result? {
        val durMin = trip.durationS / 60.0
        val km = trip.distanceM / 1000.0
        if (km < 0.3 || durMin < 1.0 || TripKind.isLikelyNonDrive(trip)) return null

        fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
        val ddRate = clamp((trip.drawdownCount / max(durMin / 10.0, 0.1)) / DD_RATE_PER_10MIN_MAX)
        val ddSev = clamp((trip.drawdownSeverity / max(km, 1.0)) / DD_SEV_PER_KM_MAX)
        val motionEvents = trip.motionBrakeCount + trip.motionAccelCount + trip.motionTurnCount
        val gpsEvents = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
        val hardEvents = (if (motionEvents > 0) motionEvents else gpsEvents).toDouble()
        val hevRate = clamp((hardEvents / max(km, 1.0)) / HARD_EVENTS_PER_KM_MAX)
        val congestion = if (trip.googleEtaFreeFlowS > 0.0)
            clamp(trip.durationS / trip.googleEtaFreeFlowS - 1.0) else 0.0
        val speeding = clamp(trip.speedingSeverity / SPEEDING_SEV_MAX)
        val load = clamp(durMin / LOAD_MINUTES_MAX)
        val tod = timeOfDayLoad(trip.startTime)

        val raw = 0.28 * ddRate + 0.14 * ddSev + 0.16 * hevRate + 0.22 * congestion +
            0.08 * speeding + 0.08 * load + 0.04 * tod
        val score = (raw * SCALE).roundToInt().coerceIn(0, 100)
        return Result(score, band(score))
    }

    /**
     * Distance-weighted ("normalized by km") average stress across [trips]: a long stressful drive counts
     * proportionally more than a brief one, so this reflects how demanding the driving was *per distance*
     * rather than per trip. Null if nothing is scorable. Pure.
     */
    fun avgPerKm(trips: List<TripEntity>): Int? {
        var num = 0.0
        var den = 0.0
        for (t in trips) {
            val s = from(t) ?: continue
            val km = (t.distanceM / 1000.0).coerceAtLeast(0.1)
            num += s.score * km
            den += km
        }
        return if (den <= 0.0) null else (num / den).roundToInt()
    }

    /** Per-trip stress scores in chronological order (unscorable trips dropped) — the raw trend series. */
    fun series(trips: List<TripEntity>): List<Int> =
        trips.sortedBy { it.startTime }.mapNotNull { from(it)?.score }

    /**
     * Trailing moving average over the last [window] points (same length as input, no lag-drop), so the
     * trend reads as a smooth evolution instead of a spiky per-trip scatter. Pure.
     */
    fun trailingAvg(values: List<Int>, window: Int): List<Float> {
        if (values.isEmpty()) return emptyList()
        val out = ArrayList<Float>(values.size)
        for (i in values.indices) {
            val start = maxOf(0, i - window + 1)
            var sum = 0.0
            for (j in start..i) sum += values[j]
            out += (sum / (i - start + 1)).toFloat()
        }
        return out
    }

    fun band(score: Int): String = when {
        score < 25 -> "Calm"
        score < 45 -> "Moderate"
        score < 65 -> "Busy"
        else -> "High stress"
    }

    /** Green (calm) -> amber -> red (stressful) — the inverse of TripScores.color. */
    fun color(score: Int): Color = when {
        score < 25 -> Color(0xFF22C55E)
        score < 45 -> Color(0xFF84CC16)
        score < 65 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    private fun timeOfDayLoad(startTime: Long): Double {
        if (startTime <= 0L) return 0.0
        val hr = Calendar.getInstance().apply { timeInMillis = startTime }.get(Calendar.HOUR_OF_DAY)
        return when {
            hr in intArrayOf(7, 8, 16, 17, 18) -> 0.6   // rush hour
            hr >= 22 || hr <= 5 -> 0.4                   // late night / very early
            else -> 0.0
        }
    }
}
