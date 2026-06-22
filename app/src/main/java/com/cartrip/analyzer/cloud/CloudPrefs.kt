package com.cartrip.analyzer.cloud

import android.content.Context

object CloudPrefs {
    private const val NAME = "cartrip_cloud"

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun autoSync(c: Context): Boolean = p(c).getBoolean("auto", true)
    fun setAutoSync(c: Context, v: Boolean) = p(c).edit().putBoolean("auto", v).apply()

    fun spreadsheetId(c: Context): String? = p(c).getString("sheet", null)
    fun setSpreadsheetId(c: Context, v: String?) = p(c).edit().putString("sheet", v).apply()

    fun email(c: Context): String? = p(c).getString("email", null)
    fun setEmail(c: Context, v: String?) = p(c).edit().putString("email", v).apply()

    // Signature of the export header layout last written to the sheet, so a schema change
    // re-writes headers instead of leaving old labels above wider rows. 0 = never written.
    fun headerSig(c: Context): Int = p(c).getInt("headerSig", 0)
    fun setHeaderSig(c: Context, v: Int) = p(c).edit().putInt("headerSig", v).apply()
}
