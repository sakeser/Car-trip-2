package com.cartrip.analyzer.ui

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

object Format {
    // Compact lowercase am/pm (no dots), e.g. "7:07am".
    private val amPmSymbols = DateFormatSymbols(Locale.getDefault()).apply {
        amPmStrings = arrayOf("am", "pm")
    }
    private val dateFmt = SimpleDateFormat("EEE d MMM, h:mma", Locale.getDefault())
        .apply { dateFormatSymbols = amPmSymbols }

    fun distance(meters: Double): String =
        if (meters < 1000) String.format(Locale.US, "%.0f m", meters)
        else String.format(Locale.US, "%.2f km", meters / 1000.0)

    fun speedKmh(kmh: Double): String = String.format(Locale.US, "%.0f km/h", kmh)

    fun accel(mps2: Double): String = String.format(Locale.US, "%.1f m/s²", mps2)

    /** Acceleration as a g-force, e.g. "0.43g" — the human-friendly form (no m/s²). */
    fun accelG(mps2: Double): String = String.format(Locale.US, "%.2fg", mps2 / 9.80665)

    fun gforce(g: Double): String = String.format(Locale.US, "%.2fg", g)

    fun duration(seconds: Double): String {
        val s = seconds.toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m)
        else String.format(Locale.US, "%dm %02ds", m, sec)
    }

    fun tripDistance(meters: Double): String =
        if (meters < 1000) String.format(Locale.US, "%.0f m", meters)
        else String.format(Locale.US, "%.1f km", meters / 1000.0)

    fun tripMinutes(seconds: Double): String =
        "${(seconds / 60.0).roundToInt().coerceAtLeast(1)} min"

    /**
     * Whole minutes, rounded DOWN (floored) — e.g. 3m47s -> "3 min", 47s -> "<1 min".
     * Used for the speeding-duration readout, where over-stating the time felt misleading.
     */
    fun durationFloorMin(seconds: Double): String {
        val m = floor(seconds / 60.0).toInt()
        return if (m < 1) "<1 min" else "$m min"
    }

    fun clock(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun dateTime(epochMs: Long): String = dateFmt.format(Date(epochMs))

    private val timeFmt = SimpleDateFormat("h:mma", Locale.getDefault())
        .apply { dateFormatSymbols = amPmSymbols }
    private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun timeOfDay(epochMs: Long): String = timeFmt.format(Date(epochMs))

    fun dateOnly(epochMs: Long): String = dayFmt.format(Date(epochMs))

    /** "Today" / "Yesterday" for recent trips, otherwise the full date ("3 Jun 2026"). */
    fun relativeDay(epochMs: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = epochMs }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(then, now) -> "Today"
            sameDay(then, yesterday) -> "Yesterday"
            else -> dateOnly(epochMs)
        }
    }
}
