package com.cartrip.engine.api

/**
 * A small, UI-facing read model of a trip for list / summary screens — part of the engine's **public API**
 * (`com.cartrip.engine.api`), deliberately decoupled from the Room `TripEntity` so consumers (e.g. the future
 * `:ui-next` module) never import persistence internals.
 *
 * Intentionally minimal: only the fields a trip-list row needs today. Grow it field-by-field as a real screen
 * requires — not speculatively. Mapped from `TripEntity` inside [TripRepository].
 */
data class TripSummary(
    val id: Long,
    /** Trip start, epoch milliseconds. */
    val startEpochMs: Long,
    /** Trip end, epoch milliseconds; `0` while a trip is still ongoing / not finalized. */
    val endEpochMs: Long,
    val distanceMeters: Double,
    val durationSeconds: Double,
)
