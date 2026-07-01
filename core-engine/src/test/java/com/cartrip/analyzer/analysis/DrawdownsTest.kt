package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawdownsTest {

    /** Build a 1 Hz track from (durationSeconds, speedKmh) segments. */
    private fun track(vararg segs: Pair<Int, Double>): List<TrackPoint> {
        val pts = ArrayList<TrackPoint>()
        var t = 0L
        for ((dur, spd) in segs) {
            repeat(dur) {
                pts += TrackPoint(t, 0.0, 0.0, spd, 0.0, 0.0)
                t += 1000
            }
        }
        return pts
    }

    @Test fun clearDrawdownCounted() {
        // Cruise 100, forced down to 25, then back up to 95 — a textbook drawdown.
        val r = Drawdowns.detect(track(12 to 100.0, 6 to 25.0, 12 to 95.0))
        assertEquals(1, r.count)
        val d = r.drawdowns.first()
        assertEquals(100.0, d.cruiseKmh, 1e-6)
        assertEquals(25.0, d.troughKmh, 1e-6)
        assertEquals(75.0, d.dropKmh, 1e-6)
        assertEquals(75.0 * 75.0, r.severity, 1e-6)
    }

    @Test fun destinationStopNotCounted() {
        // Cruise then slow to a stop that never recovers — a normal end-of-trip / destination stop.
        val r = Drawdowns.detect(track(12 to 100.0, 12 to 0.0))
        assertEquals(0, r.count)
    }

    @Test fun smoothCruiseHasNoDrawdowns() {
        val r = Drawdowns.detect(track(30 to 100.0))
        assertEquals(0, r.count)
    }

    @Test fun gentleSlowdownNotCounted() {
        // Only a ~30% dip (100 -> 72): below the 50% drop threshold, so not a drawdown.
        val r = Drawdowns.detect(track(12 to 100.0, 6 to 72.0, 12 to 98.0))
        assertEquals(0, r.count)
    }

    @Test fun twoDrawdownsInOneTrip() {
        val r = Drawdowns.detect(
            track(12 to 100.0, 5 to 20.0, 12 to 90.0, 5 to 25.0, 12 to 95.0)
        )
        assertEquals(2, r.count)
        // severity is super-linear: (100-20)^2 + (90-25)^2
        assertEquals(80.0 * 80.0 + 65.0 * 65.0, r.severity, 1.0)
    }

    @Test fun walkSpeedsNeverDrawdown() {
        val r = Drawdowns.detect(track(20 to 5.0, 10 to 1.0, 20 to 6.0))
        assertEquals(0, r.count)
        assertTrue(r.severity == 0.0)
    }
}
