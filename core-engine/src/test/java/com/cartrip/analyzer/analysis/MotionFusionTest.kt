package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionFusionTest {

    private fun movingPoints(durMs: Long) = listOf(
        TrackPoint(0L, 43.0, -79.0, 50.0, 0.0, 0.0),
        TrackPoint(durMs / 2, 43.0, -79.0, 50.0, 0.0, 0.0),
        TrackPoint(durMs, 43.0, -79.0, 50.0, 0.0, 0.0)
    )

    // 50 Hz samples; gravity down on z. `azAt` sets the vertical accel (m/s^2) per sample.
    private fun motions(n: Int, azAt: (Int, Long) -> Double): List<MotionSample> =
        (0 until n).map { i ->
            val t = i * 20L
            MotionSample(
                tripId = 1, t = t, ax = 0.0, ay = 0.0, az = azAt(i, t),
                gx = 0.0, gy = 0.0, gz = 0.0, grx = 0.0, gry = 0.0, grz = 9.8
            )
        }

    /** A 2 s patch of vertical vibration while moving = one rough stretch with a positive score. */
    @Test fun detectsSustainedRoughStretch() {
        val ms = motions(250) { i, t ->
            if (t in 1000..3000) (if (i % 2 == 0) 2.5 else -2.5) else 0.02
        }
        val r = MotionFusion.analyze(ms, movingPoints(5000))
        assertTrue("stretches=${r.roughStretchCount}", r.roughStretchCount >= 1)
        assertTrue("bumpy=${r.bumpyScore}", r.bumpyScore > 0.0)
    }

    /** A smooth road produces no stretches and a zero score. */
    @Test fun smoothRoadHasNoStretches() {
        val ms = motions(250) { _, _ -> 0.02 }
        val r = MotionFusion.analyze(ms, movingPoints(5000))
        assertEquals(0, r.roughStretchCount)
        assertEquals(0.0, r.bumpyScore, 1e-9)
    }

    private fun pointsAt(durMs: Long, kmh: Double) = listOf(
        TrackPoint(0L, 43.0, -79.0, kmh, 0.0, 0.0),
        TrackPoint(durMs / 2, 43.0, -79.0, kmh, 0.0, 0.0),
        TrackPoint(durMs, 43.0, -79.0, kmh, 0.0, 0.0)
    )

    /**
     * The same 0.40 g vertical jolt is a pothole at city speed but just expansion-joint/texture buzz at
     * highway speed — field trip 1126 logged 34 such "potholes" on the highway. Threshold rises with speed.
     */
    @Test fun potholeThresholdRisesWithSpeed() {
        val ms = motions(250) { _, t -> if (t == 2000L) 3.9 else 0.02 }   // a lone 0.40 g jolt
        assertTrue("city jolt is a pothole", MotionFusion.analyze(ms, pointsAt(5000, 35.0)).potholeCount >= 1)
        assertEquals("highway buzz is not a pothole", 0, MotionFusion.analyze(ms, pointsAt(5000, 110.0)).potholeCount)
    }

    // --- Harsh stops (Rev AH recalibration) ---

    // 50 Hz; horizontal braking force set via ax (gravity on z, so horizontal accel = |ax|).
    private fun brakingMotions(durMs: Long, axAt: (Long) -> Double): List<MotionSample> =
        (0 until (durMs / 20L).toInt()).map { i ->
            val t = i * 20L
            MotionSample(
                tripId = 1, t = t, ax = axAt(t), ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = 0.0, grx = 0.0, gry = 0.0, grz = 9.8
            )
        }

    // A decel ramp to a full stop at t=4000. The sample just before crossing < 3 km/h is 5 km/h (not
    // >= 8), so the OLD gate ("previous GPS sample >= MOVING") never saw this stop — the core 1 Hz bug.
    private fun decelToStop() = listOf(
        TrackPoint(0L, 43.0, -79.0, 20.0, 0.0, 0.0),
        TrackPoint(1000L, 43.0, -79.0, 15.0, 0.0, 0.0),
        TrackPoint(2000L, 43.0, -79.0, 8.0, 0.0, 0.0),
        TrackPoint(3000L, 43.0, -79.0, 5.0, 0.0, 0.0),
        TrackPoint(4000L, 43.0, -79.0, 2.0, 0.0, 0.0),
        TrackPoint(5000L, 43.0, -79.0, 0.0, 0.0, 0.0)
    )

    /** A firm braking force on a ramped stop is harsh — even though no GPS sample jumps straight from
     *  >= 8 km/h to < 3 km/h (the old detector missed every such stop at 1 Hz). */
    @Test fun hardBrakingRampToStopIsHarsh() {
        val ms = brakingMotions(5000) { t -> if (t in 2000..4000) 4.0 else 0.2 }
        assertEquals(1, MotionFusion.analyze(ms, decelToStop()).harshStopCount)
    }

    /** The same ramped stop with a gentle braking force is not harsh (peak decel below threshold). */
    @Test fun gentleRampToStopIsNotHarsh() {
        val ms = brakingMotions(5000) { t -> if (t in 2000..4000) 1.0 else 0.2 }
        assertEquals(0, MotionFusion.analyze(ms, decelToStop()).harshStopCount)
    }

    /** A slow crawl that dips below 3 km/h without ever reaching MOVING speed is not a "stop" at all,
     *  so even a hard horizontal force there is not counted (guards against parking-lot creep). */
    @Test fun crawlBelowMovingSpeedIsNotAStop() {
        val crawl = listOf(
            TrackPoint(0L, 43.0, -79.0, 5.0, 0.0, 0.0),
            TrackPoint(1000L, 43.0, -79.0, 4.0, 0.0, 0.0),
            TrackPoint(2000L, 43.0, -79.0, 3.0, 0.0, 0.0),
            TrackPoint(3000L, 43.0, -79.0, 2.0, 0.0, 0.0),
            TrackPoint(4000L, 43.0, -79.0, 1.0, 0.0, 0.0),
            TrackPoint(5000L, 43.0, -79.0, 0.0, 0.0, 0.0)
        )
        val ms = brakingMotions(5000) { _ -> 4.0 }
        assertEquals(0, MotionFusion.analyze(ms, crawl).harshStopCount)
    }
}
