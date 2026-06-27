package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint

/**
 * Main-screen event cleanup. Raw detector events are intentionally kept in storage/export/advanced
 * views; this pass turns overlapping detector signals into a smaller set of human-facing moments.
 */
object DisplayEvents {
    private const val G = 9.80665
    // One real-world "moment": signals within CLUSTER_TIME_MS of each other, but a single cluster can
    // never span more than MAX_CLUSTER_SPAN_MS — otherwise slow-drive events 10-30s apart chain into
    // one bogus blob (e.g. three swerves + a brake merged across 31s).
    private const val CLUSTER_TIME_MS = 4_000L
    private const val MAX_CLUSTER_SPAN_MS = 8_000L
    private const val LOW_CONFIDENCE = 0.6
    // A real brake/accel of display strength shifts the 1 Hz GPS speed by more than this over a few
    // seconds; a pothole's horizontal shake leaves it flat. Used to unmask bump-contaminated
    // longitudinals that coincide with a pothole even at high detector confidence.
    private const val BUMP_ECHO_MIN_SLOPE_KMH = 3.0

    // Speed/g gating for turn-like events (corners & swerves). A sharp-feeling number at a crawl, or a
    // gentle one at speed, usually isn't a notable maneuver — so require more g the slower you're going.
    private const val TURN_MIN_SPEED_KMH = 10.0
    private const val TURN_MID_SPEED_KMH = 30.0
    private const val TURN_MIN_G_LOW = 0.40   // 10-30 km/h
    private const val TURN_MIN_G_HIGH = 0.30  // >=30 km/h

    fun clean(rawEvents: List<DriveEvent>, points: List<TrackPoint>): List<DriveEvent> {
        if (rawEvents.isEmpty()) return emptyList()
        val sorted = rawEvents.sortedBy { it.tMs }
        val filtered = sorted
            .filter { passesDisplayThreshold(it, speedAt(points, it.tMs)) }
            .filterNot { isBumpEcho(it, rawEvents, points) }
        if (filtered.isEmpty()) return emptyList()

        // Cluster strictly by time, bounded so a cluster can't stretch across distinct moments.
        val clusters = ArrayList<MutableList<DriveEvent>>()
        filtered.forEach { event ->
            val cluster = clusters.lastOrNull()
            val last = cluster?.lastOrNull()
            val start = cluster?.firstOrNull()
            if (last != null && start != null &&
                event.tMs - last.tMs <= CLUSTER_TIME_MS &&
                event.tMs - start.tMs <= MAX_CLUSTER_SPAN_MS
            ) {
                cluster.add(event)
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
        // A harsh stop subsumes the hard brake that causes it (they fall in the same moment), so it
        // always represents its cluster — the more specific, more useful "you braked hard to a stop".
        cluster.firstOrNull { it.type == EventType.HARSH_STOP }?.let { return it }
        val potholes = cluster.filter { it.type == EventType.POTHOLE }
        val driving = cluster.filter { it.type != EventType.POTHOLE }
        val strongDriving = driving.any {
            it.source == "gps" || it.magnitude / G >= when (it.type) {
                EventType.BRAKE, EventType.ACCEL -> 0.35
                EventType.CORNER -> 0.35
                EventType.SWERVE -> 0.30
                EventType.POTHOLE -> 0.40
                EventType.HARSH_STOP -> 0.30
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

    private fun passesDisplayThreshold(event: DriveEvent, speedKmh: Double): Boolean {
        val g = event.magnitude / G
        return when (event.type) {
            EventType.BRAKE, EventType.ACCEL -> {
                g >= 0.28 && !(event.source == "fused" && event.confidence < LOW_CONFIDENCE && g < 0.35)
            }
            EventType.CORNER, EventType.SWERVE -> turnNotable(g, speedKmh)
            EventType.POTHOLE -> g >= 0.33
            // The detector already vetted the stop (peak decel >= 3.0 m/s^2 ~ 0.31 g), so always show it.
            EventType.HARSH_STOP -> true
        }
    }

    /** Speed-aware notability for turns/swerves: more g required the slower you're going. */
    private fun turnNotable(g: Double, speedKmh: Double): Boolean {
        if (speedKmh < TURN_MIN_SPEED_KMH) return false
        val minG = if (speedKmh < TURN_MID_SPEED_KMH) TURN_MIN_G_LOW else TURN_MIN_G_HIGH
        return g >= minG
    }

    private fun isBumpEcho(event: DriveEvent, rawEvents: List<DriveEvent>, points: List<TrackPoint>): Boolean {
        if (event.source != "fused") return false
        if (event.type != EventType.ACCEL && event.type != EventType.BRAKE) return false
        val nearPothole = rawEvents.any {
            it.type == EventType.POTHOLE && kotlin.math.abs(it.tMs - event.tMs) <= 1_000L
        }
        if (!nearPothole) return false
        // A low-confidence brake/accel sitting on a bump is almost always the bump's horizontal shake.
        if (event.confidence < LOW_CONFIDENCE) return true
        // A high-confidence one might be a genuine brake/accel that happened over a bump -- keep it only
        // if the GPS speed actually moved the right way. A bump's shake leaves the speed flat, so veto
        // only when the speed track CONTRADICTS the direction; fail open when GPS context is too thin.
        val slope = speedSlopeKmh(points, event.tMs) ?: return false
        return when (event.type) {
            EventType.ACCEL -> slope < BUMP_ECHO_MIN_SLOPE_KMH    // tagged accel but not actually speeding up
            EventType.BRAKE -> slope > -BUMP_ECHO_MIN_SLOPE_KMH   // tagged brake but not actually slowing
            else -> false
        }
    }

    /** (mean speed after - mean speed before) across the event, km/h; null if GPS context is too thin. */
    private fun speedSlopeKmh(points: List<TrackPoint>, tMs: Long): Double? {
        val before = points.filter { it.tMs in (tMs - 3500)..(tMs - 500) }
        val after = points.filter { it.tMs in (tMs + 500)..(tMs + 3500) }
        if (before.isEmpty() || after.isEmpty()) return null
        return after.sumOf { it.speedKmh } / after.size - before.sumOf { it.speedKmh } / before.size
    }

    private fun speedAt(points: List<TrackPoint>, tMs: Long): Double =
        nearestPoint(points, tMs)?.speedKmh ?: Double.MAX_VALUE

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
            EventType.HARSH_STOP -> 0.30
        }
        val sourceWeight = when (event.source) {
            "gps" -> 0.08
            "fused" -> 0.04 * event.confidence
            else -> 0.0
        }
        return g / threshold + sourceWeight
    }

    private fun typePriority(type: EventType): Int = when (type) {
        // A harsh stop outranks the hard brake that causes it, so a brake+stop cluster shows as the stop.
        EventType.HARSH_STOP -> 6
        EventType.BRAKE -> 5
        EventType.CORNER -> 4
        EventType.POTHOLE -> 3
        EventType.ACCEL -> 2
        EventType.SWERVE -> 1
    }
}
