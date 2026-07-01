package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Driving Intelligence (Rev CX) — the public three-pillar consolidation of the older
 * Safety / Comfort / Pace / Stress / Fuel scores into **how you drove, how hard the drive was, and what it
 * cost you**. Pure roll-up of EXISTING signals (no new sensor formulas, no schema change): it composes
 * [TripScores] (Safety/Comfort), [StressScore] (the calibrated per-trip demand model), [StopAndGo] aggregates,
 * and [FuelEstimator]. The render colours live in `ui` (`ScoreColors` green=good, `StressColors` green=calm).
 *
 * The product principle (see DRIVING_INTELLIGENCE_SCORING.md / ADVISORY §1.1): **separate driver STYLE from
 * road DEMAND, keep fuel as an OUTCOME, and never blend style+demand into one average.** Instead the headline
 * is *conditional* — Smoothness read against the demand band ("Smooth for a demanding drive"), placed on a 2x2
 * style x demand grid. This fixes the trip-1189 confound where a smoothly-handled stop-and-go crawl read "Calm".
 *
 *  - **Smoothness** (driver style, higher = smoother) = blend of Safety (rare hard spikes) + Comfort (sustained
 *    jerk/ride). The old Safety & Comfort scores survive as drill-downs into this one pillar.
 *  - **Demand / Load** (context, higher = harder) = the Stress v2 score (congestion + no-break + duration),
 *    which is already the owner-calibrated "how demanding was this drive" model. NOT the driver's fault.
 *  - **Efficiency** (outcome, higher = better) = fuel economy vs the vehicle's rating / the driver's own norm.
 */
object DrivingIntelligence {

    /** Smoothness at/above this reads as a "smooth style" for the 2x2 quadrant + headline. */
    const val SMOOTH_AT = 70
    /** Demand (Stress v2) at/above this reads as a "high-demand" drive for the 2x2 quadrant. */
    const val DEMAND_HIGH_AT = 45

    enum class DemandLevel { LOW, MODERATE, HIGH }

    /**
     * The 2x2 style x demand interpretation. [headline] is the conditional "Drive Quality" phrase shown as the
     * trip's one-line verdict; [coaching] says whether the takeaway is about the driver or the road.
     */
    enum class Quadrant(val headline: String, val coaching: String) {
        EASY_SMOOTH("Easy, smooth drive", "Low demand, smooth control."),
        SMOOTH_UNDER_PRESSURE("Smooth for a demanding drive", "High demand, handled smoothly — nicely done."),
        ROUGH_ON_EASY_ROAD("Rough on an easy road", "Low demand, but sharp inputs — the most actionable."),
        DEMANDING_AND_ROUGH("Demanding and rough", "High demand and sharp inputs — separate the traffic from the style."),
    }

    /** One pillar: a 0..100 [score], a band [label], and a plain-language [read]. */
    data class Pillar(val score: Int, val label: String, val read: String)

    data class Result(
        val smoothness: Pillar,
        val demand: Pillar,
        val efficiency: Pillar?,   // null for non-drives / no fuel estimate
        val demandLevel: DemandLevel,
        val smoothStyle: Boolean,
        val quadrant: Quadrant,
        /** The conditional "Drive Quality" headline phrase (== quadrant.headline). */
        val headline: String,
    ) {
        /** A compact one-line summary for the AI export / share. */
        fun oneLine(): String {
            val eff = efficiency?.let { ", ${it.read}" } ?: ""
            return "$headline. Smoothness ${smoothness.score} (${smoothness.label}); " +
                "Demand ${demand.score} (${demand.label})$eff."
        }
    }

    /**
     * Per-trip Driving Intelligence, or null when the trip isn't a scorable drive (walks / too short — same
     * gate as [StressScore]). [vehicle] is only needed for the Efficiency pillar; pass null (e.g. from the
     * `:ui-next` engine-api mapper, which has no vehicle profile) to get Smoothness + Demand + the headline with
     * `efficiency = null`. [personalAvgL100] is the driver's own recent economy for the "vs your average" read;
     * when null the efficiency read compares against the vehicle's combined rating instead.
     */
    fun from(trip: TripEntity, vehicle: FuelEstimator.Vehicle? = null, personalAvgL100: Double? = null): Result? {
        val stress = StressScore.from(trip) ?: return null
        val scores = TripScores.from(trip)

        // --- Smoothness (style) = Safety (the tail) + Comfort (the body) ---
        val smoothnessScore = (scores.safety * 0.55 + scores.comfort * 0.45).roundToInt().coerceIn(0, 100)
        val motionEvents = trip.motionBrakeCount + trip.motionAccelCount + trip.motionTurnCount
        val gpsEvents = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
        val hardEvents = if (motionEvents > 0) motionEvents else gpsEvents
        val smoothRead = when {
            smoothnessScore >= 85 && hardEvents == 0 -> "Smooth, controlled inputs throughout"
            hardEvents == 0 -> "No sharp braking, acceleration, or cornering"
            else -> "$hardEvents sharp ${plural(hardEvents, "input", "inputs")}" +
                if (trip.harshStopCount > 0) ", ${trip.harshStopCount} hard ${plural(trip.harshStopCount, "stop", "stops")}" else ""
        }
        val smoothness = Pillar(smoothnessScore, smoothnessBand(smoothnessScore), smoothRead)

        // --- Demand / Load (context) = Stress v2 ---
        val demandScore = stress.score
        val level = when {
            demandScore >= DEMAND_HIGH_AT -> DemandLevel.HIGH
            demandScore < 25 -> DemandLevel.LOW
            else -> DemandLevel.MODERATE
        }
        val demand = Pillar(demandScore, demandBand(demandScore), demandRead(trip, level))

        // --- Efficiency (outcome) = fuel economy (only when a vehicle profile is available) ---
        val efficiency = vehicle?.let { efficiencyPillar(trip, it, personalAvgL100) }

        // --- Conditional headline via the 2x2 grid ---
        val smooth = smoothnessScore >= SMOOTH_AT
        val high = demandScore >= DEMAND_HIGH_AT
        val quadrant = when {
            smooth && high -> Quadrant.SMOOTH_UNDER_PRESSURE
            smooth && !high -> Quadrant.EASY_SMOOTH
            !smooth && high -> Quadrant.DEMANDING_AND_ROUGH
            else -> Quadrant.ROUGH_ON_EASY_ROAD
        }

        return Result(
            smoothness = smoothness,
            demand = demand,
            efficiency = efficiency,
            demandLevel = level,
            smoothStyle = smooth,
            quadrant = quadrant,
            headline = quadrant.headline,
        )
    }

    private fun efficiencyPillar(
        trip: TripEntity,
        vehicle: FuelEstimator.Vehicle,
        personalAvgL100: Double?,
    ): Pillar? {
        val km = trip.distanceM / 1000.0
        if (km < 0.3) return null
        val avgMovingKmh = if (trip.movingS > 0) trip.distanceM / trip.movingS * 3.6 else 0.0
        val litres = FuelEstimator.litres(km, avgMovingKmh, trip.idleS, vehicle)
        if (litres <= 0.0) return null
        val tripL100 = FuelEstimator.tripL100(km, litres)
        val cost = FuelEstimator.cost(litres, vehicle)
        val combined = FuelEstimator.combinedL100(vehicle)
        // Score the trip economy against the vehicle's rating: at the rating ~85 (good), better → up to 100.
        val score = if (tripL100 > 0) (combined / tripL100 * 85.0).roundToInt().coerceIn(0, 100) else 0
        // The read leads with money + economy, then how it compares to the driver's own norm (or the rating).
        val baseline = personalAvgL100 ?: combined
        val baselineWord = if (personalAvgL100 != null) "your average" else "the rating"
        val read = buildString {
            append(money(cost))
            append(", ")
            append(String.format(Locale.US, "%.1f", tripL100))
            append(" L/100km")
            if (baseline > 0) {
                val pct = (tripL100 - baseline) / baseline * 100.0
                if (abs(pct) >= 2.0) {
                    append(", ")
                    append(abs(pct).roundToInt())
                    append(if (pct > 0) "% over $baselineWord" else "% under $baselineWord")
                } else {
                    append(", on par with $baselineWord")
                }
            }
        }
        return Pillar(score, efficiencyBand(score), read)
    }

    private fun demandRead(trip: TripEntity, level: DemandLevel): String {
        val parts = ArrayList<String>(3)
        val crawlPct = (trip.crawlFraction * 100.0).roundToInt()
        if (crawlPct >= 15) parts += "$crawlPct% crawling"
        if (trip.belowLimitLoad >= 0.25) parts += "well below posted speed"
        val noBreakMin = (trip.longestNoBreakS / 60.0).roundToInt()
        if (noBreakMin >= 30) parts += "$noBreakMin-min stretch without a break"
        if (trip.drawdownCount > 0 && parts.size < 2) {
            parts += "${trip.drawdownCount} forced ${plural(trip.drawdownCount, "slowdown", "slowdowns")}"
        }
        return when {
            parts.isNotEmpty() -> parts.joinToString(", ").replaceFirstChar { it.uppercase() }
            level == DemandLevel.LOW -> "Free-flowing, easy conditions"
            else -> "Moderate traffic conditions"
        }
    }

    private fun smoothnessBand(s: Int): String = when {
        s >= 85 -> "Very smooth"
        s >= 70 -> "Smooth"
        s >= 55 -> "A bit rough"
        else -> "Rough"
    }

    private fun demandBand(s: Int): String = when {
        s < 25 -> "Easy"
        s < 45 -> "Moderate"
        s < 65 -> "Demanding"
        else -> "Very demanding"
    }

    private fun efficiencyBand(s: Int): String = when {
        s >= 80 -> "Efficient"
        s >= 60 -> "Average"
        else -> "Thirsty"
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    private fun money(amount: Double): String = "$" + String.format(Locale.US, "%.2f", max(0.0, amount))
}
