package com.cartrip.uinext

import com.cartrip.engine.api.TripTrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedingLayerTest {

    // A positioned point with a given speed + limit (valid coords so hasPosition is true).
    private fun p(speed: Double, limit: Double?, i: Int = 0) =
        TripTrackPoint(offsetSeconds = i, speedKmh = speed, speedLimitKmh = limit, lat = 43.7 + i * 1e-4, lon = -79.4)

    @Test fun splits_into_tiered_over_limit_runs() {
        val track = listOf(
            p(50.0, 60.0, 0),   // under -> none
            p(65.0, 60.0, 1),   // 5 over -> MINOR
            p(66.0, 60.0, 2),   // 6 over -> MINOR
            p(55.0, 60.0, 3),   // under -> none (flush minor)
            p(80.0, 60.0, 4),   // 20 over -> MAJOR
            p(81.0, 60.0, 5),   // 21 over -> MAJOR
        ).speedingSegments()

        assertEquals(2, track.size)
        assertEquals(SpeedTier.MINOR, track[0].tier)
        assertEquals(SpeedTier.MAJOR, track[1].tier)
        // Each run carries the preceding point for continuity, so counts are (boundary + overs).
        assertTrue(track[0].points.size >= 3)  // point 0 (carry) + points 1,2
        assertTrue(track[1].points.size >= 2)  // point 3 (carry) + points 4,5
    }

    @Test fun no_limit_or_at_limit_is_not_speeding() {
        val none = listOf(
            p(90.0, null, 0),   // unknown limit
            p(60.0, 60.0, 1),   // exactly at limit
            p(59.0, 60.0, 2),   // under
        ).speedingSegments()
        assertEquals(emptyList<SpeedingSegment>(), none)
    }

    @Test fun one_over_point_still_draws_a_short_run_via_the_carry_point() {
        val seg = listOf(
            p(50.0, 60.0, 0),   // under
            p(68.0, 60.0, 1),   // one MINOR point (8 over), then back under -> [carry, over] = 2 -> kept
            p(50.0, 60.0, 2),
        ).speedingSegments()
        // carry(point0) + the one over point = 2 points -> a drawable minor segment.
        assertEquals(1, seg.size)
        assertEquals(SpeedTier.MINOR, seg[0].tier)
        assertEquals(2, seg[0].points.size)
    }

    @Test fun unpositioned_points_are_skipped() {
        val track = listOf(
            TripTrackPoint(0, 80.0, 60.0, lat = 0.0, lon = 0.0),      // over but no position -> skipped
            TripTrackPoint(1, 80.0, 60.0, lat = 43.7, lon = -79.4),  // over, positioned
            TripTrackPoint(2, 80.0, 60.0, lat = 43.71, lon = -79.4), // over, positioned
        ).speedingSegments()
        assertEquals(1, track.size)
        assertEquals(2, track[0].points.size)  // only the two positioned overs
    }
}
