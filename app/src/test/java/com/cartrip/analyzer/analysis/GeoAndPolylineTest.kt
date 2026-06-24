package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.cloud.RoutesClient
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoAndPolylineTest {

    @Test fun haversineOneDegreeLatitude() {
        // ~111.19 km per degree of latitude.
        assertEquals(111_195.0, GeoUtils.haversine(0.0, 0.0, 1.0, 0.0), 500.0)
    }

    @Test fun angleDiffWrapsAcrossNorth() {
        assertEquals(-10.0, GeoUtils.angleDiffDeg(355.0, 5.0), 1e-9)
        assertEquals(10.0, GeoUtils.angleDiffDeg(5.0, 355.0), 1e-9)
    }

    /** Google's canonical encoded-polyline example must decode to its three known points. */
    @Test fun decodesGoogleReferencePolyline() {
        val pts = RoutesClient.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0][0], 1e-4); assertEquals(-120.2, pts[0][1], 1e-4)
        assertEquals(40.7, pts[1][0], 1e-4); assertEquals(-120.95, pts[1][1], 1e-4)
        assertEquals(43.252, pts[2][0], 1e-4); assertEquals(-126.453, pts[2][1], 1e-4)
    }

    @Test fun decodesEmptyPolylineToEmpty() {
        assertEquals(0, RoutesClient.decodePolyline("").size)
    }
}
