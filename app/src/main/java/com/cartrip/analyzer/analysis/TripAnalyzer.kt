package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.LocationSample
import com.cartrip.analyzer.data.MotionSample
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle helpers, all orientation independent. */
object GeoUtils {
    private const val R = 6371000.0 // earth radius, meters

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(a)))
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Smallest signed difference a-b in degrees, range (-180, 180]. */
    fun angleDiffDeg(a: Double, b: Double): Double = (a - b + 540) % 360 - 180
}

enum class EventType { ACCEL, BRAKE, CORNER, POTHOLE }

data class DriveEvent(val tMs: Long, val type: EventType, val magnitude: Double)

data class TrackPoint(
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val longAccel: Double,
    val latAccel: Double,
    val speedLimitKmh: Double = 0.0
)

data class DriveMetrics(
    val distanceM: Double = 0.0,
    val durationS: Double = 0.0,
    val movingS: Double = 0.0,
    val idleS: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgMovingSpeedMps: Double = 0.0,
    val maxAccelMps2: Double = 0.0,
    val maxBrakeMps2: Double = 0.0,
    val maxLateralMps2: Double = 0.0,
    val peakGForce: Double = 0.0,
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardCornerCount: Int = 0,
    val smoothness: Int = 100,
    val rawFixes: Int = 0,
    val usedFixes: Int = 0,
    // Fraction of moving time spent over a g threshold (hard brake >0.30g, turn >0.40g, accel >0.30g).
    val hardBrakePct: Double = 0.0,
    val aggressiveTurnPct: Double = 0.0,
    val hardAccelPct: Double = 0.0,
    // Jerk = rate of change of acceleration. maxJerk in m/s^3; jerkyPct = fraction of moving time
    // the ride was jerky (abrupt accel changes that feel worse than the g level alone).
    val maxJerk: Double = 0.0,
    val jerkyPct: Double = 0.0,
    // Accelerometer-fusion outputs (need recorded gravity): rough-road exposure, potholes, harsh stops.
    val roughRoadPct: Double = 0.0,
    val potholeCount: Int = 0,
    val harshStopCount: Int = 0,
    // Parallel sensor-fused event detector (accelerometer forward axis + gyro yaw), for comparison
    // against the GPS detector. Not used in scoring yet. Confidence 0..1 = how well the car's
    // forward axis could be inferred from the phone's pose.
    val motionBrakeCount: Int = 0,
    val motionAccelCount: Int = 0,
    val motionTurnCount: Int = 0,
    val fusedConfidence: Double = 0.0
)

data class TripAnalysis(
    val metrics: DriveMetrics,
    val points: List<TrackPoint>,
    val events: List<DriveEvent>
)

/**
 * Turns raw GPS + motion samples into a clean trajectory, speed/acceleration estimates and
 * driving events.
 *
 * Speed and longitudinal acceleration are estimated jointly with a **constant-acceleration
 * Kalman filter followed by an RTS smoother** — an offline, zero-lag optimal estimator. Each GPS
 * fix contributes a measurement of speed (chip Doppler when available, otherwise a position
 * derivative) weighted by its reported accuracy, and stationary fixes inject zero-velocity
 * pseudo-measurements (ZUPT) so jitter at stops can't fabricate motion. Distance is the integral
 * of the smoothed speed, which is far less noisy than summing raw fix-to-fix haversines.
 *
 * The accelerometer (with the recorded gravity vector) is fused in via MotionFusion for peak
 * g-force, potholes, rough-road exposure, and harsh stops. Hard brake/accel/corner events and
 * jerk are still GPS-derived; folding the accelerometer/gyro into those (confidence-scored,
 * device->vehicle projected) is the planned next step.
 */
object TripAnalyzer {

    // Event thresholds (m/s^2). ~0.25g accel, ~0.30g brake, ~0.35g corner.
    const val HARD_ACCEL = 2.5
    const val HARD_BRAKE = 3.0
    const val HARD_CORNER = 3.5

    // --- Plausibility / filtering limits ---
    private const val MAX_ACCURACY_M = 35.0   // drop GPS fixes worse than this
    private const val MAX_SPEED_MPS = 75.0     // 270 km/h: anything faster is a jump
    private const val MIN_DT_S = 0.2           // ignore near-duplicate timestamps
    private const val MAX_DT_S = 10.0          // larger gaps = signal loss, clamp continuity
    private const val LAT_LIMIT = 12.0         // |lateral accel| above this is noise
    private const val IDLE_SPEED = 0.5
    private const val EVENT_GAP_S = 2.0        // debounce, one maneuver = one event
    private const val CORNER_MIN_SPEED = 5.0   // don't trust bearing below this speed
    private const val POS_JUMP_MARGIN = 25.0   // implied speed this far above Doppler = glitch

    // --- Kalman / ZUPT tuning ---
    private const val JERK_PSD = 4.0           // (m/s^3)^2 process noise for the accel state
    private const val ZUPT_SPEED = 0.6         // both Doppler & position below this => stopped
    private const val ZUPT_R = 0.02            // tight noise on the zero-velocity pseudo-measurement

    // --- Exposure-factor thresholds (g), aligned with Tesla's Safety Score ---
    private const val G = 9.80665
    private const val HARD_BRAKE_G = 0.30 * G   // ~2.94 m/s^2
    private const val AGGR_TURN_G = 0.40 * G    // ~3.92 m/s^2
    private const val HARD_ACCEL_G = 0.30 * G   // ~2.94 m/s^2
    private const val JERKY_THRESHOLD = 1.5     // m/s^3, abrupt change in acceleration

    /** A cleaned, validated fix. speed/bearing are -1 when the chip did not report them. */
    private data class Clean(
        val t: Long, val lat: Double, val lon: Double,
        val speed: Double, val bearing: Double, val accuracy: Double
    )

    fun analyze(locs: List<LocationSample>, motions: List<MotionSample>): TripAnalysis {
        val clean = filter(locs)
        if (clean.size < 2) {
            return TripAnalysis(
                DriveMetrics(rawFixes = locs.size, usedFixes = clean.size),
                emptyList(), emptyList()
            )
        }
        val n = clean.size

        // Per-step time deltas (clamped to a sane range to bound predictions across gaps).
        val dt = DoubleArray(n)
        for (i in 1 until n) {
            dt[i] = ((clean[i].t - clean[i - 1].t) / 1000.0).coerceIn(MIN_DT_S, MAX_DT_S)
        }

        // Build speed measurements + measurement noise, with ZUPT where stationary.
        val z = DoubleArray(n)
        val rNoise = DoubleArray(n)
        for (i in 0 until n) {
            val c = clean[i]
            val posV = if (i > 0) {
                GeoUtils.haversine(clean[i - 1].lat, clean[i - 1].lon, c.lat, c.lon) / dt[i]
            } else c.speed.coerceAtLeast(0.0)
            val doppler = c.speed
            val effSpeed = if (doppler >= 0) doppler else posV.coerceIn(0.0, MAX_SPEED_MPS)
            when {
                effSpeed < ZUPT_SPEED && posV < ZUPT_SPEED + 0.4 -> { z[i] = 0.0; rNoise[i] = ZUPT_R }
                doppler >= 0 -> { z[i] = doppler; rNoise[i] = (0.5 + 0.08 * c.accuracy).pow(2) }
                else -> { z[i] = posV.coerceIn(0.0, MAX_SPEED_MPS); rNoise[i] = (2.0 + 0.2 * c.accuracy).pow(2) }
            }
        }

        val (vS, aS) = kalmanRtsSmooth(z, rNoise, dt)

        // Lateral acceleration from GPS heading change (low-passed; skips missing bearings).
        val latS = DoubleArray(n)
        var latPrev = 0.0
        for (i in 1 until n) {
            var lat = 0.0
            val bA = clean[i - 1].bearing
            val bB = clean[i].bearing
            if (vS[i] > CORNER_MIN_SPEED && bA >= 0 && bB >= 0) {
                val yawRate = Math.toRadians(GeoUtils.angleDiffDeg(bB, bA)) / dt[i]
                lat = vS[i] * yawRate
                if (abs(lat) > LAT_LIMIT) lat = 0.0
            }
            latPrev = 0.5 * lat + 0.5 * latPrev
            latS[i] = latPrev
        }

        // Metrics from the smoothed signals.
        var dist = 0.0
        var moving = 0.0
        var idle = 0.0
        var sumMovingSpeedDt = 0.0
        var maxSpeed = vS[0]
        var maxAccel = 0.0
        var maxBrake = 0.0
        var maxLat = 0.0
        var brakeTime = 0.0
        var turnTime = 0.0
        var accelTime = 0.0
        var jerkyTime = 0.0
        var maxJerk = 0.0
        for (i in 0 until n) {
            if (i > 0) {
                val avg = 0.5 * (vS[i - 1] + vS[i])
                dist += avg * dt[i]
                val jerk = abs(aS[i] - aS[i - 1]) / dt[i]
                if (jerk > maxJerk) maxJerk = jerk
                if (vS[i] > IDLE_SPEED) {
                    moving += dt[i]; sumMovingSpeedDt += vS[i] * dt[i]
                    if (-aS[i] >= HARD_BRAKE_G) brakeTime += dt[i]
                    if (abs(latS[i]) >= AGGR_TURN_G) turnTime += dt[i]
                    if (aS[i] >= HARD_ACCEL_G) accelTime += dt[i]
                    if (jerk >= JERKY_THRESHOLD) jerkyTime += dt[i]
                } else idle += dt[i]
            }
            if (vS[i] > maxSpeed) maxSpeed = vS[i]
            if (aS[i] > maxAccel) maxAccel = aS[i]
            if (-aS[i] > maxBrake) maxBrake = -aS[i]
            if (abs(latS[i]) > maxLat) maxLat = abs(latS[i])
        }

        val points = ArrayList<TrackPoint>(n)
        for (i in 0 until n) {
            points.add(TrackPoint(clean[i].t, clean[i].lat, clean[i].lon, vS[i] * 3.6, aS[i], latS[i]))
        }

        // Event detection on the clean smoothed signals.
        val events = ArrayList<DriveEvent>()
        var lastAccel = -1e9
        var lastBrake = -1e9
        var lastCorner = -1e9
        var hardAccel = 0
        var hardBrake = 0
        var hardCorner = 0
        for (i in 1 until n) {
            val tSec = clean[i].t / 1000.0
            val a = aS[i]
            if (a >= HARD_ACCEL && tSec - lastAccel >= EVENT_GAP_S) {
                hardAccel++; lastAccel = tSec
                events.add(DriveEvent(clean[i].t, EventType.ACCEL, a))
            }
            if (-a >= HARD_BRAKE && tSec - lastBrake >= EVENT_GAP_S) {
                hardBrake++; lastBrake = tSec
                events.add(DriveEvent(clean[i].t, EventType.BRAKE, -a))
            }
            if (abs(latS[i]) >= HARD_CORNER && tSec - lastCorner >= EVENT_GAP_S) {
                hardCorner++; lastCorner = tSec
                events.add(DriveEvent(clean[i].t, EventType.CORNER, abs(latS[i])))
            }
        }

        // Accelerometer/gravity fusion: potholes, rough road, harsh stops. Failing soft here keeps a
        // bad sensor batch from aborting finalize (which would leave the end-trip screen frozen).
        val fusion = runCatching { MotionFusion.analyze(motions, points) }
            .getOrDefault(MotionFusion.Result.EMPTY)
        val allEvents = (events + fusion.events).sortedBy { it.tMs }
        // Parallel sensor-fused event detector (for comparison vs GPS; not scored yet).
        val fused = runCatching { FusedEventDetector.detect(motions, points) }
            .getOrDefault(FusedEventDetector.Result.EMPTY)

        val duration = (clean.last().t - clean.first().t) / 1000.0
        val avgMoving = if (moving > 0) sumMovingSpeedDt / moving else 0.0
        val km = maxOf(0.1, dist / 1000.0)
        val eventRate = (hardBrake * 1.0 + hardAccel * 0.8 + hardCorner * 0.8) / km
        val smoothness = (100 - eventRate * 8).coerceIn(0.0, 100.0).roundToInt()

        val metrics = DriveMetrics(
            distanceM = dist,
            durationS = duration,
            movingS = moving,
            idleS = idle,
            maxSpeedMps = min(maxSpeed, MAX_SPEED_MPS),
            avgMovingSpeedMps = avgMoving,
            maxAccelMps2 = maxAccel,
            maxBrakeMps2 = maxBrake,
            maxLateralMps2 = maxLat,
            peakGForce = peakGForce(motions),
            hardAccelCount = hardAccel,
            hardBrakeCount = hardBrake,
            hardCornerCount = hardCorner,
            smoothness = smoothness,
            rawFixes = locs.size,
            usedFixes = clean.size,
            hardBrakePct = if (moving > 0) brakeTime / moving else 0.0,
            aggressiveTurnPct = if (moving > 0) turnTime / moving else 0.0,
            hardAccelPct = if (moving > 0) accelTime / moving else 0.0,
            maxJerk = maxJerk,
            jerkyPct = if (moving > 0) jerkyTime / moving else 0.0,
            roughRoadPct = fusion.roughRoadPct,
            potholeCount = fusion.potholeCount,
            harshStopCount = fusion.harshStopCount,
            motionBrakeCount = fused.brakeCount,
            motionAccelCount = fused.accelCount,
            motionTurnCount = fused.turnCount,
            fusedConfidence = fused.confidence
        )
        return TripAnalysis(metrics, points, allEvents)
    }

    /**
     * Forward constant-acceleration Kalman filter + Rauch-Tung-Striebel backward smoother.
     * State is [speed, acceleration]; returns the smoothed speed and acceleration series.
     * Operating on the whole trip offline makes the estimate zero-lag, unlike a causal filter.
     */
    private fun kalmanRtsSmooth(z: DoubleArray, r: DoubleArray, dt: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = z.size
        val vF = DoubleArray(n); val aF = DoubleArray(n)   // filtered state
        val vP = DoubleArray(n); val aP = DoubleArray(n)   // predicted state
        val pF = Array(n) { DoubleArray(4) }               // filtered covariance [p00,p01,p10,p11]
        val pP = Array(n) { DoubleArray(4) }               // predicted covariance

        // Initialise from the first measurement.
        vF[0] = z[0]; aF[0] = 0.0; vP[0] = z[0]; aP[0] = 0.0
        pF[0] = doubleArrayOf(r[0], 0.0, 0.0, 4.0); pP[0] = pF[0].copyOf()

        for (i in 1 until n) {
            val d = dt[i]
            val pv = pF[i - 1]
            // Predict state.
            val vpr = vF[i - 1] + aF[i - 1] * d
            val apr = aF[i - 1]
            // Predict covariance: P' = F P F^T + Q.
            val a = pv[0] + d * pv[2]; val b = pv[1] + d * pv[3]; val c = pv[2]; val dd = pv[3]
            var p00 = a + d * b; var p01 = b; var p10 = c + d * dd; var p11 = dd
            val q = JERK_PSD
            p00 += d * d * d * d / 4.0 * q; p01 += d * d * d / 2.0 * q
            p10 += d * d * d / 2.0 * q; p11 += d * d * q
            vP[i] = vpr; aP[i] = apr; pP[i] = doubleArrayOf(p00, p01, p10, p11)
            // Update with measurement z[i] (H = [1, 0]).
            val s = p00 + r[i]
            val k0 = p00 / s; val k1 = p10 / s
            val y = z[i] - vpr
            vF[i] = vpr + k0 * y; aF[i] = apr + k1 * y
            pF[i] = doubleArrayOf(
                (1 - k0) * p00,
                (1 - k0) * p01,
                -k1 * p00 + p10,
                -k1 * p01 + p11
            )
        }

        val vS = DoubleArray(n); val aS = DoubleArray(n)
        vS[n - 1] = vF[n - 1]; aS[n - 1] = aF[n - 1]
        for (i in n - 2 downTo 0) {
            val d = dt[i + 1]
            val pf = pF[i]
            // M = P_filt * F^T,  F^T = [[1,0],[d,1]]
            val m00 = pf[0] + pf[1] * d; val m01 = pf[1]
            val m10 = pf[2] + pf[3] * d; val m11 = pf[3]
            // inv(P_pred[i+1])
            val pp = pP[i + 1]
            val det = pp[0] * pp[3] - pp[1] * pp[2]
            val inv = if (abs(det) < 1e-9) 0.0 else 1.0 / det
            val i00 = pp[3] * inv; val i01 = -pp[1] * inv; val i10 = -pp[2] * inv; val i11 = pp[0] * inv
            // Smoother gain C = M * inv
            val c00 = m00 * i00 + m01 * i10; val c01 = m00 * i01 + m01 * i11
            val c10 = m10 * i00 + m11 * i10; val c11 = m10 * i01 + m11 * i11
            val dv = vS[i + 1] - vP[i + 1]; val da = aS[i + 1] - aP[i + 1]
            vS[i] = vF[i] + c00 * dv + c01 * da
            aS[i] = aF[i] + c10 * dv + c11 * da
        }

        // Non-negative speed, and snap genuine stops to exactly zero.
        for (i in 0 until n) {
            if (vS[i] < 0) vS[i] = 0.0
            if (z[i] == 0.0 && r[i] <= ZUPT_R) { vS[i] = 0.0; aS[i] = 0.0 }
        }
        return vS to aS
    }

    /**
     * Reject poor-accuracy fixes, near-duplicates, and GPS jumps: a fix whose implied speed is
     * impossible (with no trustworthy Doppler), or that leaps far past a valid Doppler reading,
     * is dropped and the last good point is kept as the reference.
     */
    private fun filter(locs: List<LocationSample>): List<Clean> {
        val out = ArrayList<Clean>(locs.size)
        var last: Clean? = null
        for (s in locs) {
            val acc = if (s.accuracy > 0) s.accuracy else 999.0
            if (acc > MAX_ACCURACY_M) continue
            val doppler = if (s.speed in 0.0..MAX_SPEED_MPS) s.speed else -1.0
            val bearing = if (s.bearing in 0.0..360.0) s.bearing else -1.0
            val cur = Clean(s.t, s.lat, s.lon, doppler, bearing, acc)

            val prev = last
            if (prev == null) { out.add(cur); last = cur; continue }

            val dt = (cur.t - prev.t) / 1000.0
            if (dt < MIN_DT_S) continue // duplicate / too close in time
            if (dt <= MAX_DT_S) {
                val d = GeoUtils.haversine(prev.lat, prev.lon, cur.lat, cur.lon)
                val implied = d / dt
                // Impossible implied speed with no Doppler to corroborate -> drop.
                if (doppler < 0 && implied > MAX_SPEED_MPS) continue
                // Position glitch: leaps far past a trustworthy Doppler reading -> drop.
                if (doppler in 0.0..MAX_SPEED_MPS && implied > doppler + POS_JUMP_MARGIN && implied > 35.0) continue
            }
            out.add(cur); last = cur
        }
        return out
    }

    /** 99th-percentile magnitude of linear acceleration, in g — rejects single bump spikes. */
    private fun peakGForce(motions: List<MotionSample>): Double {
        if (motions.isEmpty()) return 0.0
        val mags = motions.map { sqrt(it.ax * it.ax + it.ay * it.ay + it.az * it.az) }.sorted()
        val idx = (mags.size * 0.99).toInt().coerceIn(0, mags.size - 1)
        return mags[idx] / 9.81
    }
}
