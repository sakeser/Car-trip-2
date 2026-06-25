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
}
