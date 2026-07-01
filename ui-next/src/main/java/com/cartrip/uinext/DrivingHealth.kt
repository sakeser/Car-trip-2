package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import kotlin.math.roundToInt

/** Aggregated Driving-Intelligence stats for the Health tab over a set of trips. All fields are derived purely
 *  from existing TripSummary scores (no scoring logic here). [avgSmoothness]/[avgDemand] null when unavailable. */
data class DrivingHealthSummary(
    val driveCount: Int,
    val totalKm: Double,
    val avgSmoothness: Int?,
    val avgDemand: Int?,
    val mix: List<Pair<String, Int>>,
    val smoothnessTrend: List<Int>,
)

/** Aggregate this list into a [DrivingHealthSummary]. "Scorable drives" (smoothnessScore != null) are the
 *  population for every stat. */
fun List<TripSummary>.drivingHealth(): DrivingHealthSummary {
    val scorable = filter { it.smoothnessScore != null }
    val smoothnessScores = scorable.map { it.smoothnessScore!! }
    val stressScores = scorable.mapNotNull { it.stressScore }

    return DrivingHealthSummary(
        driveCount = scorable.size,
        totalKm = scorable.fold(0.0) { total, trip -> total + trip.distanceMeters / 1000.0 },
        avgSmoothness = if (smoothnessScores.isEmpty()) null else smoothnessScores.average().roundToInt(),
        avgDemand = if (stressScores.isEmpty()) null else stressScores.average().roundToInt(),
        mix = scorable
            .mapNotNull { it.driveQuality }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value },
        smoothnessTrend = scorable
            .sortedBy { it.startEpochMs }
            .map { it.smoothnessScore!! },
    )
}
