package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StressScoreTest {

    private fun trip(
        km: Double,
        durationS: Double,
        topKmh: Double = 90.0,
        drawdownCount: Int = 0,
        drawdownSeverity: Double = 0.0,
        motionEvents: Int = 0,
        speedingSeverity: Double = 0.0,
        freeFlowS: Double = 0.0,
        userIsDrive: Boolean? = null,
    ) = TripEntity(
        startTime = 0L, endTime = 1L,
        distanceM = km * 1000.0,
        durationS = durationS,
        maxSpeedMps = topKmh / 3.6,
        drawdownCount = drawdownCount,
        drawdownSeverity = drawdownSeverity,
        motionBrakeCount = motionEvents,
        speedingSeverity = speedingSeverity,
        googleEtaFreeFlowS = freeFlowS,
        userIsDrive = userIsDrive,
    )

    @Test fun calmShortDriveIsLowStress() {
        val r = StressScore.from(trip(km = 4.0, durationS = 600.0, freeFlowS = 580.0))!!
        assertTrue("expected calm, got ${r.score}", r.score < 25)
    }

    @Test fun heavyStopAndGoIsHighStress() {
        val r = StressScore.from(
            trip(km = 49.0, durationS = 3000.0, drawdownCount = 17, drawdownSeverity = 83771.0,
                motionEvents = 10, speedingSeverity = 50.0, freeFlowS = 2940.0)
        )!!
        assertTrue("expected high stress, got ${r.score}", r.score >= 60)
    }

    @Test fun congestedShortTripScoresAboveFreeFlowingOne() {
        val congested = StressScore.from(trip(km = 5.0, durationS = 1200.0, freeFlowS = 600.0, motionEvents = 6))!!
        val free = StressScore.from(trip(km = 5.0, durationS = 600.0, freeFlowS = 580.0))!!
        assertTrue(congested.score > free.score)
    }

    @Test fun walkReturnsNull() {
        assertNull(StressScore.from(trip(km = 2.0, durationS = 1500.0, topKmh = 5.0)))
        // explicit override also excluded
        assertNull(StressScore.from(trip(km = 5.0, durationS = 600.0, topKmh = 90.0, userIsDrive = false)))
    }

    @Test fun bandsCoverRange() {
        assertTrue(StressScore.band(10) == "Calm")
        assertTrue(StressScore.band(35) == "Moderate")
        assertTrue(StressScore.band(55) == "Busy")
        assertTrue(StressScore.band(80) == "High stress")
    }
}
