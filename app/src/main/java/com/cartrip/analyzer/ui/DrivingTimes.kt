package com.cartrip.analyzer.ui

import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Buckets trips by time of day so Insights can show "when you drive" patterns (how much you drive and
 * how safely, by daypart). Pure + UI-free for unit testing; the hour comes from the device's local
 * time zone.
 */
object DrivingTimes {
    enum class Daypart(val label: String) {
        MORNING("Morning"),  // 5am - 11am
        MIDDAY("Midday"),    // 11am - 4pm
        EVENING("Evening"),  // 4pm - 9pm
        NIGHT("Night")       // 9pm - 5am
    }

    fun daypartOf(hour: Int): Daypart = when (hour) {
        in 5..10 -> Daypart.MORNING
        in 11..15 -> Daypart.MIDDAY
        in 16..20 -> Daypart.EVENING
        else -> Daypart.NIGHT
    }

    data class Entry(val startTimeMs: Long, val safety: Int?, val distanceKm: Double)

    data class Bucket(
        val part: Daypart,
        val tripCount: Int,
        val avgSafety: Int?,
        val totalKm: Double
    )

    /** One bucket per daypart, always in MORNING..NIGHT order (empty buckets included). */
    fun summarize(entries: List<Entry>): List<Bucket> {
        val byPart = entries.groupBy { daypartOf(hourOf(it.startTimeMs)) }
        return Daypart.values().map { part ->
            val group = byPart[part].orEmpty()
            val safeties = group.mapNotNull { it.safety }
            Bucket(
                part = part,
                tripCount = group.size,
                avgSafety = if (safeties.isEmpty()) null else safeties.average().roundToInt(),
                totalKm = group.sumOf { it.distanceKm }
            )
        }
    }

    private fun hourOf(ms: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
}
