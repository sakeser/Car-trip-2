package com.cartrip.analyzer.ui

import java.util.Calendar

/**
 * Differentiates trips that resolve to the **same display name** (e.g. two "North York Loop"
 * drives) by appending the compact start *time*, so a list of repeats reads as
 * "North York Loop (10:14am)" / "North York Loop (4:02pm)". The start time is enough to tell repeats
 * apart at a glance; the date is left off (the list already groups trips by recency).
 *
 * Pure + UI-free so it can be unit-tested. Only the *shown* name is suffixed — the underlying
 * `trip.name` used for rename/edit is left untouched by callers.
 */
object TripNaming {

    /** One trip's identity for disambiguation: its id, its resolved base name, and start epoch ms. */
    data class Entry(val id: Long, val baseName: String, val startTimeMs: Long)

    /**
     * Returns id -> shown name. A name owned by a single trip is returned unchanged; a name shared
     * by 2+ trips gets a start-time suffix (time only, never a date).
     */
    fun disambiguate(entries: List<Entry>): Map<Long, String> {
        val byName = entries.groupBy { it.baseName.trim() }
        val out = HashMap<Long, String>(entries.size)
        for ((_, group) in byName) {
            if (group.size <= 1) {
                group.forEach { out[it.id] = it.baseName }
                continue
            }
            for (e in group) {
                out[e.id] = "${e.baseName} (${timeLabel(e.startTimeMs)})"
            }
        }
        return out
    }

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

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
}
