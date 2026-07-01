package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripWindowTest {

    @Test fun inWindow_all_returns_everything_in_same_order() {
        val trips = listOf(
            trip(id = 1L, startEpochMs = 1_000L),
            trip(id = 2L, startEpochMs = 2_000L),
            trip(id = 3L, startEpochMs = 3_000L),
        )

        val result = trips.inWindow(RecencyWindow.ALL, nowMs = 10_000L)

        assertEquals(trips, result)
    }

    @Test fun inWindow_includes_exact_cutoff_and_excludes_one_ms_older() {
        val nowMs = 1_000_000_000L
        val cutoffMs = nowMs - RecencyWindow.DAY.millis!!
        val trips = listOf(
            trip(id = 1L, startEpochMs = cutoffMs - 1L),
            trip(id = 2L, startEpochMs = cutoffMs),
            trip(id = 3L, startEpochMs = cutoffMs + 1L),
        )

        val result = trips.inWindow(RecencyWindow.DAY, nowMs)

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test fun inWindow_preserves_input_order() {
        val nowMs = 1_000_000_000L
        val cutoffMs = nowMs - RecencyWindow.WEEK.millis!!
        val trips = listOf(
            trip(id = 3L, startEpochMs = nowMs),
            trip(id = 1L, startEpochMs = cutoffMs + 1_000L),
            trip(id = 2L, startEpochMs = cutoffMs + 500L),
        )

        val result = trips.inWindow(RecencyWindow.WEEK, nowMs)

        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
    }

    @Test fun windowSummary_excludes_non_drives_and_rounds_scored_drive_average() {
        val summary = listOf(
            trip(id = 1L, distanceMeters = 2_000.0, smoothnessScore = 80, isDrive = true),
            trip(id = 2L, distanceMeters = 100_000.0, smoothnessScore = 0, isDrive = false),
            trip(id = 3L, distanceMeters = 1_000.0, smoothnessScore = null, isDrive = true),
            trip(id = 4L, distanceMeters = 500.0, smoothnessScore = 81, isDrive = true),
        ).windowSummary()

        assertEquals(3, summary.driveCount)
        assertEquals(3.5, summary.totalKm, 0.0)
        assertEquals(81, summary.avgSmoothness)
    }

    @Test fun windowSummary_has_null_average_when_no_scored_drive_is_present() {
        val summary = listOf(
            trip(id = 1L, distanceMeters = 9_000.0, smoothnessScore = 20, isDrive = false),
            trip(id = 2L, distanceMeters = 2_000.0, smoothnessScore = null, isDrive = true),
        ).windowSummary()

        assertEquals(1, summary.driveCount)
        assertEquals(2.0, summary.totalKm, 0.0)
        assertNull(summary.avgSmoothness)
    }

    @Test fun windowSummary_empty_list_has_zero_totals_and_null_average() {
        val summary = emptyList<TripSummary>().windowSummary()

        assertEquals(0, summary.driveCount)
        assertEquals(0.0, summary.totalKm, 0.0)
        assertNull(summary.avgSmoothness)
    }

    private fun trip(
        id: Long,
        startEpochMs: Long = 0L,
        distanceMeters: Double = 0.0,
        smoothnessScore: Int? = null,
        isDrive: Boolean = true,
    ): TripSummary = TripSummary(
        id = id,
        startEpochMs = startEpochMs,
        endEpochMs = startEpochMs + 1_000L,
        distanceMeters = distanceMeters,
        durationSeconds = 60.0,
        smoothnessScore = smoothnessScore,
        isDrive = isDrive,
    )
}
