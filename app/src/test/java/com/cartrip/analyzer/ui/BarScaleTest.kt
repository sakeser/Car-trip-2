package com.cartrip.analyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarScaleTest {

    @Test
    fun niceAxisMax_roundsUpWithinTier() {
        assertEquals(10.0, BarScale.niceAxisMax(8.0, headroom = 1.25), 0.0)   // ->10, step 1
        assertEquals(9.0, BarScale.niceAxisMax(8.2, headroom = 1.0), 0.0)     // step 1
        assertEquals(15.0, BarScale.niceAxisMax(12.5, headroom = 1.0), 0.0)   // step 5
        assertEquals(60.0, BarScale.niceAxisMax(52.0, headroom = 1.0), 0.0)   // step 10
        assertEquals(150.0, BarScale.niceAxisMax(125.0, headroom = 1.0), 0.0) // step 30
    }

    @Test
    fun niceAxisMax_alwaysCoversDataMaxTimesHeadroom() {
        for (v in listOf(0.5, 3.0, 9.7, 22.0, 47.0, 95.0, 140.0, 300.0)) {
            val axis = BarScale.niceAxisMax(v, headroom = 1.15)
            assertTrue("axis $axis should cover $v * 1.15", axis >= v * 1.15 - 1e-9)
        }
    }

    @Test
    fun niceAxisMax_nonPositiveStaysPositive() {
        assertEquals(1.0, BarScale.niceAxisMax(0.0), 0.0)
        assertEquals(1.0, BarScale.niceAxisMax(-5.0), 0.0)
    }

    @Test
    fun fillFraction_basic() {
        assertEquals(0.5f, BarScale.fillFraction(30.0, 60.0), 1e-6f)
        assertEquals(1.0f, BarScale.fillFraction(100.0, 60.0), 1e-6f)   // clamped at full
    }

    @Test
    fun fillFraction_floorsTinyPositive() {
        assertEquals(0.03f, BarScale.fillFraction(1.0, 1000.0, 0.03f), 1e-6f)
    }

    @Test
    fun fillFraction_zeroForNonPositiveInputs() {
        assertEquals(0f, BarScale.fillFraction(0.0, 60.0), 0f)
        assertEquals(0f, BarScale.fillFraction(-5.0, 60.0), 0f)
        assertEquals(0f, BarScale.fillFraction(30.0, 0.0), 0f)         // non-positive axis
    }

    @Test
    fun longestDurationBarIsNotEdgeToEdge() {
        // The list's duration axis = niceAxisMax(maxMinutes, 1.15) * 60; the longest trip should fill a
        // strong majority of the track but never the whole thing (no edge-to-edge).
        for (maxMin in listOf(8.0, 23.0, 45.0, 78.0, 120.0, 240.0)) {
            val axisS = BarScale.niceAxisMax(maxMin, headroom = 1.15) * 60.0
            val longest = BarScale.fillFraction(maxMin * 60.0, axisS)
            assertTrue("maxMin=$maxMin filled $longest should be <= 0.90", longest <= 0.90f)
            assertTrue("maxMin=$maxMin filled $longest should be >= 0.60", longest >= 0.60f)
        }
    }

    @Test
    fun reproducesLegacyEtaAxisExactly() {
        // niceEtaAxisMaxMin now delegates to BarScale.niceAxisMax(_, 1.25); it must match the old inline
        // tiered logic exactly so the you-vs-traffic bar stays visually identical.
        fun legacy(dataMin: Double): Double {
            val target = (dataMin * 1.25).coerceAtLeast(1.0)
            val step = when {
                target <= 10 -> 1.0
                target <= 30 -> 5.0
                target <= 120 -> 10.0
                else -> 30.0
            }
            return kotlin.math.ceil(target / step) * step
        }
        for (v in listOf(0.0, 1.0, 4.0, 8.0, 12.0, 19.0, 25.0, 50.0, 95.0, 130.0, 400.0)) {
            assertEquals("eta axis for $v", legacy(v), BarScale.niceAxisMax(v, 1.25), 0.0)
        }
    }
}
