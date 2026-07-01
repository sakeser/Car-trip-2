package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import kotlin.math.roundToInt

/** A recency window for filtering the Trips list. [millis] null = no time bound ("All"). */
enum class RecencyWindow(val label: String, val millis: Long?) {
    DAY("24h", 24L * 60 * 60 * 1000),
    THREE_DAY("3d", 3L * 24 * 60 * 60 * 1000),
    WEEK("7d", 7L * 24 * 60 * 60 * 1000),
    MONTH("30d", 30L * 24 * 60 * 60 * 1000),
    ALL("All", null),
}

/** Aggregate stats for a filtered window. [avgSmoothness] null when no scored drive is present. */
data class TripWindowSummary(val driveCount: Int, val totalKm: Double, val avgSmoothness: Int?)

/** Trips whose startEpochMs is within [window] of [nowMs] (ALL = unfiltered). Keeps input order. */
fun List<TripSummary>.inWindow(window: RecencyWindow, nowMs: Long): List<TripSummary> {
    val millis = window.millis ?: return this
    val cutoffMs = nowMs - millis
    return filter { it.startEpochMs >= cutoffMs }
}

/**
 * Summary over the DRIVES in this list: count, total km (sum distanceMeters/1000 over drives), and the
 * mean smoothnessScore over drives that have one (rounded to Int; null if none). Non-drives are excluded.
 */
fun List<TripSummary>.windowSummary(): TripWindowSummary {
    val drives = filter { it.isDrive }
    val scoredSmoothness = drives.mapNotNull { it.smoothnessScore }
    val avgSmoothness = if (scoredSmoothness.isEmpty()) null else scoredSmoothness.average().roundToInt()
    return TripWindowSummary(
        driveCount = drives.size,
        totalKm = drives.fold(0.0) { total, trip -> total + trip.distanceMeters / 1000.0 },
        avgSmoothness = avgSmoothness,
    )
}
