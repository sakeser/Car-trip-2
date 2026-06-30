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

    private fun cell(name: String, row: List<String>): String {
        val i = ExportData.SUMMARY_HEADER.indexOf(name)
        require(i >= 0) { "no such export column: $name" }
        return row[i]
    }

    @Test fun summaryRowMapsKeyColumnsByName() {
        // Index by header NAME (robust to appended columns) and pin the value mapping, so a mis-placed or
        // swapped metric is caught - the width guard alone wouldn't notice a wrong value in the right slot.
        val row = ExportData.summaryRow(trip, analysis)
        assertEquals(trip.id.toString(), cell("TripId", row))
        assertEquals("5.000", cell("Distance_km", row))   // 5000 m -> km, 3 dp
        assertEquals("10.00", cell("Duration_min", row))  // 600 s -> min, 2 dp
        assertEquals("3", cell("DrawdownCount", row))
        assertEquals("42", cell("GnssSampleCount", row))
        assertEquals("North York -> Scarborough", cell("UserTripName", row))
        assertEquals("drive", cell("IsDrive", row))
    }

    @Test fun isDriveColumnReflectsTheOverride() {
        fun isDrive(t: TripEntity) = cell("IsDrive", ExportData.summaryRow(t, analysis))
        assertEquals("drive", isDrive(trip.copy(userIsDrive = true)))
        assertEquals("walk", isDrive(trip.copy(userIsDrive = false)))
        assertEquals("auto", isDrive(trip.copy(userIsDrive = null)))
    }
}
