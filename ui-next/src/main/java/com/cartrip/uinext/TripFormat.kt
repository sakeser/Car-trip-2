package com.cartrip.uinext

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared trip-summary formatters for the :ui-next screens (list + detail). ASCII-only source on purpose
 * (this Windows build mojibakes non-ASCII string literals in BOM-less .kt files); the middle-dot separator
 * is built from a code point rather than pasted.
 */

/** Middle dot (U+00B7) built from a code point so the source stays ASCII. */
internal val MIDDOT: Char = 0x00B7.toChar()

private val startFormat = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())

internal fun formatStart(epochMs: Long): String =
    if (epochMs <= 0L) "-" else startFormat.format(Date(epochMs))

internal fun formatKm(meters: Double): String = "%.1f km".format(meters / 1000.0)

internal fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
