package com.cartrip.analyzer.analysis

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverLoadTest {

    private val H = 3_600_000L

    /** A stressful crawl (high stress) at [startMs]. Inputs mirror a real stop-and-go trip (~78 stress). */
    private fun crawl(startMs: Long, durMin: Double = 44.0) = TripEntity(
        startTime = startMs, endTime = startMs + (durMin * 60_000).toLong(),
        distanceM = 45.0 * 1000.0, durationS = durMin * 60.0,
        maxSpeedMps = 100.0 / 3.6,
        crawlFraction = 0.41, belowLimitLoad = 0.36, longestNoBreakS = 42.0 * 60.0,
        motionAccelCount = 9
    )

    /** A calm, short hop (low stress). */
    private fun hop(startMs: Long, durMin: Double = 6.0) = TripEntity(
        startTime = startMs, endTime = startMs + (durMin * 60_000).toLong(),
        distanceM = 3.0 * 1000.0, durationS = durMin * 60.0, maxSpeedMps = 60.0 / 3.6
    )

    private fun walk(startMs: Long) = TripEntity(
        startTime = startMs, endTime = startMs + 600_000L,
        distanceM = 800.0, durationS = 600.0, maxSpeedMps = 5.0 / 3.6
    )

    @Test fun impulseIsStressWeightedDrivingTime() {
        val t = crawl(0L)
        val s = StressScore.from(t)!!.score
        val expected = (s / 100.0) * (t.durationS / 3600.0)
        assertEquals(expected, DriverLoad.impulse(t)!!, 1e-9)
        // A calm hop deposits far less than a stressful crawl.
        assertTrue(DriverLoad.impulse(hop(0L))!! < DriverLoad.impulse(crawl(0L))!!)
        // Non-drives don't deposit load at all.
        assertNull(DriverLoad.impulse(walk(0L)))
    }

    @Test fun loadDecaysWithTimeSinceTheDrive() {
        val now = 100L * 24 * H
        val recent = DriverLoad.rawLoadAt(listOf(crawl(now - 1 * H)), now)
        val old = DriverLoad.rawLoadAt(listOf(crawl(now - 72 * H)), now)
        assertTrue("recent load should exceed a 3-day-old one", recent > old)
        // 3 days (~72 h) old should have decayed to a small fraction (TAU 28.8 h -> exp(-72/28.8) ~ 0.08).
        assertTrue("3-day-old load should be ~8% of fresh", old < recent * 0.12)
    }

    @Test fun loadIsAdditiveAcrossDrives() {
        val now = 50L * 24 * H
        val one = DriverLoad.rawLoadAt(listOf(crawl(now - 2 * H)), now)
        val two = DriverLoad.rawLoadAt(listOf(crawl(now - 2 * H), crawl(now - 1 * H)), now)
        assertTrue("a second recent drive raises the load", two > one)
    }

    @Test fun futureTripsDoNotContribute() {
        val now = 10L * 24 * H
        val withFuture = DriverLoad.rawLoadAt(listOf(crawl(now + 5 * H)), now)
        assertEquals(0.0, withFuture, 1e-12)
    }

    @Test fun scaleSaturatesAndIsMonotonic() {
        assertEquals(0, DriverLoad.scale(0.0))
        assertTrue(DriverLoad.scale(0.5) < DriverLoad.scale(1.5))
        assertTrue(DriverLoad.scale(100.0) <= 100)
        assertTrue(DriverLoad.scale(100.0) >= 95) // deeply saturated
    }

    @Test fun stateReadinessIsInverseAndForecastDecaysToBaseline() {
        val now = 30L * 24 * H
        // A heavy recent cluster -> elevated load.
        val trips = listOf(crawl(now - 6 * H), crawl(now - 2 * H), crawl(now - 1 * H))
        val st = DriverLoad.state(trips, now)
        assertEquals(100 - st.load, st.readiness)
        assertTrue("heavy cluster should be at least Moderate", st.load >= 40)
        // Recovery curve: FORECAST_HOURS + 1 points, monotonically non-increasing, first == current load.
        assertEquals(DriverLoad.FORECAST_HOURS + 1, st.recovery.size)
        assertEquals(st.load.toFloat(), st.recovery.first(), 0.5f)
        for (i in 1 until st.recovery.size) {
            assertTrue("forecast must not increase", st.recovery[i] <= st.recovery[i - 1] + 1e-3f)
        }
        // It takes some hours to return to baseline from an elevated load.
        assertTrue("should need recovery time", (st.hoursToBaseline ?: 0) > 0)
    }

    @Test fun restedDriverIsAtBaselineNow() {
        val now = 60L * 24 * H
        // One calm hop two weeks ago -> negligible residual load now.
        val st = DriverLoad.state(listOf(hop(now - 14 * 24 * H)), now)
        assertTrue("stale calm history -> low load", st.load < DriverLoad.BASELINE_LOAD)
        assertEquals(0, st.hoursToBaseline)
        assertEquals("Low", st.band)
    }

    @Test fun emptyHistoryIsZeroLoad() {
        val st = DriverLoad.state(emptyList(), 0L)
        assertEquals(0, st.load)
        assertEquals(100, st.readiness)
        assertEquals(0, st.drivesCounted)
        assertNull(st.acwr)
    }

    // --- Phase 2: ACWR ---

    private fun dailyCrawls(now: Long, days: Int): List<TripEntity> =
        (1..days).map { crawl(now - it.toLong() * 24 * H) }

    @Test fun acwrNullWithoutEnoughHistory() {
        val now = 50L * 24 * H
        // Only 5 days of history — below ACWR_MIN_HISTORY_HOURS (14 d) — so no chronic baseline yet.
        assertNull(DriverLoad.acwr(dailyCrawls(now, 5), now))
        assertNull(DriverLoad.state(dailyCrawls(now, 5), now).acwr)
    }

    @Test fun acwrNearOneForSteadyDriving() {
        val now = 200L * 24 * H
        // 40 days of identical daily driving -> recent matches baseline -> ratio ~1 (no overload).
        val r = DriverLoad.state(dailyCrawls(now, 40), now).acwr!!
        assertTrue("steady driving should read ~1, got $r", r in 0.8f..1.25f)
        assertEquals("In line with your recent norm", DriverLoad.acwrLabel(r))
    }

    @Test fun acwrRisesWithARecentSpike() {
        val now = 200L * 24 * H
        // A light, regular baseline: one drive every 3 days for ~7 weeks.
        val baseline = (1..16).map { crawl(now - it.toLong() * 72 * H) }
        val steady = DriverLoad.acwr(baseline, now)!!
        // A sudden burst on top of it: a drive every 12 h across the last 4 days.
        val spike = baseline + (1..8).map { crawl(now - it.toLong() * 12 * H) }
        val spiked = DriverLoad.acwr(spike, now)!!
        assertTrue("a recent burst must raise ACWR (steady=$steady spiked=$spiked)", spiked > steady + 0.25f)
        assertTrue("a real recent overload clears the high threshold (got $spiked)", spiked > DriverLoad.ACWR_HIGH)
        assertEquals("Well above your recent norm", DriverLoad.acwrLabel(spiked))
    }

    @Test fun acwrLabelThresholds() {
        assertNull(DriverLoad.acwrLabel(null))
        assertEquals("Well above your recent norm", DriverLoad.acwrLabel(1.8f))
        assertEquals("A bit above your recent norm", DriverLoad.acwrLabel(1.35f))
        assertEquals("In line with your recent norm", DriverLoad.acwrLabel(1.0f))
        assertEquals("Below your recent norm", DriverLoad.acwrLabel(0.6f))
    }
}
