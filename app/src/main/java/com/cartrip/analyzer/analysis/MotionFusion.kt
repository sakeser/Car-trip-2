package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.MotionSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fuses the high-rate accelerometer with the recorded gravity vector and the GPS track to surface
 * things GPS alone can't see.
 *
 * Using the gravity direction (device-frame "down"), each linear-acceleration sample is split into:
 *  - vertical accel = projection onto gravity → bumps, potholes, road roughness
 *  - horizontal accel = the remainder → the actual driving force (braking/cornering), orientation-free
 *
 * From that we detect: potholes (sharp vertical jolts), sustained rough road (windowed vertical RMS),
 * and harsh stops (abrupt horizontal jerk as the car comes to rest). It fails soft: trips without
 * gravity data (older recordings, demo) yield an empty result and nothing changes.
 */
object MotionFusion {

    private const val MIN_GRAVITY = 5.0          // ignore samples before the gravity sensor warms up
    // Road excitation grows with speed: at highway speed normal expansion-joint/texture buzz exceeds a
    // city-speed cutoff and fabricates dozens of "potholes" (field trip 1126: 34 in 34 min, mostly at
    // 90-114 km/h). So the bar rises with speed — a city manhole (0.33 g @ <=40 km/h) still counts,
    // highway buzz (~0.40 g @ 110 km/h) doesn't. Field-calibrated low end against trips 778/779.
    private const val POTHOLE_VERT = 3.2         // base ~0.33 g at/under POTHOLE_SPEED_LO (catches a manhole)
    private const val POTHOLE_VERT_HWY = 4.9     // ~0.50 g at/over POTHOLE_SPEED_HI
    private const val POTHOLE_SPEED_LO = 40.0
    private const val POTHOLE_SPEED_HI = 110.0
    private const val POTHOLE_GAP_MS = 800L      // debounce repeated samples of one impact
    private const val ROUGH_RMS = 1.1            // windowed vertical RMS → "rough road" (raised from 0.9
                                                 // so only genuinely rough stretches count — Rev AA)
    private const val ROUGH_WINDOW_MS = 3000L
    // Discrete "rough stretch" detection: 1 s windows; a run of consecutive rough windows (>=1 s)
    // is one stretch. bumpyScore integrates vertical RMS over rough time (bumpiness x duration).
    private const val STRETCH_WINDOW_MS = 1000L
    private const val MOVING_KMH = 8.0           // only judge road/potholes while actually moving
    private const val HARSH_STOP_JERK = 3.0      // horizontal jerk (m/s^3) just before a stop → harsh

    data class Result(
        val events: List<DriveEvent>,            // POTHOLE events (timestamped, mappable)
        val roughRoadPct: Double,                // fraction of moving time on rough/vibrating road
        val potholeCount: Int,
        val harshStopCount: Int,
        val roughStretchCount: Int = 0,          // discrete >=1 s sustained-bumpy episodes
        val bumpyScore: Double = 0.0             // integral of vertical RMS over rough time
    ) {
        companion object {
            val EMPTY = Result(emptyList(), 0.0, 0, 0, 0, 0.0)
        }
    }

    private class Acc(val t: Long, val vertical: Double, val horizontal: Double)

    /** Speed-aware pothole cutoff: base at city speed, ramping up to the highway value (see notes above). */
    private fun potholeThreshold(speedKmh: Double): Double {
        val f = ((speedKmh - POTHOLE_SPEED_LO) / (POTHOLE_SPEED_HI - POTHOLE_SPEED_LO)).coerceIn(0.0, 1.0)
        return POTHOLE_VERT + f * (POTHOLE_VERT_HWY - POTHOLE_VERT)
    }

    fun analyze(motions: List<MotionSample>, points: List<TrackPoint>): Result {
        if (motions.size < 20 || points.size < 2) return Result.EMPTY

        // Project each sample onto vertical (gravity) and horizontal axes.
        val acc = ArrayList<Acc>(motions.size)
        for (m in motions) {
            val gm = sqrt(m.grx * m.grx + m.gry * m.gry + m.grz * m.grz)
            if (gm < MIN_GRAVITY) continue
            val ux = m.grx / gm; val uy = m.gry / gm; val uz = m.grz / gm
            val v = m.ax * ux + m.ay * uy + m.az * uz
            val am2 = m.ax * m.ax + m.ay * m.ay + m.az * m.az
            val h = sqrt(maxOf(0.0, am2 - v * v))
            acc.add(Acc(m.t, v, h))
        }
        if (acc.size < 20) return Result.EMPTY

        val ptTimes = LongArray(points.size) { points[it].tMs }
        fun speedAt(t: Long): Double {
            if (t <= ptTimes.first()) return points.first().speedKmh
            if (t >= ptTimes.last()) return points.last().speedKmh
            var lo = 0; var hi = ptTimes.lastIndex
            while (lo < hi) { val mid = (lo + hi) / 2; if (ptTimes[mid] < t) lo = mid + 1 else hi = mid }
            return if (t - ptTimes[lo - 1] <= ptTimes[lo] - t) points[lo - 1].speedKmh else points[lo].speedKmh
        }

        // Potholes: sharp vertical jolts while moving, debounced. The debounce sentinel must be
        // overflow-safe — `a.t - Long.MIN_VALUE` overflows to a huge negative that's never >= the gap,
        // which previously blocked every pothole (potholeCount stuck at 0).
        val events = ArrayList<DriveEvent>()
        var lastPothole = Long.MIN_VALUE
        for (a in acc) {
            val sp = speedAt(a.t)
            val gapOk = lastPothole == Long.MIN_VALUE || a.t - lastPothole >= POTHOLE_GAP_MS
            if (abs(a.vertical) >= potholeThreshold(sp) && gapOk && sp >= MOVING_KMH) {
                events.add(DriveEvent(a.t, EventType.POTHOLE, abs(a.vertical), "motion", 1.0))
                lastPothole = a.t
            }
        }

        // Rough road: 1 s windows of vertical RMS while moving. Track the rough fraction (kept for
        // compatibility), plus discrete rough *stretches* (runs of consecutive rough windows) and a
        // bumpyScore = sum of vertical RMS over rough time (bumpiness x duration).
        var roughMs = 0L
        var movingMs = 0L
        var roughStretchCount = 0
        var bumpyScore = 0.0
        var inStretch = false
        var idx = 0
        var winStart = acc.first().t
        val end = acc.last().t
        while (winStart <= end && idx < acc.size) {
            val winEnd = winStart + STRETCH_WINDOW_MS
            var sum = 0.0; var cnt = 0
            while (idx < acc.size && acc[idx].t < winEnd) { sum += acc[idx].vertical * acc[idx].vertical; cnt++; idx++ }
            val moving = cnt > 0 && speedAt(winStart + STRETCH_WINDOW_MS / 2) >= MOVING_KMH
            val rms = if (cnt > 0) sqrt(sum / cnt) else 0.0
            val rough = moving && rms >= ROUGH_RMS
            if (moving) movingMs += STRETCH_WINDOW_MS
            if (rough) {
                roughMs += STRETCH_WINDOW_MS
                if (!inStretch) { roughStretchCount++; inStretch = true }
                // Severity weighted non-linearly: (RMS over threshold)^2 x seconds, so a few hard,
                // sustained stretches score far above many marginal ones (Rev AA, owner's proposal).
                val excess = rms - ROUGH_RMS
                bumpyScore += excess * excess * (STRETCH_WINDOW_MS / 1000.0)
            } else {
                inStretch = false
            }
            winStart = winEnd
        }
        val roughRoadPct = if (movingMs > 0) roughMs.toDouble() / movingMs else 0.0

        // Harsh stops: abrupt horizontal jerk in the 2 s before the car comes to rest.
        var harshStopCount = 0
        for (k in 1 until points.size) {
            if (points[k].speedKmh < 3.0 && points[k - 1].speedKmh >= MOVING_KMH) {
                val stopT = points[k].tMs
                var maxJerk = 0.0
                var prev: Acc? = null
                for (a in acc) {
                    if (a.t < stopT - 2000) { prev = a; continue }
                    if (a.t > stopT) break
                    val p = prev
                    if (p != null) {
                        val dt = (a.t - p.t) / 1000.0
                        if (dt > 0) maxJerk = maxOf(maxJerk, abs(a.horizontal - p.horizontal) / dt)
                    }
                    prev = a
                }
                if (maxJerk >= HARSH_STOP_JERK) harshStopCount++
            }
        }

        return Result(events, roughRoadPct, events.size, harshStopCount, roughStretchCount, bumpyScore)
    }
}
