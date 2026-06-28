package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.ui.EventHotspots.Ev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHotspotsTest {

    private val lat = 43.7578; private val lon = -79.4035

    /** Same kind, same place, on two distinct trips -> a hotspot. */
    @Test fun recurringAcrossTripsIsAHotspot() {
        val h = EventHotspots.find(listOf(
            Ev(1, "Rough spot", lat, lon),
            Ev(2, "Rough spot", lat + 0.0001, lon),  // ~11 m away, same cell
        ))
        assertEquals(1, h.size)
        assertEquals("Rough spot", h[0].kind)
        assertEquals(2, h[0].trips)
    }

    /** Multiple events at one spot but all on the SAME trip -> not a hotspot (needs distinct trips). */
    @Test fun sameTripRepeatsDoNotCount() {
        val h = EventHotspots.find(listOf(
            Ev(1, "Hard braking", lat, lon),
            Ev(1, "Hard braking", lat, lon),
            Ev(1, "Hard braking", lat, lon),
        ))
        assertTrue(h.isEmpty())
    }

    /** A spot below the distinct-trip threshold isn't surfaced. */
    @Test fun belowThresholdNotSurfaced() {
        val h = EventHotspots.find(listOf(Ev(1, "Sharp turn", lat, lon)))
        assertTrue(h.isEmpty())
    }

    /** Different places don't merge. */
    @Test fun differentPlacesAreSeparate() {
        val h = EventHotspots.find(listOf(
            Ev(1, "Rough spot", lat, lon), Ev(2, "Rough spot", lat, lon),       // home
            Ev(1, "Rough spot", 43.6537, -79.3839), Ev(2, "Rough spot", 43.6537, -79.3839), // downtown
        ))
        assertEquals(2, h.size)
    }

    /** Corner and swerve are the same human "kind" (a sharp turn), so they reinforce one hotspot. */
    @Test fun cornerAndSwerveGroupAsSharpTurn() {
        assertEquals("Sharp turn", EventHotspots.kindOf(EventType.CORNER))
        assertEquals("Sharp turn", EventHotspots.kindOf(EventType.SWERVE))
        val h = EventHotspots.find(listOf(
            Ev(1, EventHotspots.kindOf(EventType.SWERVE), lat, lon),
            Ev(2, EventHotspots.kindOf(EventType.CORNER), lat, lon),
        ))
        assertEquals(1, h.size)
        assertEquals("Sharp turn", h[0].kind)
        assertEquals(2, h[0].trips)
    }

    /** Strongest (most distinct trips) is listed first. */
    @Test fun sortedByDistinctTrips() {
        val h = EventHotspots.find(listOf(
            Ev(1, "Sharp turn", lat, lon), Ev(2, "Sharp turn", lat, lon),                 // 2 trips
            Ev(1, "Rough spot", 43.50, -79.60), Ev(2, "Rough spot", 43.50, -79.60),
            Ev(3, "Rough spot", 43.50, -79.60),                                            // 3 trips
        ))
        assertEquals("Rough spot", h.first().kind)
        assertEquals(3, h.first().trips)
    }
}
