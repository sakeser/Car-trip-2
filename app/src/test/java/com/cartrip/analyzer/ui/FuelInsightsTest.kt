package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelInsightsTest {

    private val v = FuelEstimator.DEFAULT

    private fun drive(km: Double, kmh: Double, idleS: Double = 0.0, topKmh: Double = 80.0) = TripEntity(
        startTime = 0, endTime = 1,
        distanceM = km * 1000.0,
        avgMovingSpeedMps = kmh / 3.6,
        idleS = idleS,
        maxSpeedMps = topKmh / 3.6,
    )

    @Test fun totalsMatchPerTripEstimates() {
        val trips = listOf(drive(10.0, 50.0), drive(20.0, 90.0, idleS = 60.0))
        val s = FuelInsights.summarize(trips, v)
        val expLitres = trips.sumOf { FuelEstimator.litres(it.distanceM / 1000, it.avgMovingSpeedMps * 3.6, it.idleS, v) }
        assertEquals(2, s.drives)
        assertEquals(30.0, s.totalKm, 1e-6)
        assertEquals(expLitres, s.totalLitres, 1e-6)
        assertEquals(expLitres * v.pricePerL, s.totalCost, 1e-6)
        assertEquals(s.totalCost / s.totalKm, s.avgCostPerKm, 1e-9)
        assertEquals(s.totalLitres / s.totalKm * 100.0, s.avgL100, 1e-9)
    }

    @Test fun seriesAreChronologicalAndCumulativeMonotonic() {
        val s = FuelInsights.summarize(listOf(drive(5.0, 40.0), drive(8.0, 60.0), drive(12.0, 100.0)), v)
        assertEquals(3, s.costPerKm.size)
        assertEquals(3, s.cumulativeCost.size)
        // cumulative cost only ever grows
        assertTrue(s.cumulativeCost[0] < s.cumulativeCost[1])
        assertTrue(s.cumulativeCost[1] < s.cumulativeCost[2])
        // last cumulative == total
        assertEquals(s.totalCost.toFloat(), s.cumulativeCost.last(), 1e-3f)
    }

    @Test fun walksAndZeroDistanceExcluded() {
        val trips = listOf(
            drive(10.0, 50.0),                       // a real drive
            drive(2.0, 4.5, topKmh = 5.0),           // a walk (top speed 5 km/h -> non-drive)
            drive(0.0, 0.0, topKmh = 60.0),          // zero distance
        )
        val s = FuelInsights.summarize(trips, v)
        assertEquals(1, s.drives)
        assertEquals(10.0, s.totalKm, 1e-6)
    }

    @Test fun emptyHasNoData() {
        assertFalse(FuelInsights.summarize(emptyList(), v).hasData)
    }

    private fun driveAt(startMs: Long, km: Double = 10.0) = TripEntity(
        startTime = startMs, endTime = startMs + 1,
        distanceM = km * 1000.0, avgMovingSpeedMps = 50.0 / 3.6, maxSpeedMps = 80.0 / 3.6,
    )

    @Test fun spendBucketsByRecency() {
        val now = 100L * 24 * 3600 * 1000
        val day = 24L * 3600 * 1000
        val trips = listOf(
            driveAt(now - 2 * 3600 * 1000),   // today
            driveAt(now - 3 * day),           // within 7d
            driveAt(now - 20 * day),          // within 30d
            driveAt(now - 60 * day),          // older (all-time only)
        )
        val s = FuelInsights.spend(trips, v, now)
        val each = FuelEstimator.cost(FuelEstimator.litres(10.0, 50.0, 0.0, v), v)
        assertEquals(each, s.day, 1e-6)
        assertEquals(2 * each, s.week, 1e-6)
        assertEquals(3 * each, s.month, 1e-6)
        assertEquals(4 * each, s.allTime, 1e-6)
    }

    @Test fun smoothedSeriesOnlyWhenEnoughDrives() {
        val few = (0 until 4).map { drive(10.0, 50.0) }
        assertTrue(FuelInsights.summarize(few, v).costPerKmSmoothed.isEmpty())
        val many = (0 until FuelInsights.SMOOTH_MIN_DRIVES + 2).map { drive(10.0, 50.0) }
        assertTrue(FuelInsights.summarize(many, v).costPerKmSmoothed.isNotEmpty())
    }
}
