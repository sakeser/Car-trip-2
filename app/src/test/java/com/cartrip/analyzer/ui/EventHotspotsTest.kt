package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.ui.EventHotspots.Ev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHotspotsTest {

    private val lat = 43.7578; private val lon = -79.4035
    private fun ev(trip: Long, kind: String, la: Double = lat, lo: Double = lon, g: Double = 0.5) =
        Ev(trip, kind, la, lo, gForce = g)

    /** Same kind, same place, on enough distinct trips/instances -> a hotspot. */
    @Test fun recurringAcrossTripsIsAHotspot() {
        val h = EventHotspots.find(listOf(
            ev(1, "Rough spot"),
            ev(2, "Rough spot", la = lat + 0.0001),  // ~11 m away, same cell
            ev(3, "Rough spot"),
        ))
        assertEquals(1, h.size)
        assertEquals("Rough spot", h[0].kind)
        assertEquals(3, h[0].trips)
    }

    /** Many events at one spot but all on the SAME trip -> not a hotspot (needs distinct trips). */
    @Test fun sameTripRepeatsDoNotCount() {
        val h = EventHotspots.find(listOf(
            ev(1, "Hard braking"), ev(1, "Hard braking"), ev(1, "Hard braking"),
        ))
        assertTrue(h.isEmpty())
    }

    /** Fewer than MIN_INSTANCES occurrences -> not surfaced even across distinct trips. */
    @Test fun belowInstanceThresholdNotSurfaced() {
        assertTrue(EventHotspots.find(listOf(ev(1, "Sharp turn"))).isEmpty())
        assertTrue(EventHotspots.find(listOf(ev(1, "Sharp turn"), ev(2, "Sharp turn"))).isEmpty())
    }

    /** The g-force floor drops weak/marginal events (e.g. a 0.16 g "swerve"). */
    @Test fun gForceFloorDropsWeakEvents() {
        val weak = (1L..4L).map { ev(it, "Sharp turn", g = 0.16) }
        assertTrue(EventHotspots.find(weak, gForceFloor = 0.35).isEmpty())
        val strong = (1L..4L).map { ev(it, "Sharp turn", g = 0.50) }
        assertEquals(1, EventHotspots.find(strong, gForceFloor = 0.35).size)
    }

    /** Different places don't merge. */
    @Test fun differentPlacesAreSeparate() {
        val h = EventHotspots.find(listOf(
            ev(1, "Rough spot"), ev(2, "Rough spot"), ev(3, "Rough spot"),                       // home
            ev(1, "Rough spot", 43.6537, -79.3839), ev(2, "Rough spot", 43.6537, -79.3839),
            ev(3, "Rough spot", 43.6537, -79.3839),                                               // downtown
        ))
        assertEquals(2, h.size)
    }

    /** Corner and swerve are the same human "kind" (a sharp turn), so they reinforce one hotspot. */
    @Test fun cornerAndSwerveGroupAsSharpTurn() {
        assertEquals("Sharp turn", EventHotspots.kindOf(EventType.CORNER))
        assertEquals("Sharp turn", EventHotspots.kindOf(EventType.SWERVE))
        val h = EventHotspots.find(listOf(
            ev(1, EventHotspots.kindOf(EventType.SWERVE)),
            ev(2, EventHotspots.kindOf(EventType.CORNER)),
            ev(3, EventHotspots.kindOf(EventType.CORNER)),
        ))
        assertEquals(1, h.size)
        assertEquals("Sharp turn", h[0].kind)
        assertEquals(3, h[0].trips)
    }

    /** Strongest (most distinct trips) is listed first. */
    @Test fun sortedByDistinctTrips() {
        val h = EventHotspots.find(listOf(
            ev(1, "Sharp turn"), ev(2, "Sharp turn"), ev(3, "Sharp turn"),                 // 3 trips
            ev(1, "Rough spot", 43.50, -79.60), ev(2, "Rough spot", 43.50, -79.60),
            ev(3, "Rough spot", 43.50, -79.60), ev(4, "Rough spot", 43.50, -79.60),        // 4 trips
        ))
        assertEquals("Rough spot", h.first().kind)
        assertEquals(4, h.first().trips)
    }
}
