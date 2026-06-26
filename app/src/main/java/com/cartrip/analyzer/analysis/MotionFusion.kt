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
    // Harsh stops (recalibrated Rev AH against 27 real trips). Two prior bugs made it fire ~0/trip:
    //  (1) a stop was only counted when the *immediately preceding* 1 Hz GPS sample was >= MOVING_KMH,
    //      but a normal decel ramp (10 -> 5 -> 2 km/h) lands an intermediate sample in the 3-8 band, so
    //      ~90% of real stops were missed (trip 845: 1 detected vs 14 real). A stop is now any crossing
    //      below STOP_KMH that was preceded by genuine movement within HARSH_STOP_LOOKBACK_MS.
    //  (2) harshness was sample-to-sample horizontal *jerk*, which on 50 Hz accel is pure noise (5-168
    //      m/s^3) and cleared the bar on every stop. It is now the smoothed PEAK horizontal braking
    //      force in the approach to rest — in the data, gentle stops sit < 1.4 m/s^2 and firm stops jump
    //      to 2.6-5.7 m/s^2, so HARSH_STOP_DECEL = 3.0 m/s^2 (~0.31 g, the established hard-brake line).
    private const val STOP_KMH = 3.0
    private const val HARSH_STOP_LOOKBACK_MS = 4000L  // must have been moving (>=MOVING_KMH) this recently
    private const val HARSH_STOP_DEBOUNCE_MS = 5000L  // one stop per window (don't double-count a crawl)
    private const val HARSH_STOP_WINDOW_MS = 2000L    // look this far before rest for the braking peak
    private const val HARSH_STOP_DECEL = 3.0          // m/s^2 peak horizontal braking force -> harsh
    private const val HARSH_STOP_SMOOTH = 2           // +/- samples for the centered mean (rejects spikes)

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

        // Harsh stops: a firm braking force in the ~2 s before the car comes to rest (see the constants
        // above for the two bugs this replaces). Smooth the horizontal channel first so a lone accel
        // spike can't fake a harsh stop; harshness is the peak of that smoothed force.
        var harshStopCount = 0
        val hSmooth = DoubleArray(acc.size)
        for (i in acc.indices) {
            var s = 0.0; var n = 0
            for (k in (i - HARSH_STOP_SMOOTH)..(i + HARSH_STOP_SMOOTH)) {
                if (k in acc.indices) { s += acc[k].horizontal; n++ }
            }
            hSmooth[i] = if (n > 0) s / n else acc[i].horizontal
        }
        var lastStopT = Long.MIN_VALUE
        for (k in 1 until points.size) {
            // A crossing INTO stopped: previously at/above STOP_KMH, now below it.
            if (points[k].speedKmh >= STOP_KMH || points[k - 1].speedKmh < STOP_KMH) continue
            val stopT = points[k].tMs
            // Require real movement within the lookback window (so a slow crawl that dips below 3 km/h
            // without a genuine drive isn't a "stop"), and debounce repeated samples of one stop.
            var movedRecently = false
            var j = k - 1
            while (j >= 0 && points[j].tMs >= stopT - HARSH_STOP_LOOKBACK_MS) {
                if (points[j].speedKmh >= MOVING_KMH) { movedRecently = true; break }
                j--
            }
            if (!movedRecently) continue
            if (lastStopT != Long.MIN_VALUE && stopT - lastStopT < HARSH_STOP_DEBOUNCE_MS) continue
            lastStopT = stopT
            var peak = 0.0
            for (i in acc.indices) {
                val t = acc[i].t
                if (t < stopT - HARSH_STOP_WINDOW_MS) continue
                if (t > stopT) break
                if (hSmooth[i] > peak) peak = hSmooth[i]
            }
            if (peak >= HARSH_STOP_DECEL) harshStopCount++
        }

        return Result(events, roughRoadPct, events.size, harshStopCount, roughStretchCount, bumpyScore)
    }
}
