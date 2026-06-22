package com.cartrip.analyzer.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val dateFmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    fun distance(meters: Double): String =
        if (meters < 1000) String.format(Locale.US, "%.0f m", meters)
        else String.format(Locale.US, "%.2f km", meters / 1000.0)

    fun speedKmh(kmh: Double): String = String.format(Locale.US, "%.0f km/h", kmh)

    fun accel(mps2: Double): String = String.format(Locale.US, "%.1f m/s²", mps2)

    fun gforce(g: Double): String = String.format(Locale.US, "%.2f g", g)

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

    fun clock(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun dateTime(epochMs: Long): String = dateFmt.format(Date(epochMs))

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun timeOfDay(epochMs: Long): String = timeFmt.format(Date(epochMs))

    fun dateOnly(epochMs: Long): String = dayFmt.format(Date(epochMs))
}
