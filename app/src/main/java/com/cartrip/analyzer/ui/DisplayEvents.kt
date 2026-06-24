package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.analysis.TrackPoint
import kotlin.math.abs

/**
 * Main-screen event cleanup. Raw detector events are intentionally kept in storage/export/advanced
 * views; this pass turns overlapping detector signals into a smaller set of human-facing moments.
 */
object DisplayEvents {
    private const val G = 9.80665
    private const val CLUSTER_TIME_MS = 6_000L
    private const val CLUSTER_DISTANCE_M = 70.0
    private const val LOW_CONFIDENCE = 0.6

    fun clean(rawEvents: List<DriveEvent>, points: List<TrackPoint>): List<DriveEvent> {
        if (rawEvents.isEmpty()) return emptyList()
        if (points.isEmpty()) return rawEvents.sortedBy { it.tMs }.filter(::passesDisplayThreshold)

        val filtered = rawEvents
            .sortedBy { it.tMs }
            .filter(::passesDisplayThreshold)
            .filterNot { isBumpEcho(it, rawEvents) }
        if (filtered.isEmpty()) return emptyList()

        val clusters = ArrayList<MutableList<DriveEvent>>()
        filtered.forEach { event ->
            val lastCluster = clusters.lastOrNull()
            val lastEvent = lastCluster?.lastOrNull()
            if (lastEvent != null && sameMoment(lastEvent, event, points)) {
                lastCluster.add(event)
            } else {
                clusters.add(arrayListOf(event))
            }
        }

        return clusters.map { cluster ->
            val representative = chooseRepresentative(cluster)
            if (cluster.size == 1) representative
            else representative.copy(
                source = "summary",
                confidence = cluster.maxOf { it.confidence }
            )
        }.sortedBy { it.tMs }
    }

    private fun chooseRepresentative(cluster: List<DriveEvent>): DriveEvent {
        if (cluster.size == 1) return cluster.first()
        val potholes = cluster.filter { it.type == EventType.POTHOLE }
        val driving = cluster.filter { it.type != EventType.POTHOLE }
        val strongDriving = driving.any {
            it.source == "gps" || it.magnitude / G >= when (it.type) {
                EventType.BRAKE, EventType.ACCEL -> 0.35
                EventType.CORNER -> 0.35
                EventType.SWERVE -> 0.30
                EventType.POTHOLE -> 0.40
            }
        }
        if (potholes.isNotEmpty() && !strongDriving) {
            return potholes.maxByOrNull { it.magnitude } ?: potholes.first()
        }

        val bestStrength = cluster.maxOf { displayStrength(it) }
        return cluster
            .filter { displayStrength(it) >= bestStrength - 0.25 }
            .maxWithOrNull(compareBy<DriveEvent> { typePriority(it.type) }
                .thenBy { it.confidence }
                .thenBy { it.magnitude }) ?: cluster.first()
    }

    private fun passesDisplayThreshold(event: DriveEvent): Boolean {
        val g = event.magnitude / G
        return when (event.type) {
            EventType.BRAKE, EventType.ACCEL -> {
                g >= 0.28 && !(event.source == "fused" && event.confidence < LOW_CONFIDENCE && g < 0.35)
            }
            EventType.CORNER -> g >= 0.28
            EventType.SWERVE -> g >= 0.25 || (event.source == "fused" && event.confidence >= 0.8 && g >= 0.10)
            EventType.POTHOLE -> g >= 0.33
        }
    }

    private fun isBumpEcho(event: DriveEvent, rawEvents: List<DriveEvent>): Boolean {
        if (event.source != "fused") return false
        if (event.type != EventType.ACCEL && event.type != EventType.BRAKE) return false
        if (event.confidence >= LOW_CONFIDENCE) return false
        val nearPothole = rawEvents.any {
            it.type == EventType.POTHOLE && abs(it.tMs - event.tMs) <= 1_000L
        }
        return nearPothole
    }

    private fun sameMoment(a: DriveEvent, b: DriveEvent, points: List<TrackPoint>): Boolean {
        if (abs(b.tMs - a.tMs) <= CLUSTER_TIME_MS) return true
        val pa = nearestPoint(points, a.tMs) ?: return false
        val pb = nearestPoint(points, b.tMs) ?: return false
        return GeoUtils.haversine(pa.lat, pa.lon, pb.lat, pb.lon) <= CLUSTER_DISTANCE_M
    }

    private fun nearestPoint(points: List<TrackPoint>, tMs: Long): TrackPoint? {
        if (points.isEmpty()) return null
        val next = points.indexOfFirst { it.tMs >= tMs }
        if (next == -1) return points.last()  // target is after the last point
        if (next == 0) return points.first()  // target is at/before the first point
        val prev = next - 1
        return if (tMs - points[prev].tMs <= points[next].tMs - tMs) points[prev] else points[next]
    }

    private fun displayStrength(event: DriveEvent): Double {
        val g = event.magnitude / G
        val threshold = when (event.type) {
            EventType.BRAKE, EventType.ACCEL -> 0.30
            EventType.CORNER -> 0.35
            EventType.SWERVE -> 0.30
            EventType.POTHOLE -> 0.40
        }
        val sourceWeight = when (event.source) {
            "gps" -> 0.08
            "fused" -> 0.04 * event.confidence
            else -> 0.0
        }
        return g / threshold + sourceWeight
    }

    private fun typePriority(type: EventType): Int = when (type) {
        EventType.BRAKE -> 5
        EventType.CORNER -> 4
        EventType.POTHOLE -> 3
        EventType.ACCEL -> 2
        EventType.SWERVE -> 1
    }
}
