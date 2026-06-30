package com.cartrip.analyzer.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Scaffolding for richer destination naming via the Places API (New) "Nearby Search" - e.g. resolving a
 * trip endpoint to "IKEA" or "Costco" instead of just the neighbourhood the on-device Geocoder returns.
 *
 * Paid, metered API, OFF by default ([PlacesPrefs]). [placeNameCached] is a no-op (returns null) unless
 * the owner has enabled the flag AND added a billing-enabled key with "Places API (New)" turned on. Every
 * lookup is cached by ~110 m cell so home/work/regular stores cost at most one call, then resolve free.
 * Fail-soft everywhere - naming always falls back to the Geocoder. Not yet wired into the live naming path
 * (a deliberate, documented follow-up; see HANDOFF section 14.1 CQ); the pure parser + cache key are unit-tested.
 */
object Places {

    private const val ENDPOINT = "https://places.googleapis.com/v1/places:searchNearby"
    private const val FIELD_MASK = "places.displayName"
    private const val RADIUS_M = 60.0          // endpoint-only: the POI you parked at, not the whole block
    private const val CELL_DEG = 0.001         // ~110 m cache cell (matches GeoNamer's grid)
    private const val PREFS = "cartrip_places_cache"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    /** Quantized ~110 m cache cell key for (lat, lon). Pure. */
    fun cellKey(lat: Double, lon: Double): String {
        fun q(v: Double) = (v / CELL_DEG).roundToInt()
        return "${q(lat)}:${q(lon)}"
    }

    /**
     * Pure: the top POI name from a Places API (New) `searchNearby` response, or null if none / unparseable.
     * Shape: `{ "places": [ { "displayName": { "text": "IKEA" } } ] }`.
     */
    fun topPlaceName(json: String): String? = runCatching {
        val places = JSONObject(json).optJSONArray("places") ?: return null
        if (places.length() == 0) return null
        val name = places.getJSONObject(0).optJSONObject("displayName")?.optString("text")?.trim()
        name?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Live Nearby Search (rank by distance, 1 result). Null on any failure / non-200. Suspends on IO. */
    suspend fun nearbyName(
        apiKey: String, androidPackage: String?, androidCertSha1: String?, lat: Double, lon: Double
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("maxResultCount", 1)
                put("rankPreference", "DISTANCE")
                put("locationRestriction", JSONObject().apply {
                    put("circle", JSONObject().apply {
                        put("center", JSONObject().apply { put("latitude", lat); put("longitude", lon) })
                        put("radius", RADIUS_M)
                    })
                })
            }.toString().toRequestBody(JSON)
            val builder = Request.Builder()
                .url(ENDPOINT)
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", FIELD_MASK)
            if (androidPackage != null) builder.addHeader("X-Android-Package", androidPackage)
            if (androidCertSha1 != null) builder.addHeader("X-Android-Cert", androidCertSha1)
            http.newCall(builder.post(body).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.let(::topPlaceName)
            }
        }.getOrNull()
    }

    /**
     * Cached POI name for an endpoint, or null. No-op (null) when the [PlacesPrefs] flag is off - so with
     * the default config this never touches the network or costs anything. Caches by ~110 m cell (including
     * negative/"no POI" results, stored as an empty string) so repeat endpoints are free.
     */
    suspend fun placeNameCached(context: Context, lat: Double, lon: Double): String? {
        if (!PlacesPrefs.enabled(context)) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = cellKey(lat, lon)
        if (prefs.contains(key)) return prefs.getString(key, "")?.takeIf { it.isNotEmpty() }
        val apiKey = RoutesConfig.apiKey(context) ?: return null
        val name = nearbyName(
            apiKey, RoutesConfig.androidPackage(context), RoutesConfig.signingSha1(context), lat, lon
        )
        prefs.edit().putString(key, name ?: "").apply()   // cache the miss too, to bound calls
        return name
    }
}
