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
    private const val POTHOLE_VERT = 4.5         // m/s^2 vertical jolt → pothole/bump (~0.46 g)
    private const val POTHOLE_GAP_MS = 800L      // debounce repeated samples of one impact
    private const val ROUGH_RMS = 0.9            // windowed vertical RMS → "rough road"
    private const val ROUGH_WINDOW_MS = 3000L
    private const val MOVING_KMH = 8.0           // only judge road/potholes while actually moving
    private const val HARSH_STOP_JERK = 3.0      // horizontal jerk (m/s^3) just before a stop → harsh

    data class Result(
        val events: List<DriveEvent>,            // POTHOLE events (timestamped, mappable)
        val roughRoadPct: Double,                // fraction of moving time on rough/vibrating road
        val potholeCount: Int,
        val harshStopCount: Int
    ) {
        companion object {
            val EMPTY = Result(emptyList(), 0.0, 0, 0)
        }
    }

    private class Acc(val t: Long, val vertical: Double, val horizontal: Double)

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

        // Potholes: sharp vertical jolts while moving, debounced.
        val events = ArrayList<DriveEvent>()
        var lastPothole = Long.MIN_VALUE
        for (a in acc) {
            if (abs(a.vertical) >= POTHOLE_VERT && a.t - lastPothole >= POTHOLE_GAP_MS && speedAt(a.t) >= MOVING_KMH) {
                events.add(DriveEvent(a.t, EventType.POTHOLE, abs(a.vertical)))
                lastPothole = a.t
            }
        }

        // Rough road: windowed vertical RMS while moving.
        var roughMs = 0L
        var movingMs = 0L
        var idx = 0
        var winStart = acc.first().t
        val end = acc.last().t
        while (winStart <= end && idx < acc.size) {
            val winEnd = winStart + ROUGH_WINDOW_MS
            var sum = 0.0; var cnt = 0
            while (idx < acc.size && acc[idx].t < winEnd) { sum += acc[idx].vertical * acc[idx].vertical; cnt++; idx++ }
            if (cnt > 0 && speedAt(winStart + ROUGH_WINDOW_MS / 2) >= MOVING_KMH) {
                movingMs += ROUGH_WINDOW_MS
                if (sqrt(sum / cnt) >= ROUGH_RMS) roughMs += ROUGH_WINDOW_MS
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

        return Result(events, roughRoadPct, events.size, harshStopCount)
    }
}
