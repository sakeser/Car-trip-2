package com.cartrip.engine.api

/**
 * A notable driving event on a trip (a hard brake / accel / corner, or a road event), positioned on the trip's
 * timeline — an engine-API value type so UI consumers (e.g. the `:ui-next` Trip Line) can mark events without
 * importing persistence (`DriveEventEntity`) internals. Sourced via [TripRepository.getEvents].
 *
 * [offsetSeconds] is seconds since the trip's first analysis sample (same recording-clock origin/x-axis as [TripTrackPoint]). [kind] is
 * a UI-stable classification mapped from the raw detector type; unrecognized raw types map to [TripEventKind.OTHER]
 * rather than being dropped. [magnitude] is the detector's severity (g for motion events), for optional weighting.
 * [lat]/[lon] are the event's map position, correlated to the nearest analysis sample by time; they may be an
 * invalid `(0, 0)` fix (no nearby sample) — [hasPosition] is false then and a map layer should skip it.
 */
data class TripEvent(
    val offsetSeconds: Int,
    val kind: TripEventKind,
    val magnitude: Double,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
) {
    /** True when [lat]/[lon] is a usable fix (not `(0, 0)` and in range) — safe to place a map marker at. */
    val hasPosition: Boolean
        get() = !(lat == 0.0 && lon == 0.0) && kotlin.math.abs(lat) <= 90.0 && kotlin.math.abs(lon) <= 180.0
}

/** A UI-stable classification of a [TripEvent], decoupled from the engine's raw detector `type` strings. */
enum class TripEventKind {
    HARD_BRAKE,
    HARD_ACCEL,
    HARD_CORNER,
    ROUGH_ROAD,
    OTHER,
}
