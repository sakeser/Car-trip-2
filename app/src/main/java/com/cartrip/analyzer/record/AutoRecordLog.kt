package com.cartrip.analyzer.record

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small persistent ring buffer of auto-record decisions and outcomes, so a hands-free field test is
 * auditable afterward — the receivers/controller run in the background where logcat is quickly lost.
 * Surfaced in the Diagnostics screen. No location/PII, just trigger states and start/stop outcomes.
 *
 * Built because field test 2026-06-25 couldn't confirm why auto-start did/didn't fire (only one pre-8am
 * trip for two runs; logcat gone). This makes the decision path visible after the next drive.
 */
object AutoRecordLog {
    private const val PREFS = "cartrip_autorecord_log"
    private const val KEY = "entries"
    private const val MAX = 80
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun add(context: Context, msg: String) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val line = "${fmt.format(Date())}  $msg"
        val kept = (p.getString(KEY, "").orEmpty().split("\n").filter { it.isNotBlank() } + line).takeLast(MAX)
        p.edit().putString(KEY, kept.joinToString("\n")).apply()
    }

    /** Most-recent first. */
    fun entries(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().split("\n").filter { it.isNotBlank() }.asReversed()

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
