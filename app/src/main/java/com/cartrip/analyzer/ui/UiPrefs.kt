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

    private const val KEY_EVENT_G = "event_g_threshold"
    /** Default minimum g-force for an event to count toward a trouble-spot hotspot (user-tunable). */
    const val DEFAULT_EVENT_G = 0.35f

    /** Minimum event g-force for trouble-spot hotspots (drops weak/marginal events). */
    fun eventGThreshold(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_EVENT_G, DEFAULT_EVENT_G)

    fun setEventGThreshold(ctx: Context, g: Float) {
        prefs(ctx).edit().putFloat(KEY_EVENT_G, g).apply()
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
