package com.cartrip.analyzer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {

    @Test fun pointsInSameCellShareKey() {
        assertEquals(Tiles.key(43.001, -79.001), Tiles.key(43.009, -79.009))
    }

    @Test fun pointsInDifferentCellsDiffer() {
        assertNotEquals(Tiles.key(43.00, -79.00), Tiles.key(43.05, -79.00))
        assertNotEquals(Tiles.key(43.00, -79.00), Tiles.key(43.00, -79.05))
    }

    @Test fun boundsContainTheirKeyPointAndRoundTrip() {
        val lat = 43.234; val lon = -79.456
        val key = Tiles.key(lat, lon)
        val b = Tiles.bounds(key) // [minLat, minLon, maxLat, maxLon]
        assertTrue(lat in b[0]..b[2] && lon in b[1]..b[3])
        // The tile centre must re-key to the same tile.
        assertEquals(key, Tiles.key((b[0] + b[2]) / 2, (b[1] + b[3]) / 2))
    }

    @Test fun routeTilesAreDeduped() {
        val pts = listOf(43.001 to -79.001, 43.009 to -79.009, 43.05 to -79.00)
        assertEquals(2, Tiles.routeTiles(pts).size)
    }
}
