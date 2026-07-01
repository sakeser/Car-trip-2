package com.cartrip.uinext

import com.cartrip.engine.api.TripSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure analytics aggregations for the Insights tab's charts — derived only from existing [TripSummary] fields
 * (`startEpochMs` / `distanceMeters` / `isDrive`). No scoring logic; read-only. `nowMs` / `zone` are parameters
 * so the day bucketing is deterministic and unit-testable. minSdk 26 => java.time is available. ASCII source.
 */

/** Short weekday label (Mon, Tue, ...) for the daily-distance x-axis. */
private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE")

/**
 * Total DRIVING distance (km) per calendar day for the last [days] days ending on [nowMs]'s date (in [zone]),
 * oldest -> newest. Days with no drives are included with 0.0 so the axis stays continuous. Non-drives excluded.
 */
fun List<TripSummary>.dailyDistanceKm(
    days: Int,
    nowMs: Long,
    zone: ZoneId,
): List<Pair<String, Double>> {
    if (days <= 0) return emptyList()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val startDay = today.minusDays((days - 1).toLong())
    val byDay = HashMap<LocalDate, Double>()
    for (t in this) {
        if (!t.isDrive) continue
        val d = Instant.ofEpochMilli(t.startEpochMs).atZone(zone).toLocalDate()
        if (d < startDay || d > today) continue
        byDay[d] = (byDay[d] ?: 0.0) + t.distanceMeters / 1000.0
    }
    return (0 until days).map { i ->
        val d = startDay.plusDays(i.toLong())
        d.format(DAY_LABEL) to (byDay[d] ?: 0.0)
    }
}

/** The fixed time-of-day parts, in display order, with their [start, end) hour ranges. */
private val DAYPARTS = listOf(
    "Night" to 0..5,
    "Morning" to 6..10,
    "Midday" to 11..13,
    "Afternoon" to 14..17,
    "Evening" to 18..23,
)

/**
 * Count of DRIVES by time-of-day part (from each trip's start hour in [zone]), in the fixed order
 * Night / Morning / Midday / Afternoon / Evening. Every part is present (0 when none). Non-drives excluded.
 */
fun List<TripSummary>.daypartCounts(zone: ZoneId): List<Pair<String, Int>> {
    val counts = IntArray(DAYPARTS.size)
    for (t in this) {
        if (!t.isDrive) continue
        val hour = Instant.ofEpochMilli(t.startEpochMs).atZone(zone).hour
        val idx = DAYPARTS.indexOfFirst { hour in it.second }
        if (idx >= 0) counts[idx]++
    }
    return DAYPARTS.mapIndexed { i, (label, _) -> label to counts[i] }
}
