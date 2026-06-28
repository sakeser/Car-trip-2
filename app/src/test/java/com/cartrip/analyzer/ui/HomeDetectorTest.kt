package com.cartrip.analyzer.ui

import com.cartrip.analyzer.ui.HomeDetector.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDetectorTest {

    private val home = LatLon(43.7597, -79.4106)            // the dominant endpoint
    private val scarborough = LatLon(43.7764, -79.2574)
    private val downtown = LatLon(43.6537, -79.3839)
    private val vaughan = LatLon(43.8256, -79.5396)

    /** Home is the most frequent endpoint, with small day-to-day parking variance. */
    @Test fun detectsTheDominantEndpointCluster() {
        val eps = listOf(
            // home appears as an endpoint of many trips, each parked slightly differently (<200 m)
            LatLon(43.7596, -79.4107), LatLon(43.7598, -79.4105), LatLon(43.7597, -79.4108),
            LatLon(43.7599, -79.4104), LatLon(43.7596, -79.4106), LatLon(43.7598, -79.4107),
            // the other ends, each seen once or twice
            scarborough, downtown, vaughan, scarborough
        )
        val h = HomeDetector.detect(eps)
        assertNotNull(h)
        // centroid lands at the home cluster, not the scattered destinations
        assertEquals(43.7597, h!!.lat, 0.0005)
        assertEquals(-79.4106, h.lon, 0.0005)
    }

    /** Too little history → don't guess a home (a one-off spot must not become "Home"). */
    @Test fun nullWhenTooFewEndpoints() {
        assertNull(HomeDetector.detect(listOf(home, scarborough)))
    }

    /** No spot is frequent enough (all distinct) → null. */
    @Test fun nullWhenNoFrequentCluster() {
        val eps = listOf(home, scarborough, downtown, vaughan,
            LatLon(43.70, -79.30), LatLon(43.65, -79.50))
        assertNull(HomeDetector.detect(eps))
    }

    /** A home cluster straddling a grid-cell boundary is still counted whole (radius refinement). */
    @Test fun clusterSplitAcrossCellBoundaryIsCountedWhole() {
        // Points spread ~0..180 m around a centre that sits near a cell edge — a pure grid would split
        // them across two cells; the refinement re-gathers them within the radius.
        val ctr = LatLon(43.7582, -79.4039)
        val eps = (0 until 8).map { i ->
            LatLon(ctr.lat + (i - 4) * 0.0003, ctr.lon + (i - 4) * 0.0003)  // ~ +/- 130 m spread
        } + listOf(scarborough, downtown)  // a couple of distant one-offs
        val h = HomeDetector.detect(eps)
        assertNotNull(h)
        assertEquals(ctr.lat, h!!.lat, 0.0008)
        assertEquals(ctr.lon, h.lon, 0.0008)
    }

    /** Work = the most frequent endpoint that isn't home, well clear of home. */
    @Test fun detectsWorkAsSecondCluster() {
        val eps = ArrayList<LatLon>()
        repeat(8) { eps += LatLon(43.7597 + it * 0.0001, -79.4106) } // home cluster (8)
        repeat(5) { eps += LatLon(43.5160 + it * 0.0001, -79.6712) } // work cluster (5), ~34 km away
        eps += downtown; eps += scarborough                          // one-offs
        val home = HomeDetector.detect(eps)
        val work = HomeDetector.detectWork(eps, home)
        assertNotNull(work)
        assertEquals(43.5160, work!!.lat, 0.001)
        assertEquals(-79.6712, work.lon, 0.001)
    }

    /** No second place stands out (only home recurs) → no work. */
    @Test fun nullWorkWhenNoSecondCluster() {
        val eps = ArrayList<LatLon>()
        repeat(8) { eps += LatLon(43.7597 + it * 0.0001, -79.4106) }
        eps += downtown; eps += scarborough; eps += vaughan  // all one-offs
        val home = HomeDetector.detect(eps)
        assertNull(HomeDetector.detectWork(eps, home))
    }

    /** A frequent second spot too close to home is home spillover, not work. */
    @Test fun nullWorkWhenSecondClusterIsNearHome() {
        val eps = ArrayList<LatLon>()
        repeat(8) { eps += LatLon(43.7597, -79.4106) }
        repeat(6) { eps += LatLon(43.7601, -79.4150) } // ~360 m away — under WORK_MIN_FROM_HOME_M
        val home = HomeDetector.detect(eps)
        assertNull(HomeDetector.detectWork(eps, home))
    }

    @Test fun isHomeWithinRadius() {
        // ~100 m north of home -> home
        assertTrue(HomeDetector.isHome(43.7597 + 0.0009, -79.4106, home))
        // ~550 m away -> not home
        assertFalse(HomeDetector.isHome(43.7597 + 0.005, -79.4106, home))
        // unknown home -> never home
        assertFalse(HomeDetector.isHome(43.7597, -79.4106, null))
    }
}
