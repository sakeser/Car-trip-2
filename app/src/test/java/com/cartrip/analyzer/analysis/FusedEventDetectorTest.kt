package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun slowPoints(spanMs: Long, kmh: Double): List<TrackPoint> = listOf(
        TrackPoint(0L, 43.0, -79.0, kmh, 0.0, 0.0),
        TrackPoint(spanMs / 2, 43.0, -79.0, kmh, 0.0, 0.0),
        TrackPoint(spanMs, 43.0, -79.0, kmh, 0.0, 0.0)
    )

    /** A vertical road bump (strong az, moderate horizontal) must not be counted as a brake/accel. */
    @Test fun verticalBumpIsNotALongitudinalEvent() {
        val n = 300
        val ms = (0 until n).map { i ->
            val bump = i in 150..170
            MotionSample(
                tripId = 1, t = i * 20L,
                ax = (if (bump) 0.30 else 0.02) * 9.80665, ay = 0.0,
                az = (if (bump) 0.60 else 0.02) * 9.80665,   // vertical dominates during the bump
                gx = 0.0, gy = 0.0, gz = 0.0, grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        val r = FusedEventDetector.detect(ms, movingPoints(n * 20L))
        assertEquals(0, r.brakeCount + r.accelCount)
    }

    /** A sharp yaw at parking-lot speed is a normal turn, not a swerve. */
    @Test fun lowSpeedSharpYawIsNotASwerve() {
        val n = 200
        val ms = (0 until n).map { i ->
            MotionSample(
                tripId = 1, t = i * 20L, ax = 0.02 * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = if (i in 90..110) 0.6 else 0.0,
                grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        val r = FusedEventDetector.detect(ms, slowPoints(n * 20L, 12.0))
        assertEquals(0, r.turnCount)
    }

    /** The same sharp yaw at real road speed is a genuine cornering force. */
    @Test fun highSpeedSharpYawIsACorner() {
        val n = 200
        val ms = (0 until n).map { i ->
            MotionSample(
                tripId = 1, t = i * 20L, ax = 0.02 * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = if (i in 90..130) 0.4 else 0.0,
                grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        val r = FusedEventDetector.detect(ms, movingPoints(n * 20L))
        assertTrue("turns=${r.turnCount}", r.turnCount >= 1)
    }

    private fun accelPoints(spanMs: Long): List<TrackPoint> = listOf(
        TrackPoint(0L, 43.0, -79.0, 40.0, 0.0, 0.0),
        TrackPoint(spanMs / 2, 43.0, -79.0, 50.0, 0.0, 0.0),
        TrackPoint(spanMs, 43.0, -79.0, 60.0, 0.0, 0.0)
    )

    /**
     * A cornering force must not double-count as a hard acceleration. Field trip 847 logged a 0.47 g
     * "ACCEL" mid-curve because the gyro-yaw dip and the lateral-accel peak landed on different samples,
     * so an *instantaneous* turn-veto leaked. Here a corner (yaw ~0.4) carries a horizontal spike whose
     * yaw momentarily dips; with a confident +slope the old code would emit an ACCEL. The windowed veto
     * attributes it to the turn.
     */
    @Test fun cornerForceNotMiscountedAsAcceleration() {
        val n = 300
        val ms = (0 until n).map { i ->
            val inCorner = i in 100..140
            val spikeDip = i in 119..121      // lateral-accel peak where the yaw briefly drops out
            val yaw = if (spikeDip) 0.05 else if (inCorner) 0.4 else 0.0
            val axG = if (spikeDip) 0.45 else 0.02
            MotionSample(
                tripId = 1, t = i * 20L, ax = axG * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = yaw, grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        val r = FusedEventDetector.detect(ms, accelPoints(n * 20L))
        assertTrue("expected a corner, turns=${r.turnCount}", r.turnCount >= 1)
        assertEquals("corner force leaked as a longitudinal event", 0, r.brakeCount + r.accelCount)
    }

    /**
     * A quick steering input (swerve) shows more lateral g than speed×yaw predicts and often has an
     * ambiguous GPS slope. The old code *guessed* a brake/accel from the fragile forward axis, so every
     * narrated swerve (trips 845/847) fabricated a longitudinal. With clear rotation and an ambiguous
     * slope, the horizontal force is now treated as steering, not a longitudinal event.
     */
    @Test fun swerveWithAmbiguousSlopeIsNotALongitudinal() {
        val n = 300
        val ms = (0 until n).map { i ->
            val turning = i in 100..140
            val spike = i in 118..122
            MotionSample(
                tripId = 1, t = i * 20L,
                ax = (if (spike) 0.40 else 0.02) * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = if (turning) 0.25 else 0.0,   // rotating, but below corner/swerve
                grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        // Constant speed → ambiguous slope; 30 km/h keeps yawLatG under the corner threshold.
        val r = FusedEventDetector.detect(ms, slowPoints(n * 20L, 30.0))
        assertEquals("no corner/swerve should fire", 0, r.turnCount)
        assertEquals("ambiguous spike during steering leaked as a longitudinal", 0, r.brakeCount + r.accelCount)
    }

    /**
     * An event's stored magnitude should be the maneuver PEAK, not the value at the first sample that
     * crossed the threshold. A hard brake/accel keeps building after it first crosses the line (a
     * narrated 0.5 g brake was being stored as 0.28 g).
     */
    @Test fun eventMagnitudeReflectsManeuverPeakNotFirstCrossing() {
        val n = 300
        val ms = (0 until n).map { i ->
            val axG = when {
                i in 100..104 -> 0.26    // first threshold crossing
                i in 105..135 -> 0.50    // true peak, later in the same maneuver
                else -> 0.02
            }
            MotionSample(
                tripId = 1, t = i * 20L, ax = axG * 9.80665, ay = 0.0, az = 0.0,
                gx = 0.0, gy = 0.0, gz = 0.0, grx = 0.0, gry = 0.0, grz = 9.8
            )
        }
        val r = FusedEventDetector.detect(ms, accelPoints(n * 20L))
        val accel = r.events.firstOrNull { it.type == EventType.ACCEL }
        assertNotNull("expected an accel event", accel)
        assertEquals("magnitude should be the maneuver peak", 0.50, accel!!.magnitude / 9.80665, 0.04)
    }
}
