package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.data.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds a compact, structured **markdown summary** of the user's driving that they can paste/share into an
 * AI assistant (ChatGPT / Claude / etc.) to get personalized insights — without any raw GPS leaving the
 * device (only aggregate, already-on-device metrics; principle: send tiny structured summaries, not logs).
 *
 * Pure + unit-testable. Recent trips are one-liners; the header is a ready-to-use coaching prompt.
 */
object AiInsightsExport {
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dt = SimpleDateFormat("MMM d HH:mm", Locale.US)
    private const val RECENT_LIMIT = 25

    fun build(
        trips: List<TripEntity>,
        names: Map<Long, String>,
        vehicle: FuelEstimator.Vehicle,
        hotspots: List<EventHotspots.Hotspot>,
        nowMs: Long
    ): String {
        val drives = trips
            .filter { it.analyzed && it.endTime > 0 && it.distanceM > 0 && !TripKind.isLikelyNonDrive(it) }
            .sortedBy { it.startTime }
        val sb = StringBuilder()
        sb.appendLine("# My driving data (for AI analysis)")
        sb.appendLine()
        sb.appendLine(
            "You are an expert driving coach. Using the aggregated data below (no raw GPS), give me " +
                "specific, actionable insights about my safety, smoothness, efficiency, fuel cost and " +
                "stress, point out patterns, and list the top 3 things to improve. Be concise and concrete."
        )
        sb.appendLine()
        if (drives.isEmpty()) {
            sb.appendLine("(No analyzed drives yet.)")
            return sb.toString()
        }

        val totalKm = drives.sumOf { it.distanceM } / 1000.0
        val totalHr = drives.sumOf { it.durationS } / 3600.0
        val first = day.format(Date(drives.first().startTime))
        val last = day.format(Date(drives.last().startTime))
        val fuel = FuelInsights.summarize(drives, vehicle)

        sb.appendLine("## Overview")
        sb.appendLine("- Drives: ${drives.size} (walks/non-drives excluded), $first to $last")
        sb.appendLine("- Distance: ${totalKm.roundToInt()} km, drive time: ${"%.1f".format(totalHr)} h")
        sb.appendLine("- Vehicle: ${vehicle.label}; est. fuel spend ${money(fuel.totalCost)} " +
            "(${"%.1f".format(fuel.avgL100)} L/100km, ${money(fuel.avgCostPerKm)}/km)")
        sb.appendLine()

        val scores = drives.map { TripScores.from(it) }
        fun avg(xs: List<Int>) = if (xs.isEmpty()) 0 else xs.average().roundToInt()
        val stresses = drives.mapNotNull { StressScore.from(it)?.score }
        sb.appendLine("## Averages (0-100; higher is better, except stress)")
        sb.appendLine("- Safety ${avg(scores.map { it.safety })}, " +
            "Comfort ${avg(scores.map { it.comfort })}, " +
            "Pace ${avg(scores.mapNotNull { it.speed })}")
        if (stresses.isNotEmpty()) {
            val s = avg(stresses)
            sb.appendLine("- Drive stress $s (${StressScore.band(s)}; higher = more demanding)")
        }
        val km = totalKm.coerceAtLeast(1.0)
        val hardEvents = drives.sumOf { it.hardBrakeCount + it.hardAccelCount + it.hardCornerCount }
        sb.appendLine("- Hard events per 100 km: ${"%.1f".format(hardEvents / km * 100.0)}")
        sb.appendLine("- Forced slowdowns (drawdowns) per drive: " +
            "${"%.1f".format(drives.sumOf { it.drawdownCount }.toDouble() / drives.size)}")
        val speedTrips = drives.filter { it.limitCoverage >= 0.4 }
        if (speedTrips.isNotEmpty()) {
            sb.appendLine("- Time over the speed limit: " +
                "${(speedTrips.sumOf { it.speedingPct } / speedTrips.size * 100).roundToInt()}% " +
                "(on roads with known limits)")
        }
        sb.appendLine()

        if (hotspots.isNotEmpty()) {
            sb.appendLine("## Recurring trouble spots")
            hotspots.take(8).forEach { h ->
                val where = if (h.where.isNotEmpty()) " near ${h.where}" else ""
                val peak = h.instances.maxOfOrNull { it.gForce }?.let { " (peak ${"%.2f".format(it)}g)" } ?: ""
                sb.appendLine("- ${h.kind}$where: ${h.count} times across ${h.trips} drives$peak")
            }
            sb.appendLine()
        }

        sb.appendLine("## Recent drives (most recent first)")
        drives.takeLast(RECENT_LIMIT).reversed().forEach { t ->
            val sc = TripScores.from(t)
            val stress = StressScore.from(t)?.let { ", stress ${it.band}" } ?: ""
            val name = names[t.id] ?: t.name.ifBlank { "Trip" }
            sb.appendLine(
                "- ${dt.format(Date(t.startTime))} $name: " +
                    "${"%.1f".format(t.distanceM / 1000.0)}km, " +
                    "${(t.durationS / 60).roundToInt()}min, " +
                    "Safety ${sc.safety}/Comfort ${sc.comfort}/Pace ${sc.speed ?: '-'}" +
                    "$stress, ${t.drawdownCount} drawdowns"
            )
        }
        return sb.toString()
    }

    private fun money(v: Double) = "$" + String.format(Locale.US, "%.2f", v)
}
