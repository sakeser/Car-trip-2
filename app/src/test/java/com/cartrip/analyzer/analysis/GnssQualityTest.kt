package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GnssQualityTest {

    @Test fun unknownWithoutEnoughSamples() {
        assertEquals(GnssQuality.Level.UNKNOWN, GnssQuality.level(10.0, 40.0, 0))
        assertEquals(GnssQuality.Level.UNKNOWN, GnssQuality.level(0.0, 0.0, 100))
    }

    @Test fun strongOpenSky() {
        assertEquals(GnssQuality.Level.STRONG, GnssQuality.level(12.0, 38.0, 50))
    }

    @Test fun moderateMixed() {
        assertEquals(GnssQuality.Level.MODERATE, GnssQuality.level(6.0, 25.0, 50))
    }

    @Test fun weakUrbanCanyon() {
        assertEquals(GnssQuality.Level.WEAK, GnssQuality.level(4.0, 18.0, 50))
    }

    @Test fun summaryNullWhenUnknownElsePopulated() {
        assertNull(GnssQuality.summary(0.0, 0.0, false, 0))
        val s = GnssQuality.summary(9.0, 33.0, true, 40)
        assertNotNull(s)
        assert(s!!.contains("9 sats") && s.contains("L5"))
    }
}
