package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects hard braking / acceleration / cornering from the high-rate sensors, independent of the
 * phone's pose, so it can be compared against the GPS-derived detector.
 *
 * Method (orientation-agnostic):
 *  - The averaged gravity vector gives the vehicle's vertical ("down") axis.
 *  - In the horizontal plane perpendicular to gravity, the car's **forward axis** is inferred by
 *    correlating horizontal acceleration with the GPS speed change (you accelerate/brake along
 *    forward). That correlation also yields a confidence (how cleanly forward was found).
 *  - Longitudinal accel (brake/launch) = accelerometer projected onto that forward axis.
 *  - Lateral accel (cornering) = **gyro yaw rate** (rotation about the down axis) × speed.
 *
 * Returns counts + confidence only — this runs alongside the GPS detector and does NOT feed the
 * user-facing score yet; it's for measuring fused-vs-GPS agreement on real drives. Fails soft.
 */
object FusedEventDetector {

    private const val G = 9.80665
    private const val HARD_BRAKE = 0.30 * G
    private const val HARD_ACCEL = 0.30 * G
    private const val HARD_TURN = 0.40 * G
    private const val MOVING_MPS = 8.0 / 3.6
    private const val EVENT_GAP_MS = 1500L
    private const val MIN_GRAVITY = 5.0
    private const val SIGNIFICANT_LONG = 1.0 // m/s^2 of GPS accel worth using to find forward

    data class Result(
        val brakeCount: Int,
        val accelCount: Int,
        val turnCount: Int,
        val confidence: Double
    ) {
        companion object { val EMPTY = Result(0, 0, 0, 0.0) }
    }

    fun detect(motions: List<MotionSample>, points: List<TrackPoint>): Result {
        if (motions.size < 30 || points.size < 3) return Result.EMPTY

        // 1. Average gravity → vehicle "down" axis.
        var sx = 0.0; var sy = 0.0; var sz = 0.0; var gn = 0
        for (m in motions) {
            val gm = sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz)
            if (gm >= MIN_GRAVITY) { sx += m.grx / gm; sy += m.gry / gm; sz += m.grz / gm; gn++ }
        }
        if (gn < 10) return Result.EMPTY
        val dn = sqrt(sx * sx + sy * sy + sz * sz)
        if (dn < 1e-6) return Result.EMPTY
        val dx = sx / dn; val dy = sy / dn; val dz = sz / dn

        // 2. Orthonormal horizontal basis (e1, e2) perpendicular to down.
        val rx: Double; val ry: Double; val rz: Double
        if (abs(dx) < 0.9) { rx = 1.0; ry = 0.0; rz = 0.0 } else { rx = 0.0; ry = 1.0; rz = 0.0 }
        val rd = rx * dx + ry * dy + rz * dz
        var e1x = rx - rd * dx; var e1y = ry - rd * dy; var e1z = rz - rd * dz
        val e1n = sqrt(e1x * e1x + e1y * e1y + e1z * e1z)
        if (e1n < 1e-6) return Result.EMPTY
        e1x /= e1n; e1y /= e1n; e1z /= e1n
        val e2x = dy * e1z - dz * e1y
        val e2y = dz * e1x - dx * e1z
        val e2z = dx * e1y - dy * e1x

        // GPS speed + longitudinal-accel lookups by time.
        val ptT = LongArray(points.size) { points[it].tMs }
        val ptV = DoubleArray(points.size) { points[it].speedKmh / 3.6 }
        fun idxAt(t: Long): Int {
            if (t <= ptT.first()) return 0
            if (t >= ptT.last()) return ptT.lastIndex
            var lo = 0; var hi = ptT.lastIndex
            while (lo < hi) { val mid = (lo + hi) / 2; if (ptT[mid] < t) lo = mid + 1 else hi = mid }
            return if (t - ptT[lo - 1] <= ptT[lo] - t) lo - 1 else lo
        }
        fun speedAt(t: Long): Double = ptV[idxAt(t)]
        fun gpsLongAt(t: Long): Double {
            val i = idxAt(t).coerceIn(1, ptT.lastIndex)
            val dt = (ptT[i] - ptT[i - 1]) / 1000.0
            return if (dt > 0.05) (ptV[i] - ptV[i - 1]) / dt else 0.0
        }

        // 3. Infer forward axis: weighted sum of horizontal accel aligned with GPS accel direction.
        var fU = 0.0; var fV = 0.0; var energy = 0.0
        for (m in motions) {
            val gm = sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz)
            if (gm < MIN_GRAVITY) continue
            val gl = gpsLongAt(m.t)
            if (abs(gl) < SIGNIFICANT_LONG) continue
            val u = m.ax * e1x + m.ay * e1y + m.az * e1z
            val v = m.ax * e2x + m.ay * e2y + m.az * e2z
            val w = if (gl >= 0) abs(gl) else -abs(gl)
            fU += u * w; fV += v * w
            energy += sqrt(u * u + v * v) * abs(gl)
        }
        val fMag = sqrt(fU * fU + fV * fV)
        if (fMag < 1e-6 || energy < 1e-6) return Result.EMPTY
        val fu = fU / fMag; val fv = fV / fMag      // forward unit in (e1, e2)
        val confidence = (fMag / energy).coerceIn(0.0, 1.0)

        // 4. Detect events from forward-projected longitudinal accel + gyro yaw lateral accel.
        var brake = 0; var accel = 0; var turn = 0
        var lastB = Long.MIN_VALUE; var lastA = Long.MIN_VALUE; var lastT = Long.MIN_VALUE
        for (m in motions) {
            val gm = sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz)
            if (gm < MIN_GRAVITY) continue
            if (speedAt(m.t) < MOVING_MPS) continue
            val u = m.ax * e1x + m.ay * e1y + m.az * e1z
            val v = m.ax * e2x + m.ay * e2y + m.az * e2z
            val longAcc = u * fu + v * fv                    // + = forward
            val yawRate = m.gx * dx + m.gy * dy + m.gz * dz  // rad/s about down
            val latAcc = speedAt(m.t) * yawRate
            if (-longAcc >= HARD_BRAKE && m.t - lastB >= EVENT_GAP_MS) { brake++; lastB = m.t }
            if (longAcc >= HARD_ACCEL && m.t - lastA >= EVENT_GAP_MS) { accel++; lastA = m.t }
            if (abs(latAcc) >= HARD_TURN && m.t - lastT >= EVENT_GAP_MS) { turn++; lastT = m.t }
        }
        return Result(brake, accel, turn, confidence)
    }
}
