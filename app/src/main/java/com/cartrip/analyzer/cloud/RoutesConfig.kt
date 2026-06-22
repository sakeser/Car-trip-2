package com.cartrip.analyzer.cloud

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Supplies the credentials the Routes API call needs: the Maps API key (shared with the
 * Maps SDK) and, for Android-app-restricted keys, this build's package + signing SHA-1.
 */
object RoutesConfig {
    private const val MAPS_KEY_META = "com.google.android.geo.API_KEY"

    @Suppress("DEPRECATION")
    fun apiKey(context: Context): String? =
        runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString(MAPS_KEY_META)
        }.getOrNull()?.takeIf { it.isNotBlank() }

    fun androidPackage(context: Context): String = context.packageName

    /** Uppercase hex SHA-1 of the signing certificate, no separators — the X-Android-Cert format. */
    fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val sig = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
        digest.joinToString("") { "%02X".format(it) }
    }.getOrNull()

    /** RFC-3339 (UTC) for "now plus a small lead", a valid departureTime for a live snapshot. */
    fun nowDeparture(leadSeconds: Int = 30): String =
        rfc3339(System.currentTimeMillis() + leadSeconds * 1000L)

    /**
     * RFC-3339 for the next future occurrence of the same weekday + clock-time as [pastEpochMs],
     * giving Google a basis to predict *typical* traffic for that slot.
     */
    fun typicalDepartureFor(pastEpochMs: Long): String {
        val src = Calendar.getInstance().apply { timeInMillis = pastEpochMs }
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, src.get(Calendar.DAY_OF_WEEK))
            set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, src.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
        }
        // Roll forward until it's safely in the future.
        while (target.timeInMillis <= System.currentTimeMillis() + 120_000L) {
            target.add(Calendar.DAY_OF_YEAR, 7)
        }
        return rfc3339(target.timeInMillis)
    }

    private fun rfc3339(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }
}
