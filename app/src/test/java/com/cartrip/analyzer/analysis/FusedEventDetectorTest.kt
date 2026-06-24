package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedEventDetectorTest {

    // Gravity straight "down" (grz), accel along device-x → pure horizontal forcing, no yaw.
    private fun motions(n: Int, axG: (Int) -> Double): List<MotionSample> =
        (0 until n).map { i ->
            MotionSample(
                tripId = 1, t = i * 20L,
                ax = axG(i) * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = 0.0,
                grx = 0.0, gry = 0.0, grz = 9.8
            )
        }

    private fun movingPoints(spanMs: Long): List<TrackPoint> = listOf(
        TrackPoint(0L, 43.0, -79.0, 50.0, 0.0, 0.0),
        TrackPoint(spanMs / 2, 43.0, -79.0, 50.0, 0.0, 0.0),
        TrackPoint(spanMs, 43.0, -79.0, 50.0, 0.0, 0.0)
    )

    /** A single 1.5 g handling spike on an otherwise-calm drive must NOT become the peak. */
    @Test fun peakHorizontalGRejectsLoneOutlier() {
        val n = 400
        val ms = motions(n) { i -> if (i == 200) 1.5 else 0.1 }
        val r = FusedEventDetector.detect(ms, movingPoints(n * 20L))
        assertTrue("peak was ${r.maxHorizG}", r.maxHorizG in 0.05..0.25)
    }

    /** A genuinely sustained hard maneuver is still reported as a high peak. */
    @Test fun peakHorizontalGKeepsSustainedManeuver() {
        val n = 400
        val ms = motions(n) { 0.5 }
        val r = FusedEventDetector.detect(ms, movingPoints(n * 20L))
        assertTrue("peak was ${r.maxHorizG}", r.maxHorizG in 0.45..0.55)
    }
}
