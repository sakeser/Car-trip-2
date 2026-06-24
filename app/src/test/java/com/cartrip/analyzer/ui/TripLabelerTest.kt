package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLabelerTest {

    private val generic = setOf("Morning drive", "Afternoon drive", "Evening drive", "Night drive")

    private fun pt(lat: Double, lon: Double, t: Long) =
        AnalysisPointEntity(tripId = 1, t = t, lat = lat, lon = lon, speedKmh = 50.0, longAccel = 0.0, latAccel = 0.0)

    private fun trip() = TripEntity(startTime = 1_700_000_000_000L, endTime = 1_700_000_600_000L)

    /** A trip far outside the known area must NOT be labelled with a stray GTA landmark. */
    @Test fun farAwayTripGetsGenericLabel() {
        val pts = listOf(pt(49.2827, -123.1207, 0L), pt(49.3000, -123.1000, 60_000L)) // Vancouver
        val label = TripLabeler.label(trip(), pts)
        assertTrue("got '$label'", label in generic)
    }

    /** A trip between two known GTA places is named place-to-place. */
    @Test fun knownPlacesAreNamed() {
        val pts = listOf(pt(43.6537, -79.3839, 0L), pt(43.7255, -79.4523, 60_000L)) // Downtown -> Yorkdale
        val label = TripLabeler.label(trip(), pts)
        assertTrue("got '$label'", label.contains(" to ") && label !in generic)
    }
}
