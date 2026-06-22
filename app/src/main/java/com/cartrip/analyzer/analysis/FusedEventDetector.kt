package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rev D sensor-fused event detector — magnitude-first and orientation-agnostic, validated against
 * narrated field drives (trips 778/779).
 *
 * Design (why it replaced the v2.17 forward-axis detector):
 *  - The averaged gravity vector gives a rock-solid vehicle "down" axis. Acceleration is split into
 *    a horizontal magnitude (the driving force) and the yaw rate about down (gyro · down).
 *  - **Detection is magnitude-first**: a horizontal-accel spike is a candidate longitudinal event; a
 *    sustained lateral-g (speed × yaw) is a corner; a yaw-rate reversal is a swerve. We do NOT require
 *    the forward axis to be found — inferring it from 1 Hz GPS only reaches ~0.2 confidence on real
 *    data, and gating on it suppressed every event (that, plus a Long.MIN_VALUE debounce overflow,
 *    is why v2.17 reported 0/0/0).
 *  - **Brake vs accel is classified from the GPS speed slope** (reliable sign), not the fragile
 *    inferred forward axis. When the slope is ambiguous (|slope| < SLOPE_MIN) the event is still
 *    emitted but flagged low-confidence rather than guessed.
 *
 * Counts are not fed to the score yet; this measures sensor-vs-GPS agreement. Fails soft to EMPTY.
 */
object FusedEventDetector {

    private const val G = 9.80665
    private const val HARD_LONG_G = 0.25          // horizontal-accel spike → brake/accel candidate
    private const val HARD_CORNER_G = 0.27        // sustained lateral-g (speed × yaw) → corner
    private const val SWERVE_YAW = 0.45           // rad/s, a sharp yaw flick (not a sustained corner)
    private const val SLOPE_MIN = 0.5             // m/s^2 GPS slope needed to call brake vs accel
    private const val MOVING_MPS = 8.0 / 3.6
    private const val LONG_GAP_MS = 2500L         // debounce: one brake/accel maneuver
    private const val TURN_GAP_MS = 3000L         // debounce: sustained turns fire once
    private const val MIN_GRAVITY = 5.0

    data class Result(
        val events: List<DriveEvent>,
        val brakeCount: Int,
        val accelCount: Int,
        val turnCount: Int,            // corners + swerves
        val confidence: Double,        // forward-axis inference confidence (diagnostic only)
        val maxHorizG: Double          // true peak horizontal accel (g), for the spike peak-G metric
    ) {
        companion object { val EMPTY = Result(emptyList(), 0, 0, 0, 0.0, 0.0) }
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

        // GPS speed + longitudinal-accel lookups by time (binary search, nearest).
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
        fun gpsSlope(t: Long): Double {
            val i = idxAt(t).coerceIn(1, ptT.lastIndex)
            val dt = (ptT[i] - ptT[i - 1]) / 1000.0
            return if (dt > 0.05) (ptV[i] - ptV[i - 1]) / dt else 0.0
        }

        // 2. Forward-axis confidence — diagnostic only (NOT used to gate events). Orthonormal basis
        // perpendicular to down, then correlate horizontal accel with GPS slope sign.
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
        var fU = 0.0; var fV = 0.0; var energy = 0.0
        for (m in motions) {
            if (sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz) < MIN_GRAVITY) continue
            val gl = gpsSlope(m.t)
            if (abs(gl) < 1.0) continue
            val u = m.ax * e1x + m.ay * e1y + m.az * e1z
            val v = m.ax * e2x + m.ay * e2y + m.az * e2z
            fU += u * gl; fV += v * gl; energy += sqrt(u * u + v * v) * abs(gl)
        }
        val fMag = sqrt(fU * fU + fV * fV)
        val confidence = if (energy > 1e-6) (fMag / energy).coerceIn(0.0, 1.0) else 0.0

        // 3. Magnitude-first multi-channel detection.
        // Event magnitudes are stored in m/s^2 (× G), matching GPS/pothole events so the two lists
        // can share the map / timeline / export / formatters without a unit mismatch.
        val events = ArrayList<DriveEvent>()
        var brake = 0; var accel = 0; var turn = 0
        var maxHorizG = 0.0
        var lastLong = Long.MIN_VALUE; var lastTurn = Long.MIN_VALUE; var lastSwerve = Long.MIN_VALUE
        for (m in motions) {
            if (sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz) < MIN_GRAVITY) continue
            val sp = speedAt(m.t)
            if (sp < MOVING_MPS) continue
            val u = m.ax * e1x + m.ay * e1y + m.az * e1z
            val v = m.ax * e2x + m.ay * e2y + m.az * e2z
            val hMagG = sqrt(u * u + v * v) / G
            if (hMagG > maxHorizG) maxHorizG = hMagG
            val yaw = m.gx * dx + m.gy * dy + m.gz * dz
            val yawLatG = sp * abs(yaw) / G

            // Corner: sustained lateral-g (takes precedence over a swerve), one event per turn.
            if (yawLatG >= HARD_CORNER_G && debounced(m.t, lastTurn, TURN_GAP_MS)) {
                events.add(DriveEvent(m.t, EventType.CORNER, yawLatG * G, "fused", 0.8))
                turn++; lastTurn = m.t
            }
            // Swerve: a sharp yaw flick that isn't a sustained corner (low lateral-g, usually low speed).
            else if (abs(yaw) >= SWERVE_YAW && yawLatG < HARD_CORNER_G && debounced(m.t, lastSwerve, TURN_GAP_MS)) {
                events.add(DriveEvent(m.t, EventType.SWERVE, yawLatG * G, "fused", 0.7))
                turn++; lastSwerve = m.t; lastTurn = m.t
            }
            // Longitudinal: horizontal spike not dominated by turning → brake/accel via GPS slope.
            if (hMagG >= HARD_LONG_G && yawLatG < 0.6 * hMagG && debounced(m.t, lastLong, LONG_GAP_MS)) {
                val gl = gpsSlope(m.t)
                val magMps2 = hMagG * G
                when {
                    gl <= -SLOPE_MIN -> { events.add(DriveEvent(m.t, EventType.BRAKE, magMps2, "fused", 0.9)); brake++ }
                    gl >= SLOPE_MIN -> { events.add(DriveEvent(m.t, EventType.ACCEL, magMps2, "fused", 0.9)); accel++ }
                    else -> {
                        // Ambiguous GPS slope — emit, but low confidence; lean on the forward-axis sign.
                        val lf = u * (fU / (fMag + 1e-9)) + v * (fV / (fMag + 1e-9))
                        if (lf < 0) { events.add(DriveEvent(m.t, EventType.BRAKE, magMps2, "fused", 0.4)); brake++ }
                        else { events.add(DriveEvent(m.t, EventType.ACCEL, magMps2, "fused", 0.4)); accel++ }
                    }
                }
                lastLong = m.t
            }
        }
        return Result(events, brake, accel, turn, confidence, maxHorizG)
    }

    /** Overflow-safe debounce: the old `t - Long.MIN_VALUE >= gap` overflowed and blocked every event. */
    private fun debounced(t: Long, last: Long, gapMs: Long): Boolean =
        last == Long.MIN_VALUE || t - last >= gapMs
}
