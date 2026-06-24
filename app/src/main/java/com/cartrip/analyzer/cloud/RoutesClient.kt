package com.cartrip.analyzer.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the Google Routes API `computeRoutes` endpoint.
 *
 * Returns the road-following polyline plus two durations:
 *  - [RouteResult.trafficS]: traffic-aware estimate for [departureRfc3339]
 *  - [RouteResult.freeFlowS]: ideal, no-traffic duration (`staticDuration`)
 *
 * The same API key used for the Maps SDK works here once "Routes API" is added to the
 * key's API restrictions. For Android-app-restricted keys the package + signing SHA-1
 * are sent as X-Android-* headers.
 */
object RoutesClient {

    private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
    private const val FIELD_MASK = "routes.duration,routes.staticDuration,routes.polyline.encodedPolyline"
    private const val MAX_ATTEMPTS = 3
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // long routes return a large HIGH_QUALITY polyline
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Last failure reason, for surfacing to the UI / debug instead of a silent null. */
    @Volatile
    private var lastDiag: String = ""
    fun lastDiagnostic(): String = lastDiag

    data class RouteResult(
        val trafficS: Double,
        val freeFlowS: Double,
        val polyline: List<DoubleArray> // each entry = [lat, lon]
    )

    /** Deterministic failures (bad key, no path, malformed response) — not worth retrying. */
    private class FatalRouteException(message: String) : IOException(message)

    /**
     * @param departureRfc3339 RFC-3339 timestamp; must not be in the past. Use "now" for a live
     *   snapshot, or the next matching weekday+time for a "typical" estimate. Null => live.
     */
    fun computeRoute(
        apiKey: String,
        androidPackage: String?,
        androidCertSha1: String?,
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        departureRfc3339: String?
    ): RouteResult {
        val payload = JSONObject()
            .put("origin", waypoint(originLat, originLon))
            .put("destination", waypoint(destLat, destLon))
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_AWARE")
            .put("polylineQuality", "HIGH_QUALITY")
            .apply { if (departureRfc3339 != null) put("departureTime", departureRfc3339) }
            .toString()

        // Transient failures (network blips, 429/5xx, slow long-route responses) are common on the
        // road; retry with backoff so a long drive doesn't silently end up with no traffic comparison.
        var lastError: IOException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return attemptRoute(payload, apiKey, androidPackage, androidCertSha1).also { lastDiag = "" }
            } catch (e: FatalRouteException) {
                lastDiag = e.message ?: "Routes API error"
                throw e
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < MAX_ATTEMPTS) runCatching { Thread.sleep(attempt * 700L) }
        }
        lastDiag = lastError?.message ?: "Routes API unreachable"
        throw lastError ?: IOException(lastDiag)
    }

    private fun attemptRoute(
        payload: String,
        apiKey: String,
        androidPackage: String?,
        androidCertSha1: String?
    ): RouteResult {
        val builder = Request.Builder()
            .url(ENDPOINT)
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", FIELD_MASK)
        if (!androidPackage.isNullOrBlank() && !androidCertSha1.isNullOrBlank()) {
            builder.addHeader("X-Android-Package", androidPackage)
            builder.addHeader("X-Android-Cert", androidCertSha1)
        }
        val req = builder.post(payload.toRequestBody(JSON)).build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            // 429/5xx are transient — let the caller retry; other non-2xx are fatal (bad key, etc.).
            if (resp.code == 429 || resp.code in 500..599) throw IOException("Routes API ${resp.code}")
            if (!resp.isSuccessful) throw FatalRouteException("Routes API ${resp.code}: ${text.take(300)}")
            val routes = JSONObject(text).optJSONArray("routes")
                ?: throw FatalRouteException("Routes API: no routes in response")
            if (routes.length() == 0) throw FatalRouteException("Routes API: empty route (no path found)")
            val route = routes.getJSONObject(0)
            val traffic = parseSeconds(route.optString("duration"))
            val free = parseSeconds(route.optString("staticDuration")).takeIf { it > 0 } ?: traffic
            val encoded = route.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            return RouteResult(traffic, free, decodePolyline(encoded))
        }
    }

    private fun waypoint(lat: Double, lon: Double): JSONObject =
        JSONObject().put(
            "location",
            JSONObject().put("latLng", JSONObject().put("latitude", lat).put("longitude", lon))
        )

    /** Durations arrive as protobuf-style strings, e.g. "1234s" or "1234.5s". */
    private fun parseSeconds(value: String): Double =
        value.removeSuffix("s").toDoubleOrNull() ?: 0.0

    /** Standard Google encoded-polyline algorithm decode. */
    fun decodePolyline(encoded: String): List<DoubleArray> {
        if (encoded.isEmpty()) return emptyList()
        val out = ArrayList<DoubleArray>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out += doubleArrayOf(lat / 1e5, lon / 1e5)
        }
        return out
    }
}
