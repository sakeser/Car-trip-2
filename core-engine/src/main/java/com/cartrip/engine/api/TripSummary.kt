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
    /**
     * Drive Stress score, 0..100 (higher = more demanding), or `null` when the trip isn't scorable
     * (a non-drive, or too short). Derived from the trip's stored metrics by `analysis.StressScore`.
     * In the Driving Intelligence model this is the **Demand / Load** pillar.
     */
    val stressScore: Int? = null,
    /** Human band for [stressScore] ("Calm" / "Moderate" / "Busy" / "High stress"), or `null` when unscored. */
    val stressBand: String? = null,
    /**
     * Driving Intelligence **Smoothness** pillar (driver style), 0..100 (higher = smoother), or `null` when the
     * trip isn't scorable. Derived by `analysis.DrivingIntelligence` (a blend of Safety + Comfort).
     */
    val smoothnessScore: Int? = null,
    /** Human band for [smoothnessScore] ("Very smooth" / "Smooth" / "A bit rough" / "Rough"), or `null`. */
    val smoothnessBand: String? = null,
    /**
     * The conditional "Drive Quality" headline (Smoothness read against the demand band, e.g. "Smooth for a
     * demanding drive"), or `null` when the trip isn't scorable. The Efficiency pillar is intentionally omitted
     * here — it needs a vehicle profile the engine-api mapper doesn't have; add it via a vehicle gateway later.
     */
    val driveQuality: String? = null,
)
