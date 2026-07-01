package com.cartrip.analyzer.export

import com.cartrip.analyzer.data.AnalysisPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SweepTrackExportTest {

    private fun pt(tripId: Long, t: Long, v: Double, lim: Double = 0.0) = AnalysisPointEntity(
        tripId = tripId, t = t, lat = 0.0, lon = 0.0, speedKmh = v, longAccel = 0.0, latAccel = 0.0, speedLimitKmh = lim
    )

    @Test fun combinedCsvHasHeaderAndGroupsByTrip() {
        val csv = SweepTrackExport.combinedCsv(
            listOf(
                7L to listOf(pt(7, 0, 50.0, 60.0), pt(7, 1000, 48.5, 60.0)),
                9L to listOf(pt(9, 0, 100.0))
            )
        )
        val lines = csv.trim().split("\n")
        assertEquals("tripId,tMs,speedKmh,speedLimitKmh", lines[0])
        assertEquals("7,0,50.000,60.0", lines[1])         // Locale-safe '.' decimal even in comma-locales
        assertEquals("7,1000,48.500,60.0", lines[2])
        assertEquals("9,0,100.000,0.0", lines[3])
        assertEquals(4, lines.size)
    }

    @Test fun emptyTracksJustHeader() {
        assertEquals("tripId,tMs,speedKmh,speedLimitKmh", SweepTrackExport.combinedCsv(emptyList()).trim())
    }
}
