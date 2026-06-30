package com.cartrip.engine.api

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract test for the [TripEntity] -> [TripSummary] mapping (the only logic in the read-side seam; the
 * repository itself is a thin `.map` adapter, so the meaningful behavior to guard is here). Distinct field
 * values catch any field mis-wiring (e.g. start/end swapped).
 */
class TripSummaryMapperTest {

    @Test fun maps_every_field_with_distinct_values() {
        val s = TripEntity(
            id = 42L,
            startTime = 1_700_000_000_000L,
            endTime = 1_700_000_900_000L,
            distanceM = 12_345.6,
            durationS = 678.9,
        ).toSummary()

        assertEquals(42L, s.id)
        assertEquals(1_700_000_000_000L, s.startEpochMs)
        assertEquals(1_700_000_900_000L, s.endEpochMs)
        assertEquals(12_345.6, s.distanceMeters, 0.0)
        assertEquals(678.9, s.durationSeconds, 0.0)
    }

    @Test fun ongoing_trip_maps_zero_end_and_defaulted_metrics() {
        // Only the required fields set; endTime 0 = ongoing, distance/duration default to 0.0.
        val s = TripEntity(id = 7L, startTime = 1_000L, endTime = 0L).toSummary()

        assertEquals(7L, s.id)
        assertEquals(1_000L, s.startEpochMs)
        assertEquals(0L, s.endEpochMs)
        assertEquals(0.0, s.distanceMeters, 0.0)
        assertEquals(0.0, s.durationSeconds, 0.0)
    }
}
