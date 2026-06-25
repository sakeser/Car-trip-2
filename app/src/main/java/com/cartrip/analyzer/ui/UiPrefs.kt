package com.cartrip.analyzer.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
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

    fun setYouIcon(ctx: Context, icon: YouIcon) {
        prefs(ctx).edit().putString(KEY_YOU_ICON, icon.key).apply()
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
