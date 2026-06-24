package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimatorTest {

    private val v = FuelEstimator.DEFAULT  // 10.2 city / 8.4 hwy, $1.84/L

    /** A steady-highway trip should use close to the highway rating, not the city rating. */
    @Test fun highwayTripUsesHighwayEconomy() {
        val km = 100.0
        val litres = FuelEstimator.litres(km, avgMovingSpeedKmh = 100.0, idleSeconds = 0.0, v)
        // ~8.4 L/100km over 100 km ≈ 8.4 L
        assertEquals(8.4, litres, 0.3)
    }

    /** A slow city trip should use the (worse) city economy. */
    @Test fun cityTripUsesCityEconomy() {
        val litres = FuelEstimator.litres(100.0, avgMovingSpeedKmh = 25.0, idleSeconds = 0.0, v)
        assertEquals(10.2, litres, 0.3)
        // and city must cost more fuel than highway for the same distance
        val hwy = FuelEstimator.litres(100.0, avgMovingSpeedKmh = 100.0, idleSeconds = 0.0, v)
        assertTrue("city ($litres) should exceed hwy ($hwy)", litres > hwy)
    }

    /** Idle time burns fuel even with no distance, and cost tracks the per-litre price. */
    @Test fun idleBurnsFuelAndCostScales() {
        val litres = FuelEstimator.litres(distanceKm = 0.0, avgMovingSpeedKmh = 0.0, idleSeconds = 600.0, v)
        // 0.9 L/h * (600/3600) h = 0.15 L
        assertEquals(0.15, litres, 0.01)
        assertEquals(0.15 * 1.84, FuelEstimator.cost(litres, v), 0.001)
    }

    /** The calibration factor scales the whole estimate (how real car-reported economy feeds back). */
    @Test fun calibrationScalesEstimate() {
        val base = FuelEstimator.litres(50.0, 60.0, 60.0, v)
        val hot = FuelEstimator.litres(50.0, 60.0, 60.0, v.copy(calibration = 1.2))
        assertEquals(base * 1.2, hot, 1e-6)
    }

    @Test fun tripL100AndCombinedAreSane() {
        val litres = FuelEstimator.litres(50.0, 70.0, 30.0, v)
        val l100 = FuelEstimator.tripL100(50.0, litres)
        assertTrue("trip L/100 ($l100) should sit near the rated band", l100 in 7.5..11.0)
        assertEquals(9.39, FuelEstimator.combinedL100(v), 0.05)  // 10.2*.55 + 8.4*.45
    }
}
