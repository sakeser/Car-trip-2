package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopAndGoTest {
    /** A 1 Hz track at the given speeds (km/h), constant posted limit. */
    private fun track(speeds: List<Double>, limit: Double = 100.0): List<TrackPoint> =
        speeds.mapIndexed { i, s -> TrackPoint(i * 1000L, 0.0, 0.0, s, 0.0, 0.0, limit) }

    @Test fun sustainedCrawl_highOnEverySignalButRests() {
        // 120 s crawling at 30 in a 100 zone, never stopping -> the stop-and-go signature.
        val r = StopAndGo.analyze(track(List(120) { 30.0 }, limit = 100.0))
        assertEquals(1.0, r.crawlFraction, 1e-6)        // all moving time is < 40
        assertEquals(0.70, r.belowLimitLoad, 0.02)      // 1 - 30/100
        assertEquals(0, r.restCount)                    // never at rest
        assertTrue("longest no-break ~119 s", r.longestNoBreakS >= 115.0)
    }

    @Test fun highwayCruiseWithTwoLightStops_breaksTheGrind() {
        // cruise 100, full stop 14 s, cruise, full stop 14 s, cruise (limit 100).
        val speeds = List(60) { 100.0 } + List(15) { 0.0 } + List(60) { 100.0 } + List(15) { 0.0 } + List(60) { 100.0 }
        val r = StopAndGo.analyze(track(speeds, limit = 100.0))
        assertEquals(0.0, r.crawlFraction, 1e-6)        // cruise, no crawl
        assertEquals(0.0, r.belowLimitLoad, 0.02)       // at the limit
        assertEquals(2, r.restCount)                    // two >= 10 s stops
        assertTrue("longest grind ~60 s", r.longestNoBreakS in 55.0..66.0)
    }

    @Test fun stopAndGoCreepsAreNotBreaks() {
        // crawl 30, dip to 2 km/h for only 5 s (a creep, < REST_MIN_S), crawl again -> NOT a rest.
        val speeds = List(20) { 30.0 } + List(5) { 2.0 } + List(20) { 30.0 } + List(5) { 2.0 } + List(20) { 30.0 }
        val r = StopAndGo.analyze(track(speeds, limit = 100.0))
        assertEquals(0, r.restCount)                    // 5 s dips don't qualify
        assertTrue("one long unbroken grind", r.longestNoBreakS >= 65.0)
        assertTrue("mostly crawling", r.crawlFraction >= 0.95)
    }

    @Test fun atOrAboveLimit_zeroBelowLimitLoad() {
        val r = StopAndGo.analyze(track(List(60) { 110.0 }, limit = 100.0))  // over the limit
        assertEquals(0.0, r.belowLimitLoad, 1e-6)
        assertEquals(0.0, r.crawlFraction, 1e-6)
    }

    @Test fun emptyOrSingle_isEmpty() {
        assertEquals(StopAndGo.Result.EMPTY, StopAndGo.analyze(emptyList()))
        assertEquals(StopAndGo.Result.EMPTY, StopAndGo.analyze(track(listOf(50.0))))
    }

    @Test fun noLimitData_belowLimitLoadZeroButCrawlStillSeen() {
        val r = StopAndGo.analyze(track(List(60) { 30.0 }, limit = 0.0))   // unknown limit
        assertEquals(0.0, r.belowLimitLoad, 1e-6)
        assertEquals(1.0, r.crawlFraction, 1e-6)
    }
}
