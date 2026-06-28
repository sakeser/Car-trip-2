package com.cartrip.analyzer.cloud

import android.content.Context

/**
 * Feature flag for POI endpoint naming via the paid Places API (New). Defaults OFF - the app must
 * stay free-to-run, and Places is a metered/billed API. Turning it on only does anything once the owner has
 * enabled "Places API (New)" + billing on the Maps key. While off, naming uses the free on-device Geocoder
 * exactly as before (zero cost, zero behaviour change).
 */
object PlacesPrefs {
    private const val PREFS = "cartrip_places"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
}
