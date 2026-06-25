package com.cartrip.analyzer.ui

import java.util.Calendar

/**
 * Differentiates trips that resolve to the **same display name** (e.g. two "North York Loop"
 * drives) by appending a compact start-time suffix, so a list of repeats reads as
 * "North York Loop (10:14am)" / "North York Loop (24 Jun, 4:02pm)".
 *
 * Pure + UI-free so it can be unit-tested. Only the *shown* name is suffixed — the underlying
 * `trip.name` used for rename/edit is left untouched by callers.
 */
object TripNaming {

    /** One trip's identity for disambiguation: its id, its resolved base name, and start epoch ms. */
    data class Entry(val id: Long, val baseName: String, val startTimeMs: Long)

    /**
     * Returns id -> shown name. A name owned by a single trip is returned unchanged; a name shared
     * by 2+ trips gets a suffix: just the time when all the clashers fall on one calendar day,
     * otherwise date + time.
     */
    fun disambiguate(entries: List<Entry>): Map<Long, String> {
        val byName = entries.groupBy { it.baseName.trim() }
        val out = HashMap<Long, String>(entries.size)
        for ((_, group) in byName) {
            if (group.size <= 1) {
                group.forEach { out[it.id] = it.baseName }
                continue
            }
            val sameDay = group.map { dayKey(it.startTimeMs) }.distinct().size == 1
            for (e in group) {
                val suffix = if (sameDay) timeLabel(e.startTimeMs)
                else "${dateLabel(e.startTimeMs)}, ${timeLabel(e.startTimeMs)}"
                out[e.id] = "${e.baseName} ($suffix)"
            }
        }
        return out
    }

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    private fun dayKey(ms: Long): Long {
        val c = cal(ms)
        return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR)
    }

    /** "10:14am" / "4:02pm" — lowercase, no minutes-zero quirks, no dots. */
    private fun timeLabel(ms: Long): String {
        val c = cal(ms)
        val h24 = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val ampm = if (h24 < 12) "am" else "pm"
        var h = h24 % 12
        if (h == 0) h = 12
        return "$h:${m.toString().padStart(2, '0')}$ampm"
    }

    /** "24 Jun" — compact day + month. */
    private fun dateLabel(ms: Long): String {
        val c = cal(ms)
        val month = MONTHS[c.get(Calendar.MONTH)]
        return "${c.get(Calendar.DAY_OF_MONTH)} $month"
    }

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
}
