package com.cartrip.analyzer.cloud

import android.content.Context
import com.cartrip.analyzer.settings.VehiclePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Auto-updates the fuel price from Ontario's **weekly fuel-price survey** (free, official open data):
 * the most recent week's Toronto regular-gasoline average. Manual price entry still works; this just
 * keeps it current when the user has auto-update on. Fail-soft: any network/parse problem leaves the
 * existing price untouched.
 *
 * Source: https://data.ontario.ca/dataset/fuels-price-survey-information ("fueltypesall.csv").
 */
object GasPrice {

    private const val URL = "https://ontario.ca/v1/files/fuel-prices/fueltypesall.csv"
    private const val FUEL_TYPE = "Regular Unleaded Gasoline"
    // Sanity band so a bad parse / format change can never set an absurd price.
    private const val MIN_PER_L = 0.50
    private const val MAX_PER_L = 5.00

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** The label shown in the UI for where the price came from. */
    const val SOURCE_LABEL = "Toronto weekly average (Ontario)"

    /**
     * Pure: parse the survey CSV → the latest week's Toronto regular-gas price in **$/L** (avg of the
     * Toronto West/East columns, which are in ¢/L), or null if it can't be found / is out of range.
     */
    fun parseLatestTorontoRegular(csv: String): Double? {
        val lines = csv.split('\n').map { it.trim('\r', ' ') }.filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val header = lines.first().split(',')
        fun col(name: String) = header.indexOfFirst { it.trim().equals(name, ignoreCase = true) }
        val iDate = col("Date")
        val iWest = col("Toronto West/Ouest")
        val iEast = col("Toronto East/Est")
        val iType = col("Fuel Type")
        if (iDate < 0 || iWest < 0 || iEast < 0 || iType < 0) return null

        var bestDate = ""
        var bestPerL: Double? = null
        for (i in 1 until lines.size) {
            val f = lines[i].split(',')
            if (f.size <= maxOf(iWest, iEast, iType)) continue
            if (!f[iType].trim().equals(FUEL_TYPE, ignoreCase = true)) continue
            val date = f[iDate].trim()
            if (date.isEmpty() || date <= bestDate) continue   // ISO dates sort lexically
            val w = f[iWest].trim().toDoubleOrNull()
            val e = f[iEast].trim().toDoubleOrNull()
            val cents = when {
                w != null && w > 0 && e != null && e > 0 -> (w + e) / 2.0
                w != null && w > 0 -> w
                e != null && e > 0 -> e
                else -> continue
            }
            val perL = cents / 100.0
            if (perL in MIN_PER_L..MAX_PER_L) { bestDate = date; bestPerL = perL }
        }
        return bestPerL
    }

    /** Fetch + parse the current Toronto weekly regular price ($/L). Null on any failure. */
    suspend fun fetchTorontoRegular(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.let(::parseLatestTorontoRegular)
            }
        }.getOrNull()
    }

    /** Fetch now (ignoring the daily throttle) and store it. Returns the new price, or null on failure.
     *  Used when the user flips auto-update on or taps "update now". */
    suspend fun refreshNow(context: Context): Double? {
        val price = fetchTorontoRegular() ?: return null
        VehiclePrefs.setPrice(context, price)
        VehiclePrefs.recordPriceUpdate(context, today(), SOURCE_LABEL)
        return price
    }

    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * Once-per-day, if auto-update is on, refresh the stored price from the Toronto weekly average.
     * Safe to call on every app open. Returns the new price if it changed, else null.
     */
    suspend fun maybeUpdate(context: Context): Double? {
        if (!VehiclePrefs.autoUpdatePrice(context)) return null
        val today = today()
        if (VehiclePrefs.lastPriceUpdateDay(context) == today) return null
        val price = fetchTorontoRegular() ?: return null
        val current = VehiclePrefs.load(context).pricePerL
        VehiclePrefs.recordPriceUpdate(context, today, SOURCE_LABEL)
        if (kotlin.math.abs(price - current) < 0.001) return null
        VehiclePrefs.setPrice(context, price)
        return price
    }
}
