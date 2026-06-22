package com.cartrip.analyzer.export

import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the row data shared by the local .xlsx export and the Google Sheets sync,
 * so both stay in lock-step.
 */
object ExportData {

    val SUMMARY_HEADER = listOf(
        "TripId", "Start", "End", "Distance_km", "Duration_min", "Idle_min",
        "MaxSpeed_kmh", "AvgMoving_kmh", "MaxAccel_mps2", "MaxBrake_mps2", "MaxLateral_mps2",
        "PeakG", "HardAccel", "HardBrake", "HardCorner", "Smoothness",
        "UsedFixes", "RawFixes", "StartLat", "StartLon", "EndLat", "EndLon"
    )

    val SAMPLE_HEADER = listOf(
        "TripId", "Time_s", "Lat", "Lon", "Speed_kmh", "LongAccel_mps2", "LatAccel_mps2"
    )

    val EVENT_HEADER = listOf("TripId", "Time_s", "Type", "Magnitude_mps2")

    private val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun summaryRow(trip: TripEntity, a: TripAnalysis): List<String> {
        val m = a.metrics
        val start = a.points.firstOrNull()
        val end = a.points.lastOrNull()
        return listOf(
            trip.id.toString(),
            iso.format(Date(trip.startTime)),
            if (trip.endTime > 0) iso.format(Date(trip.endTime)) else "",
            f(m.distanceM / 1000.0, 3),
            f(m.durationS / 60.0, 2),
            f(m.idleS / 60.0, 2),
            f(m.maxSpeedMps * 3.6, 1),
            f(m.avgMovingSpeedMps * 3.6, 1),
            f(m.maxAccelMps2, 2),
            f(m.maxBrakeMps2, 2),
            f(m.maxLateralMps2, 2),
            f(m.peakGForce, 2),
            m.hardAccelCount.toString(),
            m.hardBrakeCount.toString(),
            m.hardCornerCount.toString(),
            m.smoothness.toString(),
            m.usedFixes.toString(),
            m.rawFixes.toString(),
            start?.let { f(it.lat, 6) } ?: "",
            start?.let { f(it.lon, 6) } ?: "",
            end?.let { f(it.lat, 6) } ?: "",
            end?.let { f(it.lon, 6) } ?: ""
        )
    }

    /** Per-sample rows. [cap] limits row count (cloud); pass a large value for full local detail. */
    fun sampleRows(tripId: Long, a: TripAnalysis, cap: Int = 5000): List<List<String>> {
        val pts = a.points
        if (pts.isEmpty()) return emptyList()
        val t0 = pts.first().tMs
        val step = if (pts.size > cap) pts.size.toDouble() / cap else 1.0
        val out = ArrayList<List<String>>()
        var idx = 0.0
        while (idx < pts.size) {
            val p = pts[idx.toInt()]
            out.add(
                listOf(
                    tripId.toString(),
                    f((p.tMs - t0) / 1000.0, 1),
                    f(p.lat, 6),
                    f(p.lon, 6),
                    f(p.speedKmh, 1),
                    f(p.longAccel, 2),
                    f(p.latAccel, 2)
                )
            )
            idx += step
        }
        return out
    }

    fun eventRows(tripId: Long, a: TripAnalysis): List<List<String>> {
        val t0 = a.points.firstOrNull()?.tMs ?: 0L
        return a.events.map {
            listOf(
                tripId.toString(),
                f((it.tMs - t0) / 1000.0, 1),
                it.type.name,
                f(it.magnitude, 2)
            )
        }
    }

    private fun f(v: Double, dec: Int): String = String.format(Locale.US, "%.${dec}f", v)
}
