package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class InsightsMetricsTest {

    // Fixed UTC "now" = 2024-01-10T12:00:00Z so day bucketing is deterministic.
    private val nowMs = 1_704_888_000_000L
    private val utc = ZoneOffset.UTC

    /** epoch ms for a UTC date/time. */
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        java.time.LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun trip(id: Long, startMs: Long, km: Double = 0.0, isDrive: Boolean = true): TripSummary =
        TripSummary(
            id = id,
            startEpochMs = startMs,
            endEpochMs = startMs + 1_000L,
            distanceMeters = km * 1000.0,
            durationSeconds = 60.0,
            isDrive = isDrive,
        )

    @Test fun dailyDistance_buckets_by_day_fills_gaps_and_excludes_non_drives() {
        val trips = listOf(
            trip(1L, at(2024, 1, 10, 8), km = 5.0),       // today
            trip(2L, at(2024, 1, 10, 20), km = 3.0),      // today, same day -> sums to 8
            trip(3L, at(2024, 1, 8, 9), km = 10.0),       // 2 days ago
            trip(4L, at(2024, 1, 8, 9), km = 99.0, isDrive = false), // non-drive -> excluded
            trip(5L, at(2024, 1, 1, 9), km = 42.0),       // outside 7-day window -> excluded
        )
        val daily = trips.dailyDistanceKm(days = 7, nowMs = nowMs, zone = utc)

        assertEquals(7, daily.size)
        // Oldest -> newest: last entry is today (Jan 10) = 8.0; Jan 8 = 10.0; Jan 9 = 0.0 (gap filled).
        assertEquals(8.0, daily.last().second, 0.0)
        val jan8 = daily[daily.size - 3]  // today, yesterday(0), 2-days-ago
        assertEquals(10.0, jan8.second, 0.0)
        assertEquals(0.0, daily[daily.size - 2].second, 0.0) // Jan 9 gap
        // Every day present, total over the window excludes the out-of-window + non-drive trips.
        assertEquals(18.0, daily.sumOf { it.second }, 0.0)
    }

    @Test fun daypartCounts_fixed_order_all_parts_present_excludes_non_drives() {
        val trips = listOf(
            trip(1L, at(2024, 1, 10, 2), km = 1.0),   // Night
            trip(2L, at(2024, 1, 10, 7), km = 1.0),   // Morning
            trip(3L, at(2024, 1, 10, 8), km = 1.0),   // Morning
            trip(4L, at(2024, 1, 10, 12), km = 1.0),  // Midday
            trip(5L, at(2024, 1, 10, 17), km = 1.0),  // Afternoon
            trip(6L, at(2024, 1, 10, 23), km = 1.0),  // Evening
            trip(7L, at(2024, 1, 10, 9), km = 9.0, isDrive = false), // non-drive -> excluded
        )
        val parts = trips.daypartCounts(utc)

        assertEquals(
            listOf("Night" to 1, "Morning" to 2, "Midday" to 1, "Afternoon" to 1, "Evening" to 1),
            parts,
        )
    }

    @Test fun empty_inputs_are_safe() {
        assertEquals(emptyList<Pair<String, Double>>(), emptyList<TripSummary>().dailyDistanceKm(0, nowMs, utc))
        val daily = emptyList<TripSummary>().dailyDistanceKm(3, nowMs, utc)
        assertEquals(3, daily.size)
        assertEquals(0.0, daily.sumOf { it.second }, 0.0)
        assertEquals(
            listOf("Night" to 0, "Morning" to 0, "Midday" to 0, "Afternoon" to 0, "Evening" to 0),
            emptyList<TripSummary>().daypartCounts(utc),
        )
    }
}
