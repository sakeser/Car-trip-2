package com.cartrip.analyzer.cloud

import android.content.Context

/**
 * Master switch for OPTIONAL online trip-enrichment that contacts third-party services with route
 * geometry: OSM Overpass posted speed limits + Google Routes traffic-time (ETA). **ON by default
 * (opt-out) and disclosed** — the app stays local-first by *architecture*: recording, sensor analysis,
 * scoring, and history are on-device regardless of this flag. With it OFF, trips still record and
 * analyze fully on-device; speed-limit / ETA / traffic-context insights are reduced or absent.
 *
 * Owner decision 2026-06-30 (ADVISORY_ASSESSMENT §2.3): opt-out + disclosed, implemented as a product
 * strength, not a legal apology. This flag does NOT govern Sheets sync (separate, opt-in via
 * [CloudPrefs.autoSync]), the free on-device Geocoder, or the public gas-price file (no personal data).
 */
object ConnectedFeaturesPrefs {
    private const val PREFS = "cartrip_connected"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
}
