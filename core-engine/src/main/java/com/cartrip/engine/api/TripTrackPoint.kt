package com.cartrip.engine.api

/**
 * One sample of a trip's speed timeline — an engine-API value type so UI consumers (e.g. `:ui-next`) can draw
 * a speed-vs-time "Trip Line" without importing persistence (`AnalysisPointEntity`) internals. Sourced from the
 * persisted 1 Hz analysis track via [TripRepository.getTrack].
 *
 * [offsetSeconds] is seconds since the trip's first analysis sample (the track's own recording-clock origin, NOT
 * epoch wall-clock), clamped at 0 — a stable, monotonic x-axis. [speedLimitKmh] is the OSM posted limit matched
 * to this point, or `null` when unknown (not looked up / off-network) so the UI distinguishes "no limit data"
 * from "0 km/h". [lat]/[lon] are this sample's position so a UI can sync a timeline scrubber to a marker on the
 * map; they may be an invalid `(0, 0)` / out-of-range fix (a gap-fill or cold fix — [hasPosition] is false then),
 * which the timeline keeps but a map marker should skip.
 */
data class TripTrackPoint(
    val offsetSeconds: Int,
    val speedKmh: Double,
    val speedLimitKmh: Double? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
) {
    /** True when [lat]/[lon] is a usable fix (not `(0, 0)` and in range) — safe to place a map marker at. */
    val hasPosition: Boolean
        get() = !(lat == 0.0 && lon == 0.0) && kotlin.math.abs(lat) <= 90.0 && kotlin.math.abs(lon) <= 180.0
}
