package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StressScoreTest {

    /** A drive with the v2 stress inputs. maxSpeed 100 km/h so it isn't treated as a non-drive. */
    private fun drive(
        km: Double, durMin: Double, crawl: Double = 0.0, below: Double = 0.0,
        noBreakMin: Double = 0.0, motionEvents: Int = 0, ddSeverity: Double = 0.0,
        maxKmh: Double = 100.0, userIsDrive: Boolean? = null
    ) = TripEntity(
        startTime = 1_000L, endTime = 2_000L,
        distanceM = km * 1000.0, durationS = durMin * 60.0,
        maxSpeedMps = maxKmh / 3.6,
        crawlFraction = crawl, belowLimitLoad = below, longestNoBreakS = noBreakMin * 60.0,
        motionAccelCount = motionEvents, drawdownSeverity = ddSeverity, userIsDrive = userIsDrive
    )

    @Test fun calibration_amCommute_about40() {
        // Real trip 1187: crawl .24, below .27, 18 min longest no-break, 3 events, ddSeverity 15927.
        val s = StressScore.from(
            drive(43.8, 41.1, crawl = 0.243, below = 0.27, noBreakMin = 18.2, motionEvents = 3, ddSeverity = 15927.0)
        )!!
        assertTrue("1187 expected ~40, got ${s.score}", s.score in 37..43)
        assertEquals("Moderate", s.band)
    }

    @Test fun calibration_pmStopAndGoCrawl_about78_andBeatsAm() {
        // Real trip 1189: crawl .41, below .36, 43 min longest no-break, 9 events.
        val pm = StressScore.from(
            drive(45.8, 44.4, crawl = 0.413, below = 0.36, noBreakMin = 42.6, motionEvents = 9, ddSeverity = 4798.0)
        )!!
        assertTrue("1189 expected ~78, got ${pm.score}", pm.score in 74..82)
        assertEquals("High stress", pm.band)
        val am = StressScore.from(
            drive(43.8, 41.1, crawl = 0.243, below = 0.27, noBreakMin = 18.2, motionEvents = 3, ddSeverity = 15927.0)
        )!!
        assertTrue("the crawl must clearly out-rank the smoother AM commute", pm.score > am.score + 20)
    }

    @Test fun smoothEmptyCruise_staysCalm_viaDemandGate() {
        // 45 min unbroken at the limit, no crawl/congestion -> the demand gate zeroes the no-break/duration load.
        val s = StressScore.from(drive(80.0, 45.0, crawl = 0.0, below = 0.0, noBreakMin = 45.0, motionEvents = 2))!!
        assertTrue("a smooth empty cruise should be low, got ${s.score}", s.score < 15)
        assertEquals("Calm", s.band)
    }

    @Test fun congestionRaisesScore_monotonic() {
        fun sc(c: Double) = StressScore.from(drive(40.0, 40.0, crawl = c, below = c, noBreakMin = 40.0))!!.score
        assertTrue(sc(0.5) > sc(0.2))
        assertTrue(sc(0.2) > sc(0.0))
    }

    @Test fun longerUnbrokenGrind_raisesScore_whenCongested() {
        fun sc(noBreak: Double) = StressScore.from(drive(40.0, 60.0, crawl = 0.4, below = 0.4, noBreakMin = noBreak))!!.score
        assertTrue(sc(50.0) > sc(20.0))
    }

    @Test fun nonDriveAndTooShort_returnNull() {
        assertNull(StressScore.from(drive(2.0, 10.0, maxKmh = 5.0)))   // walk (top speed)
        assertNull(StressScore.from(drive(5.0, 10.0, userIsDrive = false))) // explicit walk override
        assertNull(StressScore.from(drive(0.1, 2.0)))                  // too short (distance)
        assertNull(StressScore.from(drive(5.0, 0.5)))                  // too short (duration)
    }

    @Test fun bands() {
        assertEquals("Calm", StressScore.band(10))
        assertEquals("Moderate", StressScore.band(40))
        assertEquals("Busy", StressScore.band(55))
        assertEquals("High stress", StressScore.band(78))
    }

    @Test fun seriesDropsNonDrivesAndIsChronological() {
        val early = drive(5.0, 10.0).copy(startTime = 100L)  // calm
        val late = drive(45.8, 44.4, crawl = 0.41, below = 0.36, noBreakMin = 42.6, motionEvents = 9).copy(startTime = 300L)
        val walk = drive(2.0, 25.0, maxKmh = 5.0).copy(startTime = 200L)
        val s = StressScore.series(listOf(late, walk, early))
        assertEquals(2, s.size)               // walk dropped
        assertTrue("early(calm) should sort before late(high): $s", s[0] < s[1])
    }

    @Test fun trailingAvgSmoothsAndKeepsLength() {
        val out = StressScore.trailingAvg(listOf(10, 20, 30), 2)
        assertEquals(3, out.size)
        assertEquals(10f, out[0], 1e-4f)
        assertEquals(15f, out[1], 1e-4f)
        assertEquals(25f, out[2], 1e-4f)
        assertTrue(StressScore.trailingAvg(emptyList(), 3).isEmpty())
    }

    @Test fun kmWeightedAvg_weightsByDistance() {
        val shortHot = drive(2.0, 40.0, crawl = 0.5, below = 0.5, noBreakMin = 40.0, motionEvents = 10)
        val longCalm = drive(60.0, 50.0, crawl = 0.0, below = 0.0, noBreakMin = 5.0)
        val avg = StressScore.kmWeightedAvg(listOf(shortHot, longCalm))!!
        val plain = listOf(shortHot, longCalm).mapNotNull { StressScore.from(it)?.score }.average()
        assertTrue("the long calm drive should pull the km-weighted avg ($avg) below the plain mean ($plain)", avg < plain)
        assertNull(StressScore.kmWeightedAvg(emptyList()))
    }
}
