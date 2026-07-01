package com.cartrip.engine.api

import com.cartrip.analyzer.analysis.DrivingIntelligence
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun toTrack_offsets_from_start_sorts_and_nulls_unknown_limit() {
        val start = 1_700_000_000_000L
        fun pt(tMs: Long, speed: Double, limit: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = 0.0, lon = 0.0, speedKmh = speed, longAccel = 0.0, latAccel = 0.0, speedLimitKmh = limit)
        val track = listOf(
            pt(start + 2_000L, 50.0, 60.0),   // 2 s in
            pt(start, 0.0, 0.0),              // t0, limit unknown -> null
            pt(start - 500L, 3.0, 40.0),      // just before start -> clamped to 0 s
        ).toTrack(start)

        // Sorted by time: the pre-start sample first (offset clamped to 0), then t0, then +2 s.
        assertEquals(listOf(0, 0, 2), track.map { it.offsetSeconds })
        assertEquals(3.0, track[0].speedKmh, 0.0)
        assertEquals(40.0, track[0].speedLimitKmh!!, 0.0)
        assertNull("a <=0 limit is unknown, not 0 km/h", track[1].speedLimitKmh)
        assertEquals(60.0, track[2].speedLimitKmh!!, 0.0)
    }

    @Test fun toTrack_drops_non_finite_speed_and_nulls_non_finite_limit() {
        val start = 1_700_000_000_000L
        fun pt(tMs: Long, speed: Double, limit: Double) =
            AnalysisPointEntity(tripId = 1L, t = tMs, lat = 0.0, lon = 0.0, speedKmh = speed, longAccel = 0.0, latAccel = 0.0, speedLimitKmh = limit)
        val track = listOf(
            pt(start, 40.0, Double.NaN),            // limit NaN -> null, point kept
            pt(start + 1_000L, Double.NaN, 50.0),   // speed NaN -> point dropped
            pt(start + 2_000L, 55.0, Double.POSITIVE_INFINITY), // limit inf -> null, point kept
        ).toTrack(start)

        assertEquals("the NaN-speed sample is dropped", 2, track.size)
        assertEquals(40.0, track[0].speedKmh, 0.0)
        assertNull(track[0].speedLimitKmh)
        assertEquals(55.0, track[1].speedKmh, 0.0)
        assertNull(track[1].speedLimitKmh)
    }

    @Test fun toEvents_folds_raw_types_and_offsets_from_start() {
        val start = 1_700_000_000_000L
        fun ev(tMs: Long, type: String, mag: Double) =
            DriveEventEntity(tripId = 1L, t = tMs, type = type, magnitude = mag)
        val events = listOf(
            ev(start + 10_000L, "CORNER", 0.4),
            ev(start + 1_000L, "BRAKE", 0.5),
            ev(start + 5_000L, "HARSH_STOP", 0.6),
            ev(start + 3_000L, "ACCEL", 0.3),
            ev(start + 7_000L, "POTHOLE", 1.2),
            ev(start + 9_000L, "MYSTERY", 0.1),
        ).toEvents(start)

        // Sorted by time.
        assertEquals(listOf(1, 3, 5, 7, 9, 10), events.map { it.offsetSeconds })
        assertEquals(TripEventKind.HARD_BRAKE, events[0].kind)       // BRAKE
        assertEquals(TripEventKind.HARD_ACCEL, events[1].kind)       // ACCEL
        assertEquals(TripEventKind.HARD_BRAKE, events[2].kind)       // HARSH_STOP -> brake
        assertEquals(TripEventKind.ROUGH_ROAD, events[3].kind)       // POTHOLE
        assertEquals(TripEventKind.OTHER, events[4].kind)            // unknown -> OTHER, not dropped
        assertEquals(TripEventKind.HARD_CORNER, events[5].kind)      // CORNER
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
