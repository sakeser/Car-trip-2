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
}
