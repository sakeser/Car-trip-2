package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.DrivingIntelligence
import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.analysis.TripKind
import com.cartrip.analyzer.analysis.TripScores
import com.cartrip.analyzer.data.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
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
            "You are an expert driving coach. Using the aggregated data below (no raw GPS), coach me with the " +
                "three-pillar Driving Intelligence model: Smoothness (my driving style/control), Demand/Load " +
                "(how hard the road and traffic were — context, not my fault), and Efficiency (fuel and cost). " +
                "Crucially, separate what I controlled (style) from what the road imposed (demand): a smoothly " +
                "handled high-demand trip is good driving, while a rough low-demand trip is the most actionable. " +
                "Give specific, concrete insights and the top 3 things to improve. Be concise."
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

        // Driving Intelligence: the three-pillar roll-up + the style x demand trip mix. The point for the AI is
        // to coach style and demand separately (don't blame the driver for traffic).
        val dis = drives.mapNotNull { DrivingIntelligence.from(it, vehicle, fuel.avgL100) }
        if (dis.isNotEmpty()) {
            sb.appendLine("## Driving Intelligence (style vs demand vs outcome)")
            sb.appendLine("- Smoothness (style, higher better): ${avg(dis.map { it.smoothness.score })}")
            sb.appendLine("- Demand/Load (context, higher = harder drive): ${avg(dis.map { it.demand.score })}")
            dis.mapNotNull { it.efficiency?.score }.let {
                if (it.isNotEmpty()) sb.appendLine("- Efficiency (outcome, higher better): ${avg(it)}")
            }
            val byQuad = dis.groupingBy { it.quadrant }.eachCount()
            val mix = DrivingIntelligence.Quadrant.values()
                .filter { (byQuad[it] ?: 0) > 0 }
                .joinToString(", ") { "${quadName(it)} ${byQuad[it]}" }
            sb.appendLine("- Trip mix (style x demand): $mix")
            sb.appendLine()
        }

        // Traffic: how the user's actual time compares to Google's live-traffic estimate, and how much
        // slower than free-flow (no-traffic) their drives ran — the congestion they actually sat in.
        val etaTrips = drives.filter { it.googleEtaTrafficS > 0.0 }
        val freeTrips = drives.filter { it.googleEtaFreeFlowS > 0.0 }
        if (etaTrips.isNotEmpty() || freeTrips.isNotEmpty()) {
            sb.appendLine("## Traffic")
            if (etaTrips.isNotEmpty()) {
                val vs = etaTrips.map { (it.durationS - it.googleEtaTrafficS) / it.googleEtaTrafficS }
                    .average() * 100.0
                val word = if (vs <= 0.0) "faster than" else "slower than"
                sb.appendLine("- You vs Google's live-traffic estimate: ${"%.0f".format(abs(vs))}% $word " +
                    "the estimate across ${etaTrips.size} drives with ETA data")
            }
            if (freeTrips.isNotEmpty()) {
                val cong = (freeTrips.map { it.durationS / it.googleEtaFreeFlowS - 1.0 }.average() * 100.0)
                    .coerceAtLeast(0.0)
                sb.appendLine("- Congestion vs free-flow (no traffic): drives ran ${"%.0f".format(cong)}% " +
                    "longer than clear roads, across ${freeTrips.size} drives")
            }
            sb.appendLine()
        }

        // When you drive: daypart distribution + which part of the day is your safest / least safe.
        val activeParts = DrivingTimes.summarize(
            drives.map { DrivingTimes.Entry(it.startTime, TripScores.from(it).safety, it.distanceM / 1000.0) }
        ).filter { it.tripCount > 0 }
        if (activeParts.isNotEmpty()) {
            sb.appendLine("## When you drive")
            sb.appendLine("- By daypart: " + activeParts.joinToString(", ") {
                "${it.part.label} ${it.tripCount} (${it.totalKm.roundToInt()} km)"
            })
            val rated = activeParts.filter { it.avgSafety != null }
            if (rated.size >= 2) {
                val safest = rated.maxByOrNull { it.avgSafety!! }!!
                val worst = rated.minByOrNull { it.avgSafety!! }!!
                if (safest.part != worst.part) {
                    sb.appendLine("- Safest in the ${safest.part.label.lowercase()} (avg Safety " +
                        "${safest.avgSafety}); least safe in the ${worst.part.label.lowercase()} " +
                        "(avg Safety ${worst.avgSafety})")
                }
            }
            sb.appendLine()
        }

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
            val verdict = DrivingIntelligence.from(t, vehicle, fuel.avgL100)?.let { " [${it.headline}]" } ?: ""
            val name = names[t.id] ?: t.name.ifBlank { "Trip" }
            sb.appendLine(
                "- ${dt.format(Date(t.startTime))} $name:$verdict " +
                    "${"%.1f".format(t.distanceM / 1000.0)}km, " +
                    "${(t.durationS / 60).roundToInt()}min, " +
                    "Safety ${sc.safety}/Comfort ${sc.comfort}/Pace ${sc.speed ?: '-'}" +
                    "$stress, ${t.drawdownCount} drawdowns"
            )
        }
        return sb.toString()
    }

    private fun quadName(q: DrivingIntelligence.Quadrant): String = when (q) {
        DrivingIntelligence.Quadrant.EASY_SMOOTH -> "easy & smooth"
        DrivingIntelligence.Quadrant.SMOOTH_UNDER_PRESSURE -> "smooth under pressure"
        DrivingIntelligence.Quadrant.ROUGH_ON_EASY_ROAD -> "rough on easy roads"
        DrivingIntelligence.Quadrant.DEMANDING_AND_ROUGH -> "demanding & rough"
    }

    private fun money(v: Double) = "$" + String.format(Locale.US, "%.2f", v)
}
