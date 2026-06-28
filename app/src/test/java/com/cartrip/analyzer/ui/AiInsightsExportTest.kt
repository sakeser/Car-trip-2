package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightsExportTest {

    private val v = FuelEstimator.DEFAULT

    private fun drive(km: Double, durationS: Double, start: Long) = TripEntity(
        startTime = start, endTime = start + 1,
        distanceM = km * 1000.0, durationS = durationS,
        avgMovingSpeedMps = 60 / 3.6, maxSpeedMps = 100 / 3.6,
        analyzed = true,
    )

    @Test fun buildsCoachingPromptWithSections() {
        val day = 24L * 3600 * 1000
        val trips = listOf(drive(40.0, 2400.0, day), drive(5.0, 600.0, 2 * day))
        val md = AiInsightsExport.build(trips, emptyMap(), v, emptyList(), 3 * day)
        assertTrue(md.startsWith("# My driving data"))
        assertTrue(md.contains("driving coach"))
        assertTrue(md.contains("## Overview"))
        assertTrue(md.contains("Drives: 2"))
        assertTrue(md.contains("## Averages"))
        assertTrue(md.contains("## Recent drives"))
    }

    @Test fun emptyWhenNoDrives() {
        val md = AiInsightsExport.build(emptyList(), emptyMap(), v, emptyList(), 0L)
        assertTrue(md.contains("No analyzed drives"))
    }

    @Test fun walksExcludedFromCount() {
        val day = 24L * 3600 * 1000
        val walk = TripEntity(
            startTime = day, endTime = day + 1, distanceM = 1500.0, durationS = 900.0,
            avgMovingSpeedMps = 4.5 / 3.6, maxSpeedMps = 5.0 / 3.6, analyzed = true,
        )
        val md = AiInsightsExport.build(listOf(drive(40.0, 2400.0, 2 * day), walk), emptyMap(), v, emptyList(), 3 * day)
        assertTrue(md.contains("Drives: 1"))
    }
}
