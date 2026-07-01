package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Drive Stress Score (Rev CJ; v2 Rev CS): a per-trip "how demanding/stressful was this drive" rating,
 * 0-100 where higher = more stressful (the inverse of the green=good Safety/Comfort/Pace scores, so it gets
 * its own green->red scale). Heuristic + calibrated on the owner's real trips by DB-replay, like TripScores.
 *
 * v2 model (owner-calibrated 2026-06-29 against trips 1187 ~40 and 1189 ~78) = SCALE * mean of four roughly
 * balanced pillars:
 *  - congestion: how much of the drive was a slow crawl + how far below the posted limit (StopAndGo).
 *  - hazards: hard-event rate per 10 min (+ a little forced-slowdown severity).
 *  - no-break load: the longest unbroken driving stretch, accelerating past ~45 min (the owner's "no mental
 *    break" idea).
 *  - duration load: trip length.
 * The no-break and duration loads are multiplied by a demand gate (= how congested the drive was), so a
 * long, fast, EMPTY cruise stays restful (gate ~ 0) while a long CONGESTED grind lands at full weight. This
 * fixes the v1 blind spot where a stressful stop-and-go crawl scored "Calm" (drawdowns miss sustained
 * stop-and-go, and the Routes congestion signal was often missing). See HANDOFF section 11.1.
 *
 * Pure domain logic - lives in analysis, not ui. The green->red colour for rendering is `ui.StressColors`.
 */
object StressScore {
    data class Result(val score: Int, val band: String)

    // --- v2 normalizers (the value at which each factor is "maxed") ---
    private const val HARD_EVENTS_PER_10MIN_MAX = 3.0   // hard events / 10 min that maxes the hazard term
    private const val DD_SEV_PER_KM_MAX = 1500.0        // forced-slowdown severity / km, a minor hazard add
    private const val DRAWDOWN_HAZARD_WEIGHT = 0.25     // ...weighted small (drawdowns under-report stop-and-go)
    private const val DEMAND_FULL_AT = 0.30             // congestion at which the demand gate reaches 1.0
    private const val NO_BREAK_FULL_MIN = 45.0          // longest-no-break minutes that maxes the focus load
    private const val NO_BREAK_EXP = 1.7                // > 1 so the no-break load accelerates with duration
    private const val LOAD_FULL_MIN = 45.0              // trip minutes that max the (gated) duration load
    private const val SCALE = 105.0                     // maps the 0..1 blend onto 0..100 (owner-calibrated)

    fun from(trip: TripEntity): Result? {
        val durMin = trip.durationS / 60.0
        val km = trip.distanceM / 1000.0
        if (km < 0.3 || durMin < 1.0 || TripKind.isLikelyNonDrive(trip)) return null

        fun clamp(x: Double) = x.coerceIn(0.0, 1.0)

        // Congestion: stop-and-go crawl share + how far below the posted limit you drove.
        val congestion = 0.5 * clamp(trip.crawlFraction) + 0.5 * clamp(trip.belowLimitLoad)

        // Hazards: hard-event rate per 10 min (motion-fused if present, else GPS) + a little drawdown severity.
        val motionEvents = trip.motionBrakeCount + trip.motionAccelCount + trip.motionTurnCount
        val gpsEvents = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
        val hardEvents = (if (motionEvents > 0) motionEvents else gpsEvents).toDouble()
        val hazards = clamp(
            clamp((hardEvents / max(durMin, 1.0) * 10.0) / HARD_EVENTS_PER_10MIN_MAX) +
                DRAWDOWN_HAZARD_WEIGHT * clamp((trip.drawdownSeverity / max(km, 1.0)) / DD_SEV_PER_KM_MAX)
        )

        // Demand gate: the focus + duration loads only count when the drive was actually demanding. A long
        // empty cruise -> gate ~ 0 (restful); a congested grind -> gate ~ 1.
        val gate = clamp(congestion / DEMAND_FULL_AT)

        // No-break load: the longest unbroken driving stretch, accelerating past ~45 min, demand-gated.
        val noBreakMin = trip.longestNoBreakS / 60.0
        val noBreakLoad = clamp((noBreakMin / NO_BREAK_FULL_MIN).pow(NO_BREAK_EXP)) * gate

        // Duration load, also demand-gated.
        val durationLoad = clamp(durMin / LOAD_FULL_MIN) * gate

        val raw = (durationLoad + congestion + hazards + noBreakLoad) / 4.0
        val score = (raw * SCALE).roundToInt().coerceIn(0, 100)
        return Result(score, band(score))
    }

    /**
     * Distance-weighted average of the per-trip stress scores across [trips]: each trip's 0..100 score is
     * weighted by its distance, so a long stressful drive counts proportionally more than a brief one. NB:
     * this is a km-WEIGHTED average on the same 0..100 band scale - NOT a per-km "burden" rate (it does not
     * divide stress by distance). Null if nothing is scorable. Pure.
     */
    fun kmWeightedAvg(trips: List<TripEntity>): Int? {
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

    /** Per-trip stress scores in chronological order (unscorable trips dropped) - the raw trend series. */
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
}
