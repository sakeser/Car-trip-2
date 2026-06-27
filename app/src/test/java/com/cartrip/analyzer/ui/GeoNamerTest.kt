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

    @Test fun composeSameEndpointWithoutViaIsLoopOrDrive() {
        // A true round trip that genuinely stayed in one area (no distinct farthest point).
        assertEquals("North York loop", GeoNamer.compose("North York", "North York", roundTrip = true))
        // Same coarse name but not a physical round trip -> a "drive", not a "loop".
        assertEquals("Toronto drive", GeoNamer.compose("Toronto", "toronto"))
    }

    @Test fun composeSameEndpointNamedByFarthestVia() {
        // The fix for "North York loop": name the trip by where it actually went.
        assertEquals(
            "North York $arrow Scarborough $arrow back",
            GeoNamer.compose("North York", "North York", via = "Scarborough", roundTrip = true)
        )
        // Same coarse area, ended elsewhere (not a physical loop) -> no "back".
        assertEquals(
            "North York $arrow Scarborough",
            GeoNamer.compose("North York", "North York", via = "Scarborough", roundTrip = false)
        )
        // A via that resolves to the same name as the endpoints adds nothing -> plain loop.
        assertEquals(
            "North York loop",
            GeoNamer.compose("North York", "North York", via = "North York", roundTrip = true)
        )
    }

    @Test fun composeHomeEndpointsReadCleanly() {
        assertEquals("Home $arrow Scarborough", GeoNamer.compose("Home", "Scarborough"))
        assertEquals("Scarborough $arrow Home", GeoNamer.compose("Scarborough", "Home"))
        assertEquals(
            "Home $arrow Vaughan $arrow back",
            GeoNamer.compose("Home", "Home", via = "Vaughan", roundTrip = true)
        )
    }

    @Test fun composeOneSidedEndpoints() {
        assertEquals("North York drive", GeoNamer.compose("North York", null))
        assertEquals("Drive to Yorkdale", GeoNamer.compose(null, "Yorkdale"))
        assertEquals("North York drive", GeoNamer.compose("North York", "  "))
        // A one-sided endpoint can still be named by its farthest point.
        assertEquals("Home $arrow Barrie", GeoNamer.compose("Home", null, via = "Barrie"))
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
