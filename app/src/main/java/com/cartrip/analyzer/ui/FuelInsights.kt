package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.data.TripEntity

/**
 * Aggregates per-trip fuel estimates into history + totals for the Insights "Fuel & cost" section: how much
 * you've spent, your cost per km, and how economy trends over time. Pure (uses [FuelEstimator] on stored
 * aggregates) so it is unit-testable and cheap.
 *
 * Note: cost uses the vehicle's **current** price per litre (historical price changes aren't tracked), so a
 * "$/km" trend mostly reflects how efficiently you drove, not pump-price swings.
 */
object FuelInsights {

    /** Below this many drives, a moving-average $/km trend isn't meaningful — show the raw per-drive line. */
    const val SMOOTH_MIN_DRIVES = 10
    private const val SMOOTH_WINDOW = 5

    private const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
    /** Trailing window (in weeks) for smoothing the weekly spend-rate series. */
    private const val WEEKLY_SMOOTH_WEEKS = 4

    data class Summary(
        val drives: Int,
        val totalKm: Double,
        val totalLitres: Double,
        val totalCost: Double,
        val avgCostPerKm: Double,   // total cost / total km
        val avgL100: Double,        // total litres / total km * 100
        val costPerKm: List<Float>,         // $/km, per drive, chronological
        val costPerKmSmoothed: List<Float>, // $/km trailing moving average (empty until enough drives)
        val l100: List<Float>,              // L/100km, per drive
        val weeklySpend: List<Float>,         // $ spent per 7-day window from first drive, oldest first (0 = idle)
        val weeklySpendSmoothed: List<Float>, // weekly spend, trailing-averaged so the rate reads as a trend
    ) {
        val hasData: Boolean get() = drives > 0
    }

    /** Total fuel cost spent over fixed recency buckets (independent of the Insights window selector). */
    data class Spend(val day: Double, val week: Double, val month: Double, val allTime: Double)

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Money spent on gas in the last 1 / 7 / 30 days and since inception, across all driving trips. */
    fun spend(trips: List<TripEntity>, v: FuelEstimator.Vehicle, nowMs: Long): Spend {
        var d = 0.0; var w = 0.0; var m = 0.0; var all = 0.0
        for (t in trips) {
            if (t.distanceM <= 0.0 || TripKind.isLikelyNonDrive(t)) continue
            val cost = FuelEstimator.cost(
                FuelEstimator.litres(t.distanceM / 1000.0, t.avgMovingSpeedMps * 3.6, t.idleS, v), v
            )
            all += cost
            val age = nowMs - t.startTime
            if (age <= DAY_MS) d += cost
            if (age <= 7 * DAY_MS) w += cost
            if (age <= 30 * DAY_MS) m += cost
        }
        return Spend(d, w, m, all)
    }

    /** Trailing moving average of [series] over [window], for the points that have a full window behind them. */
    private fun smooth(series: List<Float>, window: Int): List<Float> {
        if (series.size < window) return emptyList()
        val out = ArrayList<Float>(series.size - window + 1)
        for (i in window - 1 until series.size) {
            var sum = 0f
            for (j in i - window + 1..i) sum += series[j]
            out += sum / window
        }
        return out
    }

    /** Same-length trailing average: point i is the mean of items in (i-window, i]. Smooths without lag-dropping. */
    private fun trailingAvg(series: List<Float>, window: Int): List<Float> {
        if (series.isEmpty()) return emptyList()
        return series.indices.map { i ->
            val from = maxOf(0, i - window + 1)
            var sum = 0f
            for (j in from..i) sum += series[j]
            sum / (i - from + 1)
        }
    }

    /**
     * Spend per 7-day window (anchored on the first drive), oldest first. NB: these are rolling 7-day
     * buckets from the first drive, not Mon-Sun calendar weeks — deliberately timezone/locale-free so the
     * series is deterministic. Windows with no driving are 0, so it's a true rate over time (the derivative
     * of cumulative spend) rather than a running total.
     */
    private fun weeklySpend(drives: List<TripEntity>, costs: List<Double>): List<Float> {
        if (drives.isEmpty()) return emptyList()
        val first = drives.first().startTime
        val lastIdx = ((drives.last().startTime - first) / WEEK_MS).toInt().coerceAtLeast(0)
        val weeks = FloatArray(lastIdx + 1)
        for (i in drives.indices) {
            val idx = ((drives[i].startTime - first) / WEEK_MS).toInt().coerceIn(0, lastIdx)
            weeks[idx] += costs[i].toFloat()
        }
        return weeks.toList()
    }

    /** [trips] should be chronological (oldest first). Non-drives (walks) and zero-distance are excluded. */
    fun summarize(trips: List<TripEntity>, v: FuelEstimator.Vehicle): Summary {
        val drives = trips.filter { it.distanceM > 0.0 && !TripKind.isLikelyNonDrive(it) }
        if (drives.isEmpty()) return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        var totalKm = 0.0; var totalLitres = 0.0; var totalCost = 0.0
        val costPerKm = ArrayList<Float>(drives.size)
        val l100 = ArrayList<Float>(drives.size)
        val costs = ArrayList<Double>(drives.size)
        for (t in drives) {
            val km = t.distanceM / 1000.0
            val litres = FuelEstimator.litres(km, t.avgMovingSpeedMps * 3.6, t.idleS, v)
            val cost = FuelEstimator.cost(litres, v)
            totalKm += km; totalLitres += litres; totalCost += cost
            costPerKm += (cost / km).toFloat()
            l100 += (litres / km * 100.0).toFloat()
            costs += cost
        }
        val weekly = weeklySpend(drives, costs)
        return Summary(
            drives = drives.size,
            totalKm = totalKm,
            totalLitres = totalLitres,
            totalCost = totalCost,
            avgCostPerKm = if (totalKm > 0) totalCost / totalKm else 0.0,
            avgL100 = if (totalKm > 0) totalLitres / totalKm * 100.0 else 0.0,
            costPerKm = costPerKm,
            costPerKmSmoothed = if (drives.size >= SMOOTH_MIN_DRIVES) smooth(costPerKm, SMOOTH_WINDOW) else emptyList(),
            l100 = l100,
            weeklySpend = weekly,
            weeklySpendSmoothed = trailingAvg(weekly, WEEKLY_SMOOTH_WEEKS),
        )
    }
}
