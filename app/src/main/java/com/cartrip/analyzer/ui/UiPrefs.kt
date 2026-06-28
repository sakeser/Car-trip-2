package com.cartrip.analyzer.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Small UI personalization stored in SharedPreferences. Currently just the glyph used to represent
 * "you" / your car in the You-vs-traffic comparison; kept separate from VehiclePrefs (which is the
 * fuel profile) so it can grow with other cosmetic options from the Home "Options" menu.
 */
object UiPrefs {
    private const val PREFS = "cartrip_ui"
    private const val KEY_YOU_ICON = "you_icon"

    /** Selectable glyphs for the "you" marker. [key] is the stable persisted value. */
    enum class YouIcon(val key: String, val label: String) {
        CAR("car", "Car"),
        ARROW("arrow", "Arrow"),
        PERSON("person", "Person"),
        DOT("dot", "Dot");

        companion object {
            fun fromKey(k: String?): YouIcon = values().firstOrNull { it.key == k } ?: CAR
        }
    }

    fun youIcon(ctx: Context): YouIcon =
        YouIcon.fromKey(prefs(ctx).getString(KEY_YOU_ICON, null))

    /**
     * Observe the selected "you" icon as Compose state. Reads fresh on each composition AND updates
     * live when the Options picker changes it, so the map replay marker always matches the choice
     * (a plain `remember { youIcon() }` would cache the value and ignore later changes).
     */
    @Composable
    fun rememberYouIcon(ctx: Context): YouIcon {
        val p = remember(ctx) { prefs(ctx) }
        var icon by remember { mutableStateOf(youIcon(ctx)) }
        DisposableEffect(p) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == null || key == KEY_YOU_ICON) icon = YouIcon.fromKey(sp.getString(KEY_YOU_ICON, null))
            }
            p.registerOnSharedPreferenceChangeListener(listener)
            onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return icon
    }

    fun setYouIcon(ctx: Context, icon: YouIcon) {
        prefs(ctx).edit().putString(KEY_YOU_ICON, icon.key).apply()
    }

    // v2 key: reset to the lower 0.25 default (0.35 hid every hotspot on real data).
    private const val KEY_EVENT_G = "event_g_threshold2"
    /** Default minimum g-force for an event to count toward a trouble-spot hotspot (user-tunable slider). */
    const val DEFAULT_EVENT_G = 0.25f
    const val EVENT_G_MIN = 0.20f
    const val EVENT_G_MAX = 0.50f

    /** Minimum event g-force for trouble-spot hotspots (drops weak/marginal events). */
    fun eventGThreshold(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_EVENT_G, DEFAULT_EVENT_G)

    fun setEventGThreshold(ctx: Context, g: Float) {
        prefs(ctx).edit().putFloat(KEY_EVENT_G, g).apply()
    }

    private const val KEY_SATELLITE = "map_satellite"

    /** Whether maps render in satellite/aerial (hybrid) mode instead of the normal road map. */
    fun satelliteMap(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SATELLITE, false)

    fun setSatelliteMap(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SATELLITE, on).apply()
    }

    /**
     * Observe the satellite-map toggle as Compose state, updating live across every map when any map's
     * toggle flips it (same pattern as [rememberYouIcon]).
     */
    @Composable
    fun rememberSatelliteMap(ctx: Context): Boolean {
        val p = remember(ctx) { prefs(ctx) }
        var on by remember { mutableStateOf(satelliteMap(ctx)) }
        DisposableEffect(p) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == null || key == KEY_SATELLITE) on = sp.getBoolean(KEY_SATELLITE, false)
            }
            p.registerOnSharedPreferenceChangeListener(listener)
            onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return on
    }

    fun vector(icon: YouIcon): ImageVector = when (icon) {
        YouIcon.CAR -> Icons.Filled.DirectionsCar
        YouIcon.ARROW -> Icons.Filled.Navigation
        YouIcon.PERSON -> Icons.Filled.Person
        YouIcon.DOT -> Icons.Filled.Circle
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
