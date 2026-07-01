package com.cartrip.uinext

import com.cartrip.engine.api.TripTrackPoint

/**
 * Pure logic for the Map's Speeding layer: split a trip's positioned track into runs of over-the-limit points,
 * tiered by how far over (minor = 0..[MINOR_MAX_OVER_KMH] km/h over, major = beyond that). Read-only, derived
 * from the engine-api `TripTrackPoint` speed/limit/position already exposed — no scoring, no new gateway.
 * Mirrors the legacy TripMap SpeedTier overlay. ASCII source (Cp1252 trap).
 */

internal enum class SpeedTier { MINOR, MAJOR }

/** A contiguous over-limit run to draw as one coloured polyline. [points] all have a valid position. */
internal data class SpeedingSegment(val tier: SpeedTier, val points: List<TripTrackPoint>)

/** Boundary (km/h over the limit) between a minor and a major speeding tier. */
internal const val MINOR_MAX_OVER_KMH = 10.0

/** The over-limit tier of a single sample, or `null` when it isn't speeding (no limit / at-or-under / no fix). */
private fun TripTrackPoint.speedingTier(): SpeedTier? {
    val limit = speedLimitKmh ?: return null
    if (!hasPosition) return null
    val over = speedKmh - limit
    return when {
        over <= 0.0 -> null
        over <= MINOR_MAX_OVER_KMH -> SpeedTier.MINOR
        else -> SpeedTier.MAJOR
    }
}

/**
 * Consecutive over-limit runs of positioned points, each tagged with its [SpeedTier]. Only runs of >= 2 points
 * are kept (a single point can't draw a line). Points without a usable position are skipped (they'd break the
 * polyline); a tier change flushes the current run.
 */
internal fun List<TripTrackPoint>.speedingSegments(): List<SpeedingSegment> {
    val positioned = filter { it.hasPosition }
    val segments = ArrayList<SpeedingSegment>()
    var currentTier: SpeedTier? = null
    var run = ArrayList<TripTrackPoint>()
    fun flush() {
        if (run.size >= 2 && currentTier != null) segments.add(SpeedingSegment(currentTier!!, ArrayList(run)))
        run = ArrayList()
    }
    for (i in positioned.indices) {
        val p = positioned[i]
        val tier = p.speedingTier()
        if (tier != currentTier) {
            flush()
            currentTier = tier
            // Carry the previous point in so the coloured run connects to the rest of the route.
            if (tier != null && i > 0) run.add(positioned[i - 1])
        }
        if (tier != null) run.add(p)
    }
    flush()
    return segments
}
