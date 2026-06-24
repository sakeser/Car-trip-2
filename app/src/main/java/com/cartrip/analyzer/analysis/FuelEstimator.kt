package com.cartrip.analyzer.analysis

import kotlin.math.max

/**
 * Estimates fuel use and cost for a trip from its stored aggregates (distance, average moving speed,
 * idle time) plus a [Vehicle] profile — cheap enough for the trip list and Insights (no per-point load).
 *
 * The model is intentionally simple and **calibratable**: a speed-dependent L/100km curve anchored on
 * the vehicle's city/highway ratings, plus an idle burn, all scaled by a [Vehicle.calibration] factor.
 * Real cars report actual distance / fuel / L-100km per trip; feeding those back (adjust the ratings or
 * the calibration factor) is how this converges. Seeded for a 2023 Hyundai Tucson 2.5L AWD
 * (~10.2 city / 8.4 hwy L/100km, NRCan/EPA) at a GTA fuel price; everything is user-editable.
 */
object FuelEstimator {

    /** Litres/hour a warm engine burns at idle (~0.9 L/h for a 2.5 L) → L/sec. */
    private const val IDLE_L_PER_S = 0.9 / 3600.0

    data class Vehicle(
        val year: Int,
        val make: String,
        val model: String,
        val cityL100: Double,
        val hwyL100: Double,
        val pricePerL: Double,
        val calibration: Double = 1.0,
        val fuelType: String = "Regular",
    ) {
        val label: String get() = "$year $make $model".trim()
    }

    /** NRCan-style 55/45 city/highway blend of the rated economy (no calibration applied). */
    fun combinedL100(v: Vehicle): Double = v.cityL100 * 0.55 + v.hwyL100 * 0.45

    /**
     * Rated L/100km at a steady [speedKmh], interpolated between the city and highway anchors and
     * U-shaped: worst when crawling, best in the ~35–95 km/h band, a mild aero penalty above ~110.
     * No calibration here — that is applied once in [litres].
     */
    fun l100AtSpeed(speedKmh: Double, v: Vehicle): Double {
        val city = v.cityL100
        val hwy = v.hwyL100
        return when {
            speedKmh < 5.0 -> city * 1.35                                   // stop-and-go crawl
            speedKmh <= 35.0 -> city
            speedKmh >= 95.0 -> hwy + max(0.0, speedKmh - 110.0) * 0.04 * hwy // aero drag past ~110
            else -> city + (hwy - city) * ((speedKmh - 35.0) / 60.0)        // interpolate 35→95
        }
    }

    /** Estimated litres for a trip from its stored aggregates. */
    fun litres(distanceKm: Double, avgMovingSpeedKmh: Double, idleSeconds: Double, v: Vehicle): Double {
        if (distanceKm <= 0.0 && idleSeconds <= 0.0) return 0.0
        val moving = max(0.0, distanceKm) * l100AtSpeed(avgMovingSpeedKmh, v) / 100.0
        val idle = max(0.0, idleSeconds) * IDLE_L_PER_S
        return (moving + idle) * v.calibration
    }

    fun cost(litres: Double, v: Vehicle): Double = litres * v.pricePerL

    /** Effective trip economy (L/100km) implied by the estimate, idle included. */
    fun tripL100(distanceKm: Double, litres: Double): Double =
        if (distanceKm > 0.0) litres / distanceKm * 100.0 else 0.0

    /** Default seed: a 2023 Hyundai Tucson 2.5L AWD at a representative GTA regular price. */
    val DEFAULT = Vehicle(
        year = 2023, make = "Hyundai", model = "Tucson",
        cityL100 = 10.2, hwyL100 = 8.4, pricePerL = 1.84,
    )
}
