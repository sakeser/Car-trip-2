package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayEventsTest {
    private val g = 9.80665
    private fun pts(): List<TrackPoint> = (0..20).map { TrackPoint(it * 1000L, 43.0, -79.0, 50.0, 0.0, 0.0) }

    @Test fun dropsSubThresholdEvents() {
        val weak = DriveEvent(1000L, EventType.BRAKE, 0.1 * g, "gps", 1.0)
        assertTrue(DisplayEvents.clean(listOf(weak), pts()).isEmpty())
    }

    @Test fun keepsStrongEvent() {
        val strong = DriveEvent(1000L, EventType.BRAKE, 0.45 * g, "gps", 1.0)
        val out = DisplayEvents.clean(listOf(strong), pts())
        assertEquals(1, out.size)
        assertEquals(EventType.BRAKE, out.first().type)
    }

    @Test fun clustersNearbyEventsIntoOne() {
        val a = DriveEvent(1000L, EventType.BRAKE, 0.40 * g, "gps", 1.0)
        val b = DriveEvent(3000L, EventType.BRAKE, 0.45 * g, "gps", 1.0) // within 6 s window
        assertEquals(1, DisplayEvents.clean(listOf(a, b), pts()).size)
    }
}
