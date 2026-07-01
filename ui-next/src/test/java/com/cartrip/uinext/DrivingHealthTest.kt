package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingHealthTest {

    @Test fun drivingHealth_aggregates_scorable_drives() {
        val summary = listOf(
            trip(
                id = 1L,
                startEpochMs = 3_000L,
                distanceMeters = 1_000.0,
                smoothnessScore = 80,
                stressScore = 31,
                driveQuality = "Calm",
            ),
            trip(
                id = 2L,
                startEpochMs = 1_000L,
                distanceMeters = 2_000.0,
                smoothnessScore = 81,
                stressScore = 32,
                driveQuality = "Balanced",
            ),
            trip(
                id = 3L,
                startEpochMs = 2_000L,
                distanceMeters = 3_000.0,
                smoothnessScore = 82,
                stressScore = 35,
                driveQuality = "Calm",
            ),
            trip(
                id = 4L,
                startEpochMs = 1_500L,
                distanceMeters = 999_000.0,
                smoothnessScore = null,
                stressScore = 99,
                driveQuality = "Ignored",
                isDrive = false,
            ),
            trip(
                id = 5L,
                startEpochMs = 4_000L,
                distanceMeters = 500.0,
                smoothnessScore = 84,
                stressScore = null,
                driveQuality = "Choppy",
            ),
        ).drivingHealth()

        assertEquals(4, summary.driveCount)
        assertEquals(6.5, summary.totalKm, 0.0)
        assertEquals(82, summary.avgSmoothness)
        assertEquals(33, summary.avgDemand)
        assertEquals(listOf("Calm" to 2, "Balanced" to 1, "Choppy" to 1), summary.mix)
        assertEquals(listOf(81, 82, 80, 84), summary.smoothnessTrend)
    }

    @Test fun drivingHealth_has_null_average_demand_when_no_scorable_drive_has_stress_score() {
        val summary = listOf(
            trip(id = 1L, smoothnessScore = 90, stressScore = null),
            trip(id = 2L, smoothnessScore = 80, stressScore = null),
            trip(id = 3L, smoothnessScore = null, stressScore = 10),
        ).drivingHealth()

        assertNull(summary.avgDemand)
    }

    @Test fun drivingHealth_empty_list_has_zero_totals_and_null_averages() {
        val summary = emptyList<TripSummary>().drivingHealth()

        assertEquals(
            DrivingHealthSummary(
                driveCount = 0,
                totalKm = 0.0,
                avgSmoothness = null,
                avgDemand = null,
                mix = emptyList(),
                smoothnessTrend = emptyList(),
            ),
            summary,
        )
    }

    @Test fun drivingHealth_orders_smoothness_trend_by_start_time() {
        val summary = listOf(
            trip(id = 4L, startEpochMs = 4_000L, smoothnessScore = 40),
            trip(id = 3L, startEpochMs = 3_000L, smoothnessScore = 30),
            trip(id = 2L, startEpochMs = 2_000L, smoothnessScore = 20),
            trip(id = 1L, startEpochMs = 1_000L, smoothnessScore = 10),
        ).drivingHealth()

        assertEquals(listOf(10, 20, 30, 40), summary.smoothnessTrend)
    }

    private fun trip(
        id: Long,
        startEpochMs: Long = 0L,
        distanceMeters: Double = 0.0,
        smoothnessScore: Int? = null,
        stressScore: Int? = null,
        driveQuality: String? = null,
        isDrive: Boolean = true,
    ): TripSummary = TripSummary(
        id = id,
        startEpochMs = startEpochMs,
        endEpochMs = startEpochMs + 1_000L,
        distanceMeters = distanceMeters,
        durationSeconds = 60.0,
        stressScore = stressScore,
        smoothnessScore = smoothnessScore,
        driveQuality = driveQuality,
        isDrive = isDrive,
    )
}
