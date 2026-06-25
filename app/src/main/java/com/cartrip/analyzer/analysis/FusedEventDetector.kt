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
 *    the forward axis to be found — inferring it from low-rate GPS only reaches weak confidence on real
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
    private const val HARD_CORNER_G = 0.32        // sustained lateral-g (speed × yaw) → corner
    private const val SWERVE_YAW = 0.45           // rad/s, a sharp yaw flick (not a sustained corner)
    // Below this speed a sharp yaw is just a normal tight/parking-lot turn, not a "swerve" — gating
    // on it stops the detector over-labeling routine low-speed turns (the 36-vs-14 swerve/corner skew).
    private const val SWERVE_MIN_SPEED_MPS = 25.0 / 3.6
    // A candidate brake/accel whose acceleration is more vertical than horizontal is a road bump, not
    // a driving force — veto it so potholes/expansion joints don't fabricate longitudinal events.
    private const val BUMP_VERT_DOMINANCE = 1.0
    private const val SWERVE_REVERSAL_MIN_SPEED_MPS = 20.0 / 3.6
    private const val SWERVE_REVERSAL_YAW = 0.18  // lower yaw allowed when right/left reversal is clear
    private const val SWERVE_REVERSAL_SWING = 0.45
    private const val SWERVE_REVERSAL_WINDOW_MS = 5_000L
    private const val SWERVE_REVERSAL_MIN_SEP_MS = 250L
    private const val SWERVE_REVERSAL_MAX_SEP_MS = 4_500L
    private const val SLOPE_MIN = 0.5             // m/s^2 GPS slope needed to call brake vs accel
    private const val MOVING_MPS = 8.0 / 3.6
    private const val LONG_GAP_MS = 2500L         // debounce: one brake/accel maneuver
    private const val TURN_GAP_MS = 3000L         // debounce: sustained turns fire once
    private const val MIN_GRAVITY = 5.0

    // A corner's centripetal estimate (speed × yaw) and the measured horizontal-accel peak don't fall
    // on the same sample, so an instantaneous turn-veto leaks a spurious brake/accel mid-corner — the
    // narrated field drives (845/847) logged a hard curve as a 0.47 g "acceleration". The longitudinal
    // veto therefore looks at a short *window* of the turning signals. And a quick steering input
    // (swerve) shows more lateral g than speed × yaw predicts, so an ambiguous-slope spike during clear
    // rotation is treated as steering, not a fabricated brake/accel.
    private const val CORNER_VETO_MS = 450L       // ± window for the windowed turn-veto
    private const val AMB_TURN_YAW = 0.20         // rad/s windowed yaw → ambiguous spike is steering
    private const val AMB_TURN_LAT_G = 0.15       // g windowed centripetal → ambiguous spike is steering
    // An event's stored magnitude is the PEAK of the maneuver, not the value at the first
    // threshold-crossing sample — a hard brake/turn keeps building after it first crosses the line
    // (a narrated 0.5 g brake was being stored as 0.28 g). Scan forward this far for the peak.
    private const val PEAK_WINDOW_MS = 1500L

    // Startup & plausibility guards (Rev AA, from field trip 1126): motion is logged before the first
    // GPS fix, and those early samples get a stale *extrapolated* speed, so phone-handling yaw fabricated
    // a 4.65 g "corner" ~2 min before GPS locked; and a pothole's gyro buzz on a fast off-ramp faked a
    // 0.71 g "corner" (the corner channel had no vertical-bump veto).
    private const val WARMUP_MS = 1500L            // skip pre-GPS + the first settling samples (stale speed)
    private const val MAX_PLAUSIBLE_LAT_G = 0.9    // a car can't pull more sustained lateral g — reject as artifact
    private const val CORNER_BUMP_VERT_G = 0.38    // a coincident vertical jolt this big = bump, not a corner

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

    private data class YawSample(val t: Long, val yaw: Double, val speedMps: Double)

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
        // No events until the first GPS fix has settled — before it, motion samples get a stale
        // extrapolated speed that turns phone-handling yaw into a giant phantom corner.
        val warmupUntil = ptT.first() + WARMUP_MS
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

        // Per-(gravity-valid) sample channels, computed once so the longitudinal veto can look at a
        // short *window* of the turning signals instead of a single sample (see CORNER_VETO_MS).
        val cap = motions.size
        val sT = ArrayList<Long>(cap); val sU = ArrayList<Double>(cap); val sV = ArrayList<Double>(cap)
        val sHmagG = ArrayList<Double>(cap); val sVertG = ArrayList<Double>(cap)
        val sYaw = ArrayList<Double>(cap); val sYawLatG = ArrayList<Double>(cap); val sSpeed = ArrayList<Double>(cap)
        for (m in motions) {
            if (sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz) < MIN_GRAVITY) continue
            val sp = speedAt(m.t)
            val u = m.ax * e1x + m.ay * e1y + m.az * e1z
            val v = m.ax * e2x + m.ay * e2y + m.az * e2z
            val yaw = m.gx * dx + m.gy * dy + m.gz * dz
            sT.add(m.t); sU.add(u); sV.add(v)
            sHmagG.add(sqrt(u * u + v * v) / G)
            // Vertical (along-gravity) component, to tell a road bump from a real braking/accel force.
            sVertG.add(abs(m.ax * dx + m.ay * dy + m.az * dz) / G)
            sYaw.add(yaw); sYawLatG.add(sp * abs(yaw) / G); sSpeed.add(sp)
        }
        val sn = sT.size

        // Windowed maxima of the turning channels + the vertical-jolt channel (two forward-only
        // pointers over the sorted times). wVertG flags a coincident road bump so it can't fake a corner.
        val wYawLatG = DoubleArray(sn); val wYaw = DoubleArray(sn); val wVertG = DoubleArray(sn)
        var lo = 0; var hi = 0
        for (i in 0 until sn) {
            while (sT[lo] < sT[i] - CORNER_VETO_MS) lo++
            if (hi < i) hi = i
            while (hi < sn - 1 && sT[hi + 1] <= sT[i] + CORNER_VETO_MS) hi++
            var ml = 0.0; var my = 0.0; var mv = 0.0
            for (k in lo..hi) {
                if (sYawLatG[k] > ml) ml = sYawLatG[k]
                val ay = abs(sYaw[k]); if (ay > my) my = ay
                if (sVertG[k] > mv) mv = sVertG[k]
            }
            wYawLatG[i] = ml; wYaw[i] = my; wVertG[i] = mv
        }

        // Severity = the peak of the maneuver window (see PEAK_WINDOW_MS), restricted to samples that
        // still look like the same force (longitudinal: horizontal-dominant, not a bump or a turn).
        fun peakLongMag(i: Int): Double {
            var pk = sHmagG[i]; var k = i; val end = sT[i] + PEAK_WINDOW_MS
            while (k < sn && sT[k] <= end) {
                val h = sHmagG[k]
                if (h > pk && sVertG[k] < BUMP_VERT_DOMINANCE * h && wYawLatG[k] < 0.6 * h) pk = h
                k++
            }
            return pk
        }
        fun peakLatG(i: Int): Double {
            var pk = sYawLatG[i]; var k = i; val end = sT[i] + PEAK_WINDOW_MS
            while (k < sn && sT[k] <= end) { if (sYawLatG[k] > pk) pk = sYawLatG[k]; k++ }
            return pk
        }

        // Collect horizontal-accel magnitudes so the peak is a high percentile, not the raw max — one
        // phone bump or handling spike was setting a 1.1 g "peak" on otherwise calm drives (and that
        // peak feeds the safety score directly). p99.5 keeps a true hard maneuver while rejecting lone outliers.
        val horizMagsG = ArrayList<Double>()
        var lastLong = Long.MIN_VALUE; var lastTurn = Long.MIN_VALUE; var lastSwerve = Long.MIN_VALUE
        val yawSamples = ArrayList<YawSample>()
        for (i in 0 until sn) {
            if (sT[i] < warmupUntil) continue
            val sp = sSpeed[i]
            if (sp < MOVING_MPS) continue
            val t = sT[i]; val hMagG = sHmagG[i]; val vertG = sVertG[i]
            val yaw = sYaw[i]; val yawLatG = sYawLatG[i]
            horizMagsG.add(hMagG)
            yawSamples.add(YawSample(t, yaw, sp))

            // Corner: sustained lateral-g (takes precedence over a swerve), one event per turn. A
            // coincident vertical jolt (windowed) means a road bump shook the gyro — not a corner; and a
            // physically impossible magnitude (> MAX_PLAUSIBLE_LAT_G) is a handling artifact, rejected.
            if (yawLatG >= HARD_CORNER_G && wVertG[i] < CORNER_BUMP_VERT_G &&
                peakLatG(i) <= MAX_PLAUSIBLE_LAT_G && debounced(t, lastTurn, TURN_GAP_MS)) {
                events.add(DriveEvent(t, EventType.CORNER, peakLatG(i) * G, "fused", 0.8))
                turn++; lastTurn = t
            }
            // Swerve: a sharp yaw flick that isn't a sustained corner — only at real speed, otherwise
            // it's a normal tight turn (parking lot, side street), not an evasive maneuver.
            else if (abs(yaw) >= SWERVE_YAW && yawLatG < HARD_CORNER_G && sp >= SWERVE_MIN_SPEED_MPS &&
                debounced(t, lastSwerve, TURN_GAP_MS)) {
                events.add(DriveEvent(t, EventType.SWERVE, peakLatG(i).coerceAtMost(MAX_PLAUSIBLE_LAT_G) * G, "fused", 0.7))
                turn++; lastSwerve = t; lastTurn = t
            }
            // Longitudinal: horizontal spike not dominated by turning (windowed, see above) or by a
            // vertical bump → brake/accel.
            if (hMagG >= HARD_LONG_G && wYawLatG[i] < 0.6 * hMagG && vertG < BUMP_VERT_DOMINANCE * hMagG &&
                debounced(t, lastLong, LONG_GAP_MS)) {
                val gl = gpsSlope(t)
                val magMps2 = peakLongMag(i) * G
                when {
                    gl <= -SLOPE_MIN -> { events.add(DriveEvent(t, EventType.BRAKE, magMps2, "fused", 0.9)); brake++; lastLong = t }
                    gl >= SLOPE_MIN -> { events.add(DriveEvent(t, EventType.ACCEL, magMps2, "fused", 0.9)); accel++; lastLong = t }
                    // Ambiguous GPS slope. If the vehicle is clearly rotating, this horizontal force is
                    // steering, not braking/accel — suppress rather than guess (guessing fabricated a
                    // brake/accel on every narrated swerve). Otherwise lean on the forward-axis sign.
                    wYaw[i] >= AMB_TURN_YAW || wYawLatG[i] >= AMB_TURN_LAT_G -> { /* steering, not longitudinal */ }
                    else -> {
                        val lf = sU[i] * (fU / (fMag + 1e-9)) + sV[i] * (fV / (fMag + 1e-9))
                        if (lf < 0) { events.add(DriveEvent(t, EventType.BRAKE, magMps2, "fused", 0.4)); brake++ }
                        else { events.add(DriveEvent(t, EventType.ACCEL, magMps2, "fused", 0.4)); accel++ }
                        lastLong = t
                    }
                }
            }
        }
        detectSwerveReversals(yawSamples, events).forEach {
            events.add(it)
            turn++
        }
        events.sortBy { it.tMs }
        return Result(events, brake, accel, turn, confidence, percentile(horizMagsG, 0.995))
    }

    /** High-percentile value of an unsorted list (robust "peak" that rejects lone outlier spikes). */
    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = (sorted.size * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    /**
     * S-swerves can feel obvious even when each individual yaw flick is below the single-spike
     * threshold. Detect a right-left or left-right reversal inside one short maneuver window.
     */
    private fun detectSwerveReversals(
        samples: List<YawSample>,
        existingEvents: List<DriveEvent>
    ): List<DriveEvent> {
        if (samples.size < 20) return emptyList()
        val out = ArrayList<DriveEvent>()
        var i = 0
        while (i < samples.size) {
            val startT = samples[i].t
            val endT = startT + SWERVE_REVERSAL_WINDOW_MS
            val window = ArrayList<YawSample>()
            var j = i
            while (j < samples.size && samples[j].t <= endT) {
                window.add(samples[j])
                j++
            }
            if (window.size >= 20) {
                val pos = window.maxByOrNull { it.yaw }
                val neg = window.minByOrNull { it.yaw }
                if (pos != null && neg != null) {
                    val sep = abs(pos.t - neg.t)
                    val swing = pos.yaw - neg.yaw
                    if (pos.yaw >= SWERVE_REVERSAL_YAW &&
                        neg.yaw <= -SWERVE_REVERSAL_YAW &&
                        swing >= SWERVE_REVERSAL_SWING &&
                        sep in SWERVE_REVERSAL_MIN_SEP_MS..SWERVE_REVERSAL_MAX_SEP_MS
                    ) {
                        val t = (pos.t + neg.t) / 2
                        val duplicate =
                            existingEvents.any { it.isTurnLike() && abs(it.tMs - t) <= TURN_GAP_MS } ||
                                out.any { abs(it.tMs - t) <= TURN_GAP_MS }
                        val speed = 0.5 * (pos.speedMps + neg.speedMps)
                        if (!duplicate && speed >= SWERVE_REVERSAL_MIN_SPEED_MPS) {
                            val peakYaw = maxOf(abs(pos.yaw), abs(neg.yaw))
                            out.add(DriveEvent(t, EventType.SWERVE, speed * peakYaw, "fused", 0.85))
                            while (i < samples.size && samples[i].t < endT + TURN_GAP_MS) i++
                            continue
                        }
                    }
                }
            }
            i += 10
        }
        return out
    }

    private fun DriveEvent.isTurnLike(): Boolean =
        type == EventType.CORNER || type == EventType.SWERVE

    /** Overflow-safe debounce: the old `t - Long.MIN_VALUE >= gap` overflowed and blocked every event. */
    private fun debounced(t: Long, last: Long, gapMs: Long): Boolean =
        last == Long.MIN_VALUE || t - last >= gapMs
}
