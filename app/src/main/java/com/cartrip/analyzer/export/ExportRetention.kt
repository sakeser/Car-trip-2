package com.cartrip.analyzer.export

/**
 * Retention policy for the per-trip `.xlsx` files written to external app storage. Those files live
 * **outside** the app's DB and any at-rest encryption, are world-... no, app-private but unencrypted, and
 * one is written on every trip finalize — so without a sweep they accumulate forever. We keep them for a
 * bounded window (mirroring the 30-day raw-sensor retention) and cap the count, then prune the rest.
 *
 * Pure + unit-testable: [toDelete] decides *which* entries to remove; the actual file IO lives in TripExcel.
 */
object ExportRetention {
    /** Keep exported files this long, then prune (mirrors RAW_SENSOR_RETENTION_MS). */
    const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    /** Hard cap on retained export files regardless of age (newest kept). */
    const val MAX_FILES = 50

    data class Entry(val name: String, val lastModified: Long)

    /**
     * Entries to delete: anything older than [maxAgeMs], OR — among what's left — beyond the newest
     * [maxFiles]. Order-independent input; returns a subset of [entries]. Pure.
     */
    fun toDelete(
        entries: List<Entry>,
        nowMs: Long,
        maxAgeMs: Long = MAX_AGE_MS,
        maxFiles: Int = MAX_FILES
    ): List<Entry> {
        val tooOld = entries.filterTo(HashSet()) { nowMs - it.lastModified > maxAgeMs }
        val overCap = entries.asSequence()
            .filter { it !in tooOld }
            .sortedByDescending { it.lastModified }
            .drop(maxFiles.coerceAtLeast(0))
            .toHashSet()
        return entries.filter { it in tooOld || it in overCap }
    }
}
