package com.cartrip.analyzer.ui

import android.content.Context
import android.content.pm.PackageManager

object GoogleMapConfig {
    private const val MAPS_KEY_META = "com.google.android.geo.API_KEY"
    private const val MAP_ID_META = "com.cartrip.analyzer.GOOGLE_MAP_ID"

    fun hasApiKey(context: Context): Boolean =
        metaValue(context, MAPS_KEY_META)?.isNotBlank() == true

    fun mapId(context: Context): String? =
        metaValue(context, MAP_ID_META)?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun metaValue(context: Context, key: String): String? =
        runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getString(key)
        }.getOrNull()
}
