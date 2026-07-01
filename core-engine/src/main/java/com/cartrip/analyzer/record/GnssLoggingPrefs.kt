package com.cartrip.analyzer.record

import android.content.Context

/**
 * Toggle for high-precision raw GNSS measurement logging (per-satellite carrier phase + Doppler). Off by
 * default because it's voluminous (~20-40 rows/sec); turn it on from Diagnostics only for a deliberate
 * lane-detection calibration drive. Read by [RecordingService] when a recording starts.
 */
object GnssLoggingPrefs {
    private const val NAME = "cartrip_gnss"
    private const val KEY = "rawMeasurements"

    fun rawEnabled(c: Context): Boolean =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setRawEnabled(c: Context, v: Boolean) =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY, v).apply()
}
