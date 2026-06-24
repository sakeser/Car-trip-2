package com.cartrip.analyzer.ui

import com.cartrip.analyzer.ui.GeoNamer.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure label-composition logic for [GeoNamer] -- no Android Geocoder involved. */
class GeoNamerTest {

    // Build the expected arrow the same pure-ASCII way GeoNamer does, so this test is independent
    // of how the build reads source-file encoding.
    private val arrow = GeoNamer.ARROW

    // ---- describe(): most-specific name wins -----------------------------------------------------

    @Test fun describePrefersNeighbourhoodOverCity() {
        assertEquals("North York", GeoNamer.describe(Spot(neighbourhood = "North York", city = "Toronto")))
    }

    @Test fun describeFallsBackToCityThenRoad() {
        assertEquals("Vaughan", GeoNamer.describe(Spot(city = "Vaughan")))
        assertEquals("Yonge Street", GeoNamer.describe(Spot(road = "Yonge Street")))
    }

    @Test fun describeBlankFieldsAreSkipped() {
        assertEquals("Toronto", GeoNamer.describe(Spot(neighbourhood = "   ", city = "Toronto")))
        assertNull(GeoNamer.describe(Spot(neighbourhood = "", city = " ", road = null)))
        assertNull(GeoNamer.describe(null))
    }

    // ---- compose(): readable place-to-place labels -----------------------------------------------

    @Test fun composeDistinctEndpointsUsesArrow() {
        assertEquals("North York $arrow Scarborough", GeoNamer.compose("North York", "Scarborough"))
        assertEquals("Toronto $arrow Vaughan", GeoNamer.compose("Toronto", "Vaughan"))
    }

    @Test fun composeSameEndpointIsALoop() {
        assertEquals("North York loop", GeoNamer.compose("North York", "North York"))
        // case-insensitive: still a loop, not "A -> A"
        assertEquals("Toronto loop", GeoNamer.compose("Toronto", "toronto"))
    }

    @Test fun composeOneSidedEndpoints() {
        assertEquals("North York drive", GeoNamer.compose("North York", null))
        assertEquals("Drive to Yorkdale", GeoNamer.compose(null, "Yorkdale"))
        assertEquals("North York drive", GeoNamer.compose("North York", "  "))
    }

    /** Both endpoints unnameable -> null, so the caller falls back to TripLabeler. */
    @Test fun composeNothingYieldsNullForFallback() {
        assertNull(GeoNamer.compose(null, null))
        assertNull(GeoNamer.compose("", "   "))
    }

    // ---- cellKey(): quantization for cache hits --------------------------------------------------

    @Test fun cellKeyQuantizesNearbyCoordsTogether() {
        // Two points ~30 m apart in the same ~110 m cell must share a key (one geocode, reused).
        val a = GeoNamer.cellKey(43.76123, -79.41067)
        val b = GeoNamer.cellKey(43.76145, -79.41089)
        assertEquals(a, b)
    }

    @Test fun cellKeySeparatesDistinctCells() {
        val downtown = GeoNamer.cellKey(43.6537, -79.3839)
        val northYork = GeoNamer.cellKey(43.7685, -79.4126)
        assertNotEquals(downtown, northYork)
    }
}
