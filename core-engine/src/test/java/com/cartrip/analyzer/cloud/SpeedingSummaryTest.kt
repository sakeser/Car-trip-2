package com.cartrip.analyzer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure speeding-summary logic (Rev BF): magnitude-weighted severity + limit-drop grace. */
class SpeedingSummaryTest {

    private fun secs(n: Int) = (0 until n).map { it * 1000L }

    /** 1 km/h over the whole trip must NOT be penalized (the bug this rev fixes). */
    @Test fun tinyOverageIsFree() {
        val n = 12
        val r = SpeedLimits.speedingSummary(secs(n), List(n) { 50.0 }, List(n) { 49.0 })
        assertEquals(0.0, r.severity, 1e-9)   // 1 over < tolerance -> no severity
        assertEquals(0.0, r.speedingPct, 1e-9) // also under the 3 km/h "is speeding" tolerance
    }

    /** Severity is the mean of max(0, over-5)^2 over covered time. */
    @Test fun moderateOverageSeverity() {
        val n = 12
        val r = SpeedLimits.speedingSummary(secs(n), List(n) { 60.0 }, List(n) { 50.0 }) // 10 over
        assertEquals(25.0, r.severity, 1e-6) // (10-5)^2
        assertEquals(10.0, r.maxOverKmh, 1e-6)
        assertEquals(1.0, r.speedingPct, 1e-6)
    }

    /** Going far over is super-linearly worse: 20 over is 9x the severity of 10 over, not 2x. */
    @Test fun severityIsSuperLinear() {
        val n = 12
        val ten = SpeedLimits.speedingSummary(secs(n), List(n) { 60.0 }, List(n) { 50.0 }).severity   // (5)^2=25
        val twenty = SpeedLimits.speedingSummary(secs(n), List(n) { 70.0 }, List(n) { 50.0 }).severity // (15)^2=225
        assertEquals(9.0, twenty / ten, 1e-6)
    }

    /** Highway exit: limit drops 100->60 while the car decelerates to match -> the transition grace
     *  forgives it (normal braking to the new limit is not speeding). */
    @Test fun limitDropWhileDeceleratingIsForgiven() {
        val times = secs(10)
        val speeds = listOf(100.0, 100.0, 100.0, 100.0, 100.0, 90.0, 80.0, 70.0, 62.0, 60.0)
        val limits = listOf(100.0, 100.0, 100.0, 100.0, 100.0, 60.0, 60.0, 60.0, 60.0, 60.0)
        val r = SpeedLimits.speedingSummary(times, speeds, limits)
        assertEquals("decel after a limit drop must not be flagged as speeding", 0.0, r.severity, 1e-6)
    }

    /** But STAYING fast well past the grace window in the new lower-limit zone IS penalized. */
    @Test fun sustainedSpeedingAfterDropIsPenalized() {
        val n = 22
        val speeds = (0 until n).map { if (it < 5) 100.0 else 90.0 }
        val limits = (0 until n).map { if (it < 5) 100.0 else 60.0 } // drop at index 5; speed stays 90
        val r = SpeedLimits.speedingSummary(secs(n), speeds, limits)
        // After ~6 s grace, the 90-in-a-60 stretch (30 over) is counted -> large severity.
        assertTrue("sustained speeding past the grace must be penalized (sev=${r.severity})", r.severity > 200.0)
    }

    /** No matched limits -> no coverage, no severity (can't judge). */
    @Test fun noLimitsNoSeverity() {
        val n = 10
        val r = SpeedLimits.speedingSummary(secs(n), List(n) { 60.0 }, List(n) { null })
        assertEquals(0.0, r.coverage, 1e-9)
        assertEquals(0.0, r.severity, 1e-9)
    }
}
