package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayEventsTest {
    private val g = 9.80665

    /** Points covering 0..durMs at a constant speed, ~1 Hz. */
    private fun pts(durMs: Long, kmh: Double): List<TrackPoint> =
        (0..(durMs / 1000)).map { TrackPoint(it * 1000L, 43.0, -79.0, kmh, 0.0, 0.0) }

    @Test fun dropsSubThresholdEvents() {
        val weak = DriveEvent(1000L, EventType.BRAKE, 0.1 * g, "gps", 1.0)
        assertTrue(DisplayEvents.clean(listOf(weak), pts(20_000, 50.0)).isEmpty())
    }

    @Test fun keepsStrongEvent() {
        val strong = DriveEvent(1000L, EventType.BRAKE, 0.45 * g, "gps", 1.0)
        val out = DisplayEvents.clean(listOf(strong), pts(20_000, 50.0))
        assertEquals(1, out.size)
        assertEquals(EventType.BRAKE, out.first().type)
    }

    @Test fun clustersNearbyEventsIntoOne() {
        val a = DriveEvent(1000L, EventType.BRAKE, 0.40 * g, "gps", 1.0)
        val b = DriveEvent(3000L, EventType.BRAKE, 0.45 * g, "gps", 1.0) // within 4 s window
        assertEquals(1, DisplayEvents.clean(listOf(a, b), pts(20_000, 50.0)).size)
    }

    /** The reported bug: distinct events ~10 s apart must NOT merge into one. */
    @Test fun farApartEventsAreNotClustered() {
        val ev = (0..3).map { DriveEvent(it * 10_000L, EventType.CORNER, 0.40 * g, "gps", 1.0) }
        val out = DisplayEvents.clean(ev, pts(40_000, 50.0))
        assertEquals(4, out.size)
    }

    /** A cluster can't stretch past the span cap even if each step is within the time window. */
    @Test fun clusterSpanIsCapped() {
        val ev = listOf(0L, 4000L, 8000L, 12000L).map { DriveEvent(it, EventType.BRAKE, 0.40 * g, "gps", 1.0) }
        // 0-8s = one cluster (span 8s), 12s starts another -> 2 events.
        assertEquals(2, DisplayEvents.clean(ev, pts(20_000, 50.0)).size)
    }

    /** A 0.26 g swerve at 20 km/h is not notable -> filtered out. */
    @Test fun lowSpeedWeakSwerveDropped() {
        val sw = DriveEvent(2000L, EventType.SWERVE, 0.26 * g, "fused", 0.7)
        assertTrue(DisplayEvents.clean(listOf(sw), pts(20_000, 20.0)).isEmpty())
    }

    /** A strong corner at highway speed is kept. */
    @Test fun highSpeedStrongCornerKept() {
        val c = DriveEvent(2000L, EventType.CORNER, 0.45 * g, "fused", 0.8)
        assertEquals(1, DisplayEvents.clean(listOf(c), pts(20_000, 80.0)).size)
    }

    /** Below the walking-pace floor, nothing turn-like is flagged. */
    @Test fun crawlingTurnDropped() {
        val c = DriveEvent(2000L, EventType.CORNER, 0.5 * g, "gps", 1.0)
        assertTrue(DisplayEvents.clean(listOf(c), pts(20_000, 6.0)).isEmpty())
    }

    // ---- Bump-echo veto: pothole-coincident brake/accel contradicted by the GPS speed track ----

    /** Points ramping linearly from one speed to another at ~1 Hz. */
    private fun ramp(durMs: Long, fromKmh: Double, toKmh: Double): List<TrackPoint> {
        val n = durMs / 1000
        return (0..n).map { i ->
            val f = if (n == 0L) 0.0 else i.toDouble() / n
            TrackPoint(i * 1000L, 43.0, -79.0, fromKmh + (toKmh - fromKmh) * f, 0.0, 0.0)
        }
    }

    /** High-conf accel on a bump but with flat GPS speed = the bump's horizontal shake -> dropped. */
    @Test fun highConfAccelOnBumpWithFlatSpeedDropped() {
        val accel = DriveEvent(5000L, EventType.ACCEL, 0.36 * g, "fused", 0.90)
        val bump = DriveEvent(5000L, EventType.POTHOLE, 0.40 * g, "motion", 1.0)
        val out = DisplayEvents.clean(listOf(accel, bump), pts(20_000, 50.0))
        assertTrue("flat-speed accel on a bump should be vetoed", out.none { it.type == EventType.ACCEL })
        assertTrue("the pothole itself still shows", out.any { it.type == EventType.POTHOLE })
    }

    /** Same setup but the GPS speed really climbs -> a genuine accel that happened over a bump is kept.
     *  (Weaker coincident pothole so the kept accel wins the cluster, as in the real field case.) */
    @Test fun highConfAccelOverBumpWithRisingSpeedKept() {
        val accel = DriveEvent(5000L, EventType.ACCEL, 0.36 * g, "fused", 0.90)
        val bump = DriveEvent(5000L, EventType.POTHOLE, 0.34 * g, "motion", 1.0)
        val out = DisplayEvents.clean(listOf(accel, bump), ramp(20_000, 30.0, 60.0))
        assertTrue("a real accel over a bump must survive", out.any { it.type == EventType.ACCEL })
    }

    /** Low-confidence longitudinal on a bump is dropped regardless of slope (original behavior kept). */
    @Test fun lowConfAccelOnBumpDropped() {
        val accel = DriveEvent(5000L, EventType.ACCEL, 0.36 * g, "fused", 0.40)
        val bump = DriveEvent(5000L, EventType.POTHOLE, 0.40 * g, "motion", 1.0)
        val out = DisplayEvents.clean(listOf(accel, bump), ramp(20_000, 30.0, 60.0))
        assertTrue("low-conf accel on a bump is a bump echo", out.none { it.type == EventType.ACCEL })
    }

    /** Away from any bump, a high-conf accel is never second-guessed by this veto, even if flat. */
    @Test fun flatAccelWithNoBumpKept() {
        val accel = DriveEvent(5000L, EventType.ACCEL, 0.36 * g, "fused", 0.90)
        val out = DisplayEvents.clean(listOf(accel), pts(20_000, 50.0))
        assertTrue("away from bumps the veto must not fire", out.any { it.type == EventType.ACCEL })
    }

    /** Bump-coincident but no GPS context to judge the slope -> fail open (keep the event). */
    @Test fun accelOnBumpKeptWhenNoGpsContext() {
        val accel = DriveEvent(5000L, EventType.ACCEL, 0.36 * g, "fused", 0.90)
        val bump = DriveEvent(5000L, EventType.POTHOLE, 0.34 * g, "motion", 1.0)
        val out = DisplayEvents.clean(listOf(accel, bump), pts(2_000, 50.0)) // no points near 5 s
        assertTrue("fail open when the speed track can't judge", out.any { it.type == EventType.ACCEL })
    }
}
