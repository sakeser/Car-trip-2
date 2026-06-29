package com.cartrip.analyzer.analysis

/**
 * Stop-and-go / continuous-focus signals for the **Drive Stress Score v2** (owner-driven, 2026-06-29).
 *
 * Motivation: on the 2026-06-29 narrated drive, a genuinely stressful stop-and-go crawl scored "Calm"
 * because the old model only saw drawdowns (which miss sustained stop-and-go — the car never re-cruises)
 * plus a Routes congestion factor that was missing for that trip. These three signals come straight from
 * the per-point speed track, so they don't depend on an external traffic estimate:
 *
 *  - [crawlFraction]: share of MOVING time spent crawling (< [CRAWL_KMH]). Raw stop-and-go density.
 *  - [belowLimitLoad]: time-weighted mean shortfall below the posted limit over moving time (0 = at/above
 *    the limit, 1 = crawling on a fast road). Driving 40 in a 100 zone reads 0.6. Captures "congested
 *    relative to the road" — the owner's idea, and more robust than the Routes ETA (a snapshot/typical
 *    estimate, not the conditions actually driven).
 *  - [longestNoBreakS] / [restCount]: the owner's "no mental break" idea. A *rest* = at/below [REST_KMH]
 *    for at least [REST_MIN_S] (a real pause — a light, a true stop — NOT a stop-and-go creep, which is
 *    brief). [longestNoBreakS] is the longest continuous driving stretch with no such rest. Long unbroken
 *    operation (spacing, lane changes, hazard watching) is taxing; the longer, the more stressful.
 *
 * Pure + unit-testable; dt-weighted off the timestamps so it's robust to gaps and isn't fooled by uneven
 * sampling. **Not yet wired into [StressScore]** — weights/shape are being calibrated with the owner
 * (HANDOFF section 11.1, Drive Stress v2). Validated by DB-replay on trips 1187 (smooth) vs 1189 (crawl).
 */
object StopAndGo {
    /** Above this speed the vehicle is "moving" (below it counts as a stop, not slow driving). */
    const val MOVING_KMH = 3.0
    /** Moving but below this = a congested crawl. */
    const val CRAWL_KMH = 40.0
    /** At/below this speed the vehicle is "at rest". */
    const val REST_KMH = 3.0
    /** A rest must last at least this long to count as a real mental break (not a stop-and-go creep). */
    const val REST_MIN_S = 10.0
    /** Time gaps longer than this (s) between points are ignored (GPS dropout), not counted as driving. */
    const val MAX_GAP_S = 30.0

    data class Result(
        val crawlFraction: Double,   // 0..1, share of moving time below CRAWL_KMH
        val belowLimitLoad: Double,  // 0..1, mean shortfall vs the posted limit over moving time
        val longestNoBreakS: Double, // longest continuous driving stretch with no qualifying rest
        val restCount: Int           // number of qualifying rests (>= REST_MIN_S at/below REST_KMH)
    ) {
        companion object {
            val EMPTY = Result(0.0, 0.0, 0.0, 0)
        }
    }

    fun analyze(points: List<TrackPoint>): Result {
        val n = points.size
        if (n < 2) return Result.EMPTY

        // dt-weighted moving / crawl time + below-limit load.
        var movingS = 0.0
        var crawlS = 0.0
        var belowSum = 0.0
        var limitTimeS = 0.0
        for (i in 0 until n - 1) {
            val p = points[i]
            val dt = (points[i + 1].tMs - p.tMs) / 1000.0
            if (dt <= 0.0 || dt > MAX_GAP_S) continue
            if (p.speedKmh >= MOVING_KMH) {
                movingS += dt
                if (p.speedKmh < CRAWL_KMH) crawlS += dt
                if (p.speedLimitKmh > 0.0) {
                    belowSum += (1.0 - p.speedKmh / p.speedLimitKmh).coerceIn(0.0, 1.0) * dt
                    limitTimeS += dt
                }
            }
        }

        // Rests: contiguous runs at/below REST_KMH lasting >= REST_MIN_S. The longest no-break stretch is
        // the longest active span between rests (and the trip ends).
        val restMinMs = (REST_MIN_S * 1000).toLong()
        var restCount = 0
        var longest = 0.0
        var stretchStart = points.first().tMs
        var i = 0
        while (i < n) {
            if (points[i].speedKmh <= REST_KMH) {
                var j = i
                while (j < n && points[j].speedKmh <= REST_KMH) j++
                val restStart = points[i].tMs
                val restEnd = points[j - 1].tMs
                if (restEnd - restStart >= restMinMs) {
                    restCount++
                    longest = maxOf(longest, (restStart - stretchStart) / 1000.0)
                    stretchStart = restEnd
                }
                i = j
            } else {
                i++
            }
        }
        longest = maxOf(longest, (points.last().tMs - stretchStart) / 1000.0)

        val crawlFraction = if (movingS > 0.0) crawlS / movingS else 0.0
        val belowLimitLoad = if (limitTimeS > 0.0) belowSum / limitTimeS else 0.0
        return Result(crawlFraction, belowLimitLoad, longest, restCount)
    }
}
