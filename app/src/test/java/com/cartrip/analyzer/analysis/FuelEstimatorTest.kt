package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimatorTest {

    private val v = FuelEstimator.DEFAULT  // Tucson Hybrid: 6.4 city / 6.6 hwy, $1.84/L

    /** A steady-highway trip should use close to the highway rating, not the city rating. */
    @Test fun highwayTripUsesHighwayEconomy() {
        val km = 100.0
        val litres = FuelEstimator.litres(km, avgMovingSpeedKmh = 100.0, idleSeconds = 0.0, v)
        // ~6.6 L/100km over 100 km ≈ 6.6 L
        assertEquals(6.6, litres, 0.3)
    }

    /** A slow city trip should use the (worse) city economy. */
    @Test fun cityTripUsesCityEconomy() {
        val litres = FuelEstimator.litres(100.0, avgMovingSpeedKmh = 25.0, idleSeconds = 0.0, v)
        assertEquals(6.4, litres, 0.3)
        // The speed curve interpolates between the city and highway anchors (don't assume which is
        // larger — a hybrid's city economy is actually better than its highway economy).
        val hwy = FuelEstimator.litres(100.0, avgMovingSpeedKmh = 100.0, idleSeconds = 0.0, v)
        val mid = FuelEstimator.litres(100.0, avgMovingSpeedKmh = 65.0, idleSeconds = 0.0, v)
        assertTrue("mid ($mid) should sit between city ($litres) and hwy ($hwy)",
            mid in minOf(litres, hwy)..maxOf(litres, hwy))
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
        assertTrue("trip L/100 ($l100) should sit near the rated band", l100 in 5.5..7.5)
        assertEquals(6.49, FuelEstimator.combinedL100(v), 0.05)  // 6.4*.55 + 6.6*.45
    }
}
