package com.cartrip.analyzer.analysis

/**
 * Detects **drawdowns** (Rev CI): forced, unnecessary speed reductions — cruising fast, then having to
 * brake hard/long (a >50% speed loss) that *later recovers*, i.e. you got going again. This distinguishes
 * a stop-and-go / traffic-quality event from a normal destination or controlled-intersection stop (which
 * does not recover to the prior speed). A strong traffic-quality signal that feeds the Drive Stress Score.
 *
 * Pure and unit-testable; runs over the per-point speed track (1 Hz is plenty). Speed limits aren't required
 * (the recovery test already excludes legit decel like highway exits, which don't return to highway speed),
 * so it works at record time before OSM limits are fetched.
 */
object Drawdowns {
    /** A drawdown must start from a genuine cruise, not a slow crawl. */
    const val CRUISE_MIN_KMH = 60.0
    /** Speed must fall below this fraction of the cruise speed to count as a "forced" slowdown. */
    const val DROP_FRACTION = 0.5
    /** ...and then climb back to at least this fraction of the cruise speed — otherwise it's a real stop. */
    const val RECOVERY_FRACTION = 0.75
    /** Recovery must happen within this window after the cruise ended (else: a destination/long stop). */
    const val RECOVERY_WINDOW_S = 150.0
    /** The cruise must be sustained at least this long before the drop. */
    const val MIN_CRUISE_S = 4.0
    /** Ignore the first/last few seconds of a trip (pulling out / arriving are not drawdowns). */
    const val EDGE_TRIM_S = 5.0

    /** A cruise speed [v0], the trough it fell to [v1], and how much speed was lost. */
    data class Drawdown(val tMs: Long, val cruiseKmh: Double, val troughKmh: Double, val dropKmh: Double)

    /** [severity] = sum of (km/h lost)^2 across drawdowns — super-linear so a big forced stop dominates. */
    data class Result(val count: Int, val severity: Double, val drawdowns: List<Drawdown>) {
        companion object {
            val EMPTY = Result(0, 0.0, emptyList())
        }
    }

    fun detect(points: List<TrackPoint>): Result {
        val n = points.size
        if (n < 5) return Result.EMPTY
        val t0 = points.first().tMs
        val tN = points.last().tMs
        fun inEdge(tMs: Long): Boolean =
            (tMs - t0) < EDGE_TRIM_S * 1000 || (tN - tMs) < EDGE_TRIM_S * 1000

        val out = ArrayList<Drawdown>()
        var i = 0
        while (i < n) {
            if (points[i].speedKmh < CRUISE_MIN_KMH || inEdge(points[i].tMs)) {
                i++
                continue
            }
            // Extend the cruise while the speed stays reasonably high; the cruise speed is its peak.
            var j = i
            var cruiseSpeed = 0.0
            val cruiseStart = points[i].tMs
            while (j < n && points[j].speedKmh >= CRUISE_MIN_KMH * 0.85) {
                cruiseSpeed = maxOf(cruiseSpeed, points[j].speedKmh)
                j++
            }
            val cruiseEnd = points[j - 1].tMs
            if (cruiseEnd - cruiseStart < MIN_CRUISE_S * 1000) {
                i = maxOf(j, i + 1)
                continue
            }
            // From here the speed has fallen out of cruise. Find the trough and test for recovery.
            val dropFloor = cruiseSpeed * DROP_FRACTION
            val recoverTo = cruiseSpeed * RECOVERY_FRACTION
            val windowEnd = cruiseEnd + (RECOVERY_WINDOW_S * 1000).toLong()
            var k = j
            var trough = Double.MAX_VALUE
            var hitDrop = false
            var recoverIdx = -1
            while (k < n && points[k].tMs <= windowEnd) {
                val s = points[k].speedKmh
                if (s < trough) trough = s
                if (s <= dropFloor) hitDrop = true
                if (hitDrop && s >= recoverTo) {
                    recoverIdx = k
                    break
                }
                k++
            }
            if (hitDrop && recoverIdx >= 0) {
                val drop = cruiseSpeed - trough
                out += Drawdown(points[j].tMs, cruiseSpeed, trough, drop)
                i = recoverIdx + 1
            } else {
                i = maxOf(j, i + 1)
            }
        }
        val severity = out.sumOf { it.dropKmh * it.dropKmh }
        return Result(out.size, severity, out)
    }
}
