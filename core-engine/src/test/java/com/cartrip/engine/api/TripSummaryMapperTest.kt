package com.cartrip.engine.api

import com.cartrip.analyzer.analysis.DrivingIntelligence
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for the [TripEntity] -> [TripSummary] mapping (the only logic in the read-side seam; the
 * repository itself is a thin `.map` adapter, so the meaningful behavior to guard is here). Distinct field
 * values catch any field mis-wiring (e.g. start/end swapped).
 */
class TripSummaryMapperTest {

    @Test fun maps_every_field_with_distinct_values() {
        val trip = TripEntity(
            id = 42L,
            startTime = 1_700_000_000_000L,
            endTime = 1_700_000_900_000L,
            distanceM = 12_345.6,
            durationS = 678.9,
            maxSpeedMps = 20.0, // > 12 km/h -> clearly a drive, so StressScore scores it
        )
        val s = trip.toSummary()

        assertEquals(42L, s.id)
        assertEquals(1_700_000_000_000L, s.startEpochMs)
        assertEquals(1_700_000_900_000L, s.endEpochMs)
        assertEquals(12_345.6, s.distanceMeters, 0.0)
        assertEquals(678.9, s.durationSeconds, 0.0)

        // Stress is derived from the entity by the pure analysis.StressScore; the mapper must pass it through.
        val expected = StressScore.from(trip)
        assertNotNull("a 12 km / 11 min drive should be scorable", expected)
        assertEquals(expected?.score, s.stressScore)
        assertEquals(expected?.band, s.stressBand)

        // Driving Intelligence pillars (Smoothness + the conditional headline) are derived vehicle-free.
        val di = DrivingIntelligence.from(trip)
        assertNotNull(di)
        assertEquals(di?.smoothness?.score, s.smoothnessScore)
        assertEquals(di?.smoothness?.label, s.smoothnessBand)
        assertEquals(di?.headline, s.driveQuality)
    }

    @Test fun eta_exposed_only_for_a_drive_with_a_fetched_traffic_estimate() {
        // A real drive (fast enough) with a fetched ETA -> traffic + free-flow surface.
        val drive = TripEntity(
            id = 1L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0, maxSpeedMps = 25.0,
            googleEtaTrafficS = 600.0, googleEtaFreeFlowS = 480.0, etaSource = "typical",
        ).toSummary()
        assertEquals(600.0, drive.etaTrafficSeconds!!, 0.0)
        assertEquals(480.0, drive.etaFreeFlowSeconds!!, 0.0)

        // No fetched ETA (etaSource empty) -> null even though a traffic seconds value is present.
        val noFetch = TripEntity(
            id = 2L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0, maxSpeedMps = 25.0,
            googleEtaTrafficS = 600.0, etaSource = "",
        ).toSummary()
        assertNull(noFetch.etaTrafficSeconds)

        // A non-drive (walk speed) with an ETA -> suppressed (a driving ETA is meaningless).
        val walk = TripEntity(
            id = 3L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0, maxSpeedMps = 1.5,
            googleEtaTrafficS = 600.0, etaSource = "typical",
        ).toSummary()
        assertNull(walk.etaTrafficSeconds)
    }

    @Test fun isDrive_reflects_trip_kind() {
        val drive = TripEntity(
            id = 1L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0, maxSpeedMps = 25.0,
        ).toSummary()
        assertTrue(drive.isDrive)

        val walk = TripEntity(
            id = 2L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0, maxSpeedMps = 1.5,
        ).toSummary()
        assertFalse(walk.isDrive)
    }

    @Test fun stats_map_raw_measured_quantities_with_speed_in_kmh() {
        val s = TripEntity(
            id = 1L, startTime = 1_000L, endTime = 700_000L, durationS = 700.0,
            maxSpeedMps = 30.0, avgMovingSpeedMps = 15.0, movingS = 600.0, idleS = 100.0,
            hardBrakeCount = 2, hardAccelCount = 1, hardCornerCount = 3,
        ).toSummary()
        val stats = s.stats!!
        assertEquals(108.0, stats.maxSpeedKmh, 1e-9)     // 30 m/s * 3.6
        assertEquals(54.0, stats.avgMovingSpeedKmh, 1e-9) // 15 m/s * 3.6
        assertEquals(600.0, stats.movingSeconds, 0.0)
        assertEquals(100.0, stats.idleSeconds, 0.0)
        assertEquals(2, stats.hardBrakeCount)
        assertEquals(1, stats.hardAccelCount)
        assertEquals(3, stats.hardCornerCount)
    }

    @Test fun ongoing_trip_maps_zero_end_and_defaulted_metrics() {
        // Only the required fields set; endTime 0 = ongoing, distance/duration default to 0.0.
        val s = TripEntity(id = 7L, startTime = 1_000L, endTime = 0L).toSummary()

        assertEquals(7L, s.id)
        assertEquals(1_000L, s.startEpochMs)
        assertEquals(0L, s.endEpochMs)
        assertEquals(0.0, s.distanceMeters, 0.0)
        assertEquals(0.0, s.durationSeconds, 0.0)
        // 0 m / 0 s is below the StressScore scorability floor -> no score, and no Driving Intelligence.
        assertNull(s.stressScore)
        assertNull(s.stressBand)
        assertNull(s.smoothnessScore)
        assertNull(s.driveQuality)
    }

    @Test fun toRoute_keeps_valid_points_and_drops_zero_or_out_of_range() {
        fun pt(lat: Double, lon: Double) =
            AnalysisPointEntity(tripId = 1L, t = 0L, lat = lat, lon = lon, speedKmh = 0.0, longAccel = 0.0, latAccel = 0.0)
        val route = listOf(
            pt(43.76, -79.41),   // valid
            pt(0.0, 0.0),        // gap-fill / cold fix -> dropped
            pt(43.77, -79.40),   // valid
            pt(200.0, 10.0),     // out-of-range lat -> dropped
        ).toRoute()

        assertEquals(2, route.size)
        assertEquals(43.76, route[0].lat, 0.0)
        assertEquals(-79.41, route[0].lon, 0.0)
        assertEquals(43.77, route[1].lat, 0.0)
    }

    @Test fun toTrack_offsets_from_first_sample_sorts_and_nulls_unknown_limit() {
        // Sample t is a MONOTONIC RECORDING CLOCK (e.g. elapsedRealtime), NOT epoch ms. Using a big non-epoch
        // base guards the real bug we hit on-device: offsetting from the trip's epoch startTime crushed every
        // point to x=0. Origin must come from the samples themselves (the earliest t).
        val base = 262_520_731L
        fun pt(tMs: Long, speed: Double, limit: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = 0.0, lon = 0.0, speedKmh = speed, longAccel = 0.0, latAccel = 0.0, speedLimitKmh = limit)
        val track = listOf(
            pt(base + 2_000L, 50.0, 60.0),   // 2 s in
            pt(base, 0.0, 0.0),              // origin, limit unknown -> null
            pt(base + 500L, 3.0, 40.0),      // 0.5 s -> truncates to 0 s
        ).toTrack()

        // Sorted by time: origin (0 s), +0.5 s (->0), +2 s. Offsets are relative to the earliest sample.
        assertEquals(listOf(0, 0, 2), track.map { it.offsetSeconds })
        assertNull("a <=0 limit is unknown, not 0 km/h", track[0].speedLimitKmh)
        assertEquals(3.0, track[1].speedKmh, 0.0)
        assertEquals(40.0, track[1].speedLimitKmh!!, 0.0)
        assertEquals(60.0, track[2].speedLimitKmh!!, 0.0)
    }

    @Test fun toTrack_drops_non_finite_speed_and_nulls_non_finite_limit() {
        val base = 262_520_731L
        fun pt(tMs: Long, speed: Double, limit: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = 0.0, lon = 0.0, speedKmh = speed, longAccel = 0.0, latAccel = 0.0, speedLimitKmh = limit)
        val track = listOf(
            pt(base, 40.0, Double.NaN),            // limit NaN -> null, point kept
            pt(base + 1_000L, Double.NaN, 50.0),   // speed NaN -> point dropped
            pt(base + 2_000L, 55.0, Double.POSITIVE_INFINITY), // limit inf -> null, point kept
        ).toTrack()

        assertEquals("the NaN-speed sample is dropped", 2, track.size)
        assertEquals(40.0, track[0].speedKmh, 0.0)
        assertNull(track[0].speedLimitKmh)
        assertEquals(55.0, track[1].speedKmh, 0.0)
        assertNull(track[1].speedLimitKmh)
    }

    @Test fun toTrack_carries_position_and_flags_invalid_fixes() {
        val base = 262_520_731L
        fun pt(tMs: Long, lat: Double, lon: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = lat, lon = lon, speedKmh = 10.0, longAccel = 0.0, latAccel = 0.0)
        val track = listOf(
            pt(base, 43.76, -79.41),      // valid fix
            pt(base + 1_000L, 0.0, 0.0),  // gap-fill / cold fix -> hasPosition false, but KEPT for the timeline
        ).toTrack()

        assertEquals("invalid-coord samples are kept on the speed timeline", 2, track.size)
        assertEquals(43.76, track[0].lat, 0.0)
        assertEquals(-79.41, track[0].lon, 0.0)
        assertTrue(track[0].hasPosition)
        assertFalse("a (0,0) fix has no usable position for a map marker", track[1].hasPosition)
    }

    @Test fun toEvents_folds_raw_types_and_offsets_from_start() {
        val start = 1_700_000_000_000L
        fun ev(tMs: Long, type: String, mag: Double) =
            DriveEventEntity(tripId = 1L, t = tMs, type = type, magnitude = mag)
        // A single origin sample at `start` with no valid coord: anchors offsets, leaves events unpositioned.
        val points = listOf(AnalysisPointEntity(tripId = 1L, t = start, lat = 0.0, lon = 0.0, speedKmh = 0.0, longAccel = 0.0, latAccel = 0.0))
        val events = listOf(
            ev(start + 10_000L, "CORNER", 0.4),
            ev(start + 1_000L, "BRAKE", 0.5),
            ev(start + 5_000L, "HARSH_STOP", 0.6),
            ev(start + 3_000L, "ACCEL", 0.3),
            ev(start + 7_000L, "POTHOLE", 1.2),
            ev(start + 9_000L, "MYSTERY", 0.1),
        ).toEvents(points)

        // Sorted by time.
        assertEquals(listOf(1, 3, 5, 7, 9, 10), events.map { it.offsetSeconds })
        assertEquals(TripEventKind.HARD_BRAKE, events[0].kind)       // BRAKE
        assertEquals(TripEventKind.HARD_ACCEL, events[1].kind)       // ACCEL
        assertEquals(TripEventKind.HARD_BRAKE, events[2].kind)       // HARSH_STOP -> brake
        assertEquals(TripEventKind.ROUGH_ROAD, events[3].kind)       // POTHOLE
        assertEquals(TripEventKind.OTHER, events[4].kind)            // unknown -> OTHER, not dropped
        assertEquals(TripEventKind.HARD_CORNER, events[5].kind)      // CORNER
    }

    @Test fun toEvents_positions_from_nearest_sample_within_tolerance() {
        val start = 1_700_000_000_000L
        fun pt(tMs: Long, lat: Double, lon: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = lat, lon = lon, speedKmh = 0.0, longAccel = 0.0, latAccel = 0.0)
        val points = listOf(
            pt(start, 43.70, -79.40),
            pt(start + 10_000L, 43.75, -79.45),   // valid, near the first event
            pt(start + 60_000L, 0.0, 0.0),         // gap-fill: no usable coord
        )
        val events = listOf(
            DriveEventEntity(tripId = 1L, t = start + 11_000L, type = "BRAKE", magnitude = 0.5),  // 1 s from the valid +10s sample -> positioned
            DriveEventEntity(tripId = 1L, t = start + 60_000L, type = "CORNER", magnitude = 0.4), // nearest sample is (0,0) -> unpositioned
            DriveEventEntity(tripId = 1L, t = start + 200_000L, type = "ACCEL", magnitude = 0.3), // 140 s from any sample (> tolerance) -> unpositioned
        ).toEvents(points)

        assertTrue("event near a valid sample is positioned", events[0].hasPosition)
        assertEquals(43.75, events[0].lat, 0.0)
        assertEquals(-79.45, events[0].lon, 0.0)
        assertFalse("nearest sample is (0,0)", events[1].hasPosition)
        assertFalse("nearest sample is beyond the 15 s tolerance", events[2].hasPosition)
    }

    @Test fun eventKind_maps_every_known_type() {
        assertEquals(TripEventKind.HARD_BRAKE, eventKindOf("BRAKE"))
        assertEquals(TripEventKind.HARD_BRAKE, eventKindOf("HARSH_STOP"))
        assertEquals(TripEventKind.HARD_ACCEL, eventKindOf("ACCEL"))
        assertEquals(TripEventKind.HARD_CORNER, eventKindOf("CORNER"))
        assertEquals(TripEventKind.HARD_CORNER, eventKindOf("SWERVE"))
        assertEquals(TripEventKind.ROUGH_ROAD, eventKindOf("POTHOLE"))
        assertEquals(TripEventKind.OTHER, eventKindOf(""))
    }
}
