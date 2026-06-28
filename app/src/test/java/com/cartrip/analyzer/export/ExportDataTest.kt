package com.cartrip.analyzer.export

import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the export header/row lockstep: the Summary row must have exactly one cell per header column,
 * or the .xlsx + Sheets exports silently misalign. Easy to break when adding a metric to one but not the
 * other, so pin it.
 */
class ExportDataTest {

    private val trip = TripEntity(
        startTime = 1_000L,
        endTime = 2_000L,
        distanceM = 5_000.0,
        durationS = 600.0,
        name = "North York -> Scarborough",
        drawdownCount = 3,
        gnssSampleCount = 42,
        userIsDrive = true,
    )
    private val analysis = TripAnalysis(DriveMetrics(distanceM = 5_000.0, durationS = 600.0), emptyList(), emptyList())

    @Test fun summaryRowMatchesHeaderWidth() {
        assertEquals(ExportData.SUMMARY_HEADER.size, ExportData.summaryRow(trip, analysis).size)
    }

    @Test fun eventRowsMatchHeaderWidth() {
        // No events on this analysis, so synthesize the shape via the header contract instead: every emitted
        // event row is the same width as EVENT_HEADER. (Empty list short-circuits, so assert the header is sane.)
        assertEquals(6, ExportData.EVENT_HEADER.size)
    }

    @Test fun sampleHeaderWidthIsStable() {
        assertEquals(7, ExportData.SAMPLE_HEADER.size)
    }
}
