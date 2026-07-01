package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAnalyzerTest {

    /** Constant 20 m/s due east for 60 s should integrate to ~1200 m with peak speed ~20 m/s. */
    @Test fun straightLineConstantSpeed() {
        val mps = 20.0
        val lat = 43.0
        val cos = Math.cos(Math.toRadians(lat))
        var lon = -79.0
        val locs = ArrayList<LocationSample>()
        for (i in 0..60) {
            locs += LocationSample(
                tripId = 1, t = i * 1000L, lat = lat, lon = lon,
                speed = mps, bearing = 90.0, accuracy = 5.0
            )
            lon += mps / (111_320.0 * cos)
        }
        val a = TripAnalyzer.analyze(locs, emptyList())
        assertEquals(1200.0, a.metrics.distanceM, 80.0)
        assertEquals(20.0, a.metrics.maxSpeedMps, 1.5)
        assertTrue("points=${a.points.size}", a.points.size > 50)
    }

    /** A hard stop (20 -> 0 m/s in ~2 s) should register as a braking event. */
    @Test fun hardBrakeIsDetected() {
        val lat = 43.0
        val cos = Math.cos(Math.toRadians(lat))
        var lon = -79.0
        val locs = ArrayList<LocationSample>()
        var t = 0L
        // cruise at 20 m/s for 10 s
        var v = 20.0
        repeat(10) {
            locs += LocationSample(tripId = 1, t = t, lat = lat, lon = lon, speed = v, bearing = 90.0, accuracy = 5.0)
            lon += v / (111_320.0 * cos); t += 1000L
        }
        // brake ~6 m/s^2: 20 -> 0 over ~3 s
        for (vv in listOf(14.0, 8.0, 2.0, 0.0)) {
            locs += LocationSample(tripId = 1, t = t, lat = lat, lon = lon, speed = vv, bearing = 90.0, accuracy = 5.0)
            lon += vv / (111_320.0 * cos); t += 1000L
        }
        // sit stopped
        repeat(5) {
            locs += LocationSample(tripId = 1, t = t, lat = lat, lon = lon, speed = 0.0, bearing = 90.0, accuracy = 5.0)
            t += 1000L
        }
        val a = TripAnalyzer.analyze(locs, emptyList())
        assertTrue("brakes=${a.metrics.hardBrakeCount} maxBrake=${a.metrics.maxBrakeMps2}", a.metrics.hardBrakeCount >= 1)
    }

    @Test fun emptyInputIsSafe() {
        val a = TripAnalyzer.analyze(emptyList(), emptyList())
        assertEquals(0.0, a.metrics.distanceM, 1e-9)
        assertTrue(a.events.isEmpty())
    }
}
