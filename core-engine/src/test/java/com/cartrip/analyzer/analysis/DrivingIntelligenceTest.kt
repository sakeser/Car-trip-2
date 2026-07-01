package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rev CX — verifies the three-pillar roll-up and, most importantly, the 2x2 style x demand quadrant that drives
 * the conditional headline. The point of the model is that smoothly-handled high-demand trips do NOT read as
 * bad driving, and rough low-demand trips ARE flagged as driver-actionable.
 */
class DrivingIntelligenceTest {

    private val vehicle = FuelEstimator.DEFAULT

    /** Clean inputs (no hard events, low-rate motion so GPS path), tunable crawl/duration for demand. */
    private fun cleanTrip(
        distanceM: Double, durationS: Double,
        crawl: Double = 0.0, belowLimit: Double = 0.0, longestNoBreakS: Double = 120.0,
    ) = TripEntity(
        startTime = 0L, endTime = durationS.toLong() * 1000,
        distanceM = distanceM, durationS = durationS, movingS = durationS - 40, idleS = 40.0,
        motionSampleCount = 0, maxHorizGForce = 0.0,
        crawlFraction = crawl, belowLimitLoad = belowLimit, longestNoBreakS = longestNoBreakS,
    )

    /** Dense-motion trip with many hard events (rough style), tunable crawl/duration for demand. */
    private fun roughTrip(
        distanceM: Double, durationS: Double, events: Int,
        crawl: Double = 0.0, belowLimit: Double = 0.0, longestNoBreakS: Double = 120.0,
    ) = TripEntity(
        startTime = 0L, endTime = durationS.toLong() * 1000,
        distanceM = distanceM, durationS = durationS, movingS = durationS - 40, idleS = 40.0,
        motionSampleCount = (durationS * 45).toInt(), maxHorizGForce = 0.30,
        motionBrakeCount = events, motionAccelCount = events, motionTurnCount = events,
        crawlFraction = crawl, belowLimitLoad = belowLimit, longestNoBreakS = longestNoBreakS,
    )

    @Test fun smoothLowDemand_isEasySmooth() {
        val r = DrivingIntelligence.from(cleanTrip(10_000.0, 720.0), vehicle)!!
        assertTrue("smooth", r.smoothStyle)
        assertEquals(DrivingIntelligence.DemandLevel.LOW, r.demandLevel)
        assertEquals(DrivingIntelligence.Quadrant.EASY_SMOOTH, r.quadrant)
        assertEquals(r.quadrant.headline, r.headline)
    }

    @Test fun smoothHighDemand_isSmoothUnderPressure() {
        // Heavy crawl + long no-break stretch, but clean inputs: the trip-1189 case.
        val r = DrivingIntelligence.from(
            cleanTrip(20_000.0, 2400.0, crawl = 0.8, belowLimit = 0.8, longestNoBreakS = 2400.0), vehicle
        )!!
        assertTrue("smooth handling", r.smoothStyle)
        assertTrue("high demand (>=45), was ${r.demand.score}", r.demand.score >= DrivingIntelligence.DEMAND_HIGH_AT)
        assertEquals(DrivingIntelligence.Quadrant.SMOOTH_UNDER_PRESSURE, r.quadrant)
    }

    @Test fun roughLowDemand_isRoughOnEasyRoad() {
        val r = DrivingIntelligence.from(roughTrip(5_000.0, 600.0, events = 20), vehicle)!!
        assertTrue("rough style, smoothness was ${r.smoothness.score}", !r.smoothStyle)
        assertTrue("not high demand, was ${r.demand.score}", r.demand.score < DrivingIntelligence.DEMAND_HIGH_AT)
        assertEquals(DrivingIntelligence.Quadrant.ROUGH_ON_EASY_ROAD, r.quadrant)
    }

    @Test fun roughHighDemand_isDemandingAndRough() {
        val r = DrivingIntelligence.from(
            roughTrip(20_000.0, 2400.0, events = 60, crawl = 0.8, belowLimit = 0.8, longestNoBreakS = 2400.0),
            vehicle
        )!!
        assertTrue("rough style, smoothness was ${r.smoothness.score}", !r.smoothStyle)
        assertTrue("high demand, was ${r.demand.score}", r.demand.score >= DrivingIntelligence.DEMAND_HIGH_AT)
        assertEquals(DrivingIntelligence.Quadrant.DEMANDING_AND_ROUGH, r.quadrant)
    }

    @Test fun demandPillarEqualsStressScore() {
        val trip = cleanTrip(20_000.0, 2400.0, crawl = 0.8, belowLimit = 0.8, longestNoBreakS = 2400.0)
        val r = DrivingIntelligence.from(trip, vehicle)!!
        assertEquals(StressScore.from(trip)!!.score, r.demand.score)
    }

    @Test fun driveHasEfficiencyPillar() {
        val r = DrivingIntelligence.from(cleanTrip(10_000.0, 720.0), vehicle)!!
        assertNotNull("a real drive should have an efficiency outcome", r.efficiency)
        assertTrue(r.efficiency!!.read.startsWith("$"))
    }

    @Test fun tooShortTripIsNotScorable() {
        // < 0.3 km → StressScore returns null → no Driving Intelligence.
        assertNull(DrivingIntelligence.from(cleanTrip(100.0, 120.0), vehicle))
    }
}
