package com.cartrip.analyzer.record

import android.content.Context

/**
 * Settings for hands-free auto-recording (see [AutoRecordPolicy]). Off by default — the user opts in
 * from the Auto-record screen. Charging is the primary trigger; Bluetooth (a specific car device) is an
 * optional one.
 */
object AutoRecordPrefs {
    private const val NAME = "cartrip_autorecord"
    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun requireWireless(c: Context) = p(c).getBoolean("requireWireless", false)
    fun setRequireWireless(c: Context, v: Boolean) = p(c).edit().putBoolean("requireWireless", v).apply()

    fun useBluetooth(c: Context) = p(c).getBoolean("useBluetooth", false)
    fun setUseBluetooth(c: Context, v: Boolean) = p(c).edit().putBoolean("useBluetooth", v).apply()

    fun carBtAddress(c: Context): String? = p(c).getString("carBtAddress", null)
    fun carBtName(c: Context): String? = p(c).getString("carBtName", null)
    fun setCarBt(c: Context, address: String?, name: String?) =
        p(c).edit().putString("carBtAddress", address).putString("carBtName", name).apply()

    fun config(c: Context) = AutoRecordPolicy.Config(
        enabled = enabled(c),
        requireWireless = requireWireless(c),
        useBluetooth = useBluetooth(c),
    )

    // Service-side tuning (not user-facing).
    const val MIN_SPEED_KMH = 5.0          // a drive must reach this within the confirm window
    const val MOTION_CONFIRM_MS = 45_000L  // else the provisional trip is discarded (parked charging)
    const val STOP_GRACE_MS = 8_000L       // wait this long after the trigger drops before stopping
}
