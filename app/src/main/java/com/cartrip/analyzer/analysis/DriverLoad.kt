package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Driver Load / "Drive readiness" (Rev CW, v1): a single 0-100 number that **builds with recent stressful
 * driving and decays with time** (rest), plus a 24 h recovery forecast. Unlike [StressScore] (a per-trip
 * rating) and [StressScore.kmWeightedAvg] (a window average), this is a **recency-weighted cumulative state
 * "right now"** — an exponentially-weighted leaky integrator of stress-weighted driving time.
 *
 * Evidence base (it maps onto established load models): the EWMA Acute:Chronic Workload Ratio (Williams et
 * al., BJSM 2017), the Banister fitness-fatigue model's fast-decaying "fatigue" term, and the Borbely
 * two-process homeostatic-pressure curve — all leaky-integrator math: load accumulates per event and decays
 * exponentially toward baseline with rest.
 *
 * ⚠️ Heuristic wellness/awareness indicator from the user's OWN driving — **NOT** a medical or
 * fitness-to-drive assessment. Real fatigue depends on sleep, which the app can't see ("time not driving" is
 * only a proxy for recovery).
 *
 * Pure domain logic (analysis, not ui); computable from the trip list (stress + duration + timestamp), no
 * schema change. The green->red render colour reuses `ui.StressColors`.
 */
object DriverLoad {

    /**
     * Decay time-constant (hours). Half-life = TAU * ln 2 ~ 20 h, so a drive 3 days old still contributes
     * exp(-72/28.8) ~ 8% ("little difference", per the owner). Same role as the ACWR "acute" EWMA constant.
     */
    const val TAU_HOURS = 28.8

    /**
     * Saturation constant for mapping the raw leaky-integrator sum onto 0-100 via 1 - exp(-raw/K). Calibrated
     * by DB-replay over the owner's 39 scorable drives (the way Stress v2 was anchored): at K=1.0 a calm
     * isolated commute lands ~23 (Light), the median real driving day peaks ~54 (Moderate), a busy day ~72
     * (Elevated), and the heaviest observed day ~78 — leaving headroom for a truly exceptional day to read
     * High. Lower K = the scale fills faster. Tunable; workshop over time.
     */
    const val SATURATION_K = 1.0

    /**
     * At/below this 0-100 load the driver reads "recovered" (the Low/Light boundary) — used for the "back to
     * baseline" forecast. Assumes no further driving; for a daily driver the live load rarely sits this low.
     */
    const val BASELINE_LOAD = 20

    /** Hours plotted in the recovery forecast (now -> +[FORECAST_HOURS], assuming no further driving). */
    const val FORECAST_HOURS = 24

    data class State(
        val load: Int,                 // 0-100, higher = more accumulated load (inverse of readiness)
        val readiness: Int,            // 100 - load
        val band: String,
        val drivesCounted: Int,        // scorable drives that contributed a (non-negligible) impulse
        val recovery: List<Float>,     // forecast load at now+0..now+FORECAST_HOURS h, one point per hour
        val hoursToBaseline: Int?,     // whole hours until the forecast load first reaches BASELINE_LOAD
    )

    /**
     * Stress-weighted driving-time impulse a trip deposits: (stress/100) * durationHours. A calm 5-min hop is
     * ~0; a 45-min stressful crawl is large. Null when the trip isn't stress-scorable (walks, too short).
     */
    fun impulse(trip: TripEntity): Double? {
        val stress = StressScore.from(trip)?.score ?: return null
        val hours = trip.durationS / 3600.0
        if (hours <= 0.0) return null
        return (stress / 100.0) * hours
    }

    /** The raw leaky-integrator load at [nowMs]: sum of each trip's impulse decayed by its age. Pure. */
    fun rawLoadAt(trips: List<TripEntity>, nowMs: Long): Double {
        var sum = 0.0
        for (t in trips) {
            val i = impulse(t) ?: continue
            val ageH = (nowMs - t.startTime) / 3_600_000.0
            if (ageH < 0.0) continue   // a trip timestamped in the future can't contribute
            sum += i * exp(-ageH / TAU_HOURS)
        }
        return sum
    }

    /** Map a raw leaky-integrator sum onto the 0-100 load scale (saturating, so it can't exceed 100). */
    fun scale(raw: Double): Int =
        ((1.0 - exp(-raw / SATURATION_K)) * 100.0).roundToInt().coerceIn(0, 100)

    /**
     * Full "right now" state at [nowMs] over the whole [trips] history: the current load + readiness, plus the
     * 24 h recovery curve (load if you stop driving now) and the hours until it decays back to baseline. Load
     * is recency-weighted, so passing the full history (not a window) is correct — old drives self-attenuate.
     */
    fun state(trips: List<TripEntity>, nowMs: Long): State {
        val raw = rawLoadAt(trips, nowMs)
        val load = scale(raw)
        val drives = trips.count { t ->
            val i = impulse(t) ?: return@count false
            val ageH = (nowMs - t.startTime) / 3_600_000.0
            ageH >= 0.0 && i * exp(-ageH / TAU_HOURS) >= 0.01
        }
        // With no further driving the raw sum decays as raw * exp(-h/TAU); scale each hour onto 0-100.
        val recovery = (0..FORECAST_HOURS).map { h -> scale(raw * exp(-h / TAU_HOURS)).toFloat() }
        return State(
            load = load,
            readiness = 100 - load,
            band = band(load),
            drivesCounted = drives,
            recovery = recovery,
            hoursToBaseline = hoursToBaseline(raw),
        )
    }

    /**
     * Whole hours until the load decays to [BASELINE_LOAD] with no further driving. 0 if already at/below it;
     * null if it can't reach baseline within a generous horizon (shouldn't happen for realistic loads). Solved
     * in closed form from the decay then rounded up to the next whole hour.
     */
    fun hoursToBaseline(raw: Double): Int? {
        if (scale(raw) <= BASELINE_LOAD) return 0
        // Smallest raw whose scaled load is still > BASELINE_LOAD shrinks as the threshold; invert the scale to
        // find the raw at exactly BASELINE_LOAD, then the decay time to reach it.
        val targetRaw = -SATURATION_K * ln(1.0 - BASELINE_LOAD / 100.0)
        if (targetRaw <= 0.0 || raw <= targetRaw) return 0
        val hours = TAU_HOURS * ln(raw / targetRaw)
        if (hours.isNaN() || hours < 0.0) return 0
        return kotlin.math.ceil(hours).toInt().coerceAtMost(240)
    }

    /** Load band (higher = more load). Mirrors the green->red intent of [StressColors]. */
    fun band(load: Int): String = when {
        load < 20 -> "Low"
        load < 40 -> "Light"
        load < 60 -> "Moderate"
        load < 80 -> "Elevated"
        else -> "High"
    }
}
