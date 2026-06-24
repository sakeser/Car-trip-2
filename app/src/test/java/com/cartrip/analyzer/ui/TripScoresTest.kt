package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripScoresTest {

    /** A trip with dense motion data (so the fused detector is trusted). */
    private fun fusedTrip(
        distanceM: Double,
        brake: Int = 0, accel: Int = 0, turn: Int = 0,
        maxHorizG: Double = 0.25,
    ) = TripEntity(
        startTime = 0L, endTime = 600_000L,
        distanceM = distanceM, durationS = 600.0, movingS = 580.0, idleS = 20.0,
        motionSampleCount = 27_000,          // 45 Hz over 600 s → full fused trust
        maxHorizGForce = maxHorizG,
        motionBrakeCount = brake, motionAccelCount = accel, motionTurnCount = turn,
    )

    /** On dense-motion trips, the fused hard-event rate now moves Safety (1 Hz GPS exposure is ~0). */
    @Test fun fusedHardEventsLowerSafetyThanCalmDrive() {
        val calm = TripScores.from(fusedTrip(5000.0, brake = 0, accel = 1, turn = 2)).safety
        val aggressive = TripScores.from(fusedTrip(5000.0, brake = 4, accel = 6, turn = 4)).safety
        assertTrue("calm should stay high, was $calm", calm >= 97)
        assertTrue("aggressive ($aggressive) should be well below calm ($calm)", aggressive <= calm - 6)
    }

    /** The fused safety term is capped — a single rough stretch can't zero an otherwise-fine drive. */
    @Test fun fusedHardPenaltyIsCapped() {
        val s = TripScores.from(fusedTrip(5000.0, brake = 100, accel = 100, turn = 100)).safety
        assertTrue("capped safety floor, was $s", s in 68..76)
    }

    /** Old / low-rate trips (no usable motion data) fall back to the GPS exposure path unchanged. */
    @Test fun lowMotionTripFallsBackToGpsExposure() {
        val trip = TripEntity(
            startTime = 0L, endTime = 600_000L,
            distanceM = 5000.0, durationS = 600.0, movingS = 580.0,
            motionSampleCount = 0,               // no fused trust → GPS path
            maxHorizGForce = 0.0,
            hardBrakePct = 0.02,                 // 2% of moving time braking hard → -10
            motionBrakeCount = 99, motionAccelCount = 99, motionTurnCount = 99, // must be ignored
        )
        assertEquals(90, TripScores.from(trip).safety)
    }
}
