package com.cartrip.engine.api

/**
 * One sample of a trip's speed timeline — an engine-API value type so UI consumers (e.g. `:ui-next`) can draw
 * a speed-vs-time "Trip Line" without importing persistence (`AnalysisPointEntity`) internals. Sourced from the
 * persisted 1 Hz analysis track via [TripRepository.getTrack].
 *
 * [offsetSeconds] is seconds since the trip's start (`TripEntity.startTime`), clamped at 0 — a stable, monotonic
 * x-axis independent of wall-clock/timezone. [speedLimitKmh] is the OSM posted limit matched to this point, or
 * `null` when unknown (not looked up / off-network) so the UI can distinguish "no limit data" from "0 km/h".
 */
data class TripTrackPoint(
    val offsetSeconds: Int,
    val speedKmh: Double,
    val speedLimitKmh: Double? = null,
)
