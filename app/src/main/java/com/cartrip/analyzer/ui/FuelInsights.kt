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

    data class Summary(
        val drives: Int,
        val totalKm: Double,
        val totalLitres: Double,
        val totalCost: Double,
        val avgCostPerKm: Double,   // total cost / total km
        val avgL100: Double,        // total litres / total km * 100
        val costPerKm: List<Float>,       // $/km, per drive, chronological
        val l100: List<Float>,            // L/100km, per drive
        val cumulativeCost: List<Float>,  // running total $ across drives
    ) {
        val hasData: Boolean get() = drives > 0
    }

    /** [trips] should be chronological (oldest first). Non-drives (walks) and zero-distance are excluded. */
    fun summarize(trips: List<TripEntity>, v: FuelEstimator.Vehicle): Summary {
        val drives = trips.filter { it.distanceM > 0.0 && !TripKind.isLikelyNonDrive(it) }
        if (drives.isEmpty()) return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), emptyList(), emptyList())

        var totalKm = 0.0; var totalLitres = 0.0; var totalCost = 0.0
        val costPerKm = ArrayList<Float>(drives.size)
        val l100 = ArrayList<Float>(drives.size)
        val cumulative = ArrayList<Float>(drives.size)
        for (t in drives) {
            val km = t.distanceM / 1000.0
            val litres = FuelEstimator.litres(km, t.avgMovingSpeedMps * 3.6, t.idleS, v)
            val cost = FuelEstimator.cost(litres, v)
            totalKm += km; totalLitres += litres; totalCost += cost
            costPerKm += (cost / km).toFloat()
            l100 += (litres / km * 100.0).toFloat()
            cumulative += totalCost.toFloat()
        }
        return Summary(
            drives = drives.size,
            totalKm = totalKm,
            totalLitres = totalLitres,
            totalCost = totalCost,
            avgCostPerKm = if (totalKm > 0) totalCost / totalKm else 0.0,
            avgL100 = if (totalKm > 0) totalLitres / totalKm * 100.0 else 0.0,
            costPerKm = costPerKm,
            l100 = l100,
            cumulativeCost = cumulative,
        )
    }
}
