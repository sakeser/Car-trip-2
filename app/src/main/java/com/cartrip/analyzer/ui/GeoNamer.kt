package com.cartrip.analyzer.ui

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reverse-geocoded, human-readable trip names such as "North York -> Scarborough".
 *
 * This sits *over* [TripLabeler]: it tries the Android [Geocoder] for a trip's start/end coordinates
 * and composes a place-to-place label. On **any** failure (no geocoder backend, offline,
 * rate-limited, null/empty result, or a thrown exception) it returns null and the caller falls back
 * to the static [TripLabeler.label].
 *
 * Results are cached in SharedPreferences keyed by *quantized* coordinates (~110 m cells), so the
 * Past-trips list does not re-geocode every trip on every refresh -- the same driveway/parking spot
 * resolves once and is reused. A per-refresh [Budget] caps live geocoder calls so a cold cache can't
 * fire dozens of network lookups at once.
 *
 * The pure label-composition logic ([describe], [compose], [cellKey]) is deliberately Android-free
 * so it is unit-testable; only [reverseGeocode]/[endpointName]/[nameTrip] touch the framework.
 *
 * Note: the visible separator is a real arrow glyph, but it is built from its code point
 * ([ARROW]) so this source file stays pure ASCII. A literal arrow gets mojibaked when the Kotlin
 * compiler reads a BOM-less file as the platform charset (Cp1252 on Windows), so we avoid one.
 */
object GeoNamer {

    /** U+2192 RIGHTWARDS ARROW, built in pure-ASCII source to dodge source-encoding mojibake. */
    val ARROW: String = 0x2192.toChar().toString()

    // ---- Pure, Android-free label composition (unit-tested) -------------------------------------

    /**
     * The handful of fields we distill an Android [Address] down to. Keeping composition operating
     * on this plain type (rather than [Address]) is what makes the logic testable without a device.
     */
    data class Spot(
        /** subLocality -- neighbourhood / former municipality, e.g. "North York". */
        val neighbourhood: String? = null,
        /** locality -- city, e.g. "Toronto" or "Vaughan". */
        val city: String? = null,
        /** thoroughfare -- street, e.g. "Yonge Street". Last-resort name. */
        val road: String? = null,
    )

    /**
     * Best single human name for one endpoint, most specific first (neighbourhood -> city -> road),
     * or null when nothing usable came back.
     */
    fun describe(spot: Spot?): String? {
        if (spot == null) return null
        return listOf(spot.neighbourhood, spot.city, spot.road)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }

    /**
     * Compose a "start -> end" label from two endpoint names. Returns null when neither endpoint is
     * nameable, so the caller can fall back to [TripLabeler].
     */
    fun compose(from: String?, to: String?): String? {
        val a = from?.trim()?.ifBlank { null }
        val b = to?.trim()?.ifBlank { null }
        return when {
            a != null && b != null && !a.equals(b, ignoreCase = true) -> "$a $ARROW $b"
            a != null && b != null -> "$a loop"   // round trip within one named area
            a != null -> "$a drive"
            b != null -> "Drive to $b"
            else -> null
        }
    }

    /**
     * Quantize a coordinate to a ~110 m cache cell (3 decimal places of latitude ~= 111 m) so nearby
     * trip endpoints share a single geocode result. Trades a little precision for a much higher
     * cache hit-rate, which is the whole point of the cache.
     */
    fun cellKey(lat: Double, lon: Double): String {
        val rl = (lat * 1000.0).roundToInt()
        val ro = (lon * 1000.0).roundToInt()
        return "$rl,$ro"
    }

    // ---- Android-backed reverse geocoding (fail-soft) ------------------------------------------

    /** Caps live geocoder calls within a single trip-list refresh; cached cells are free. */
    class Budget(private var remaining: Int) {
        fun tryConsume(): Boolean = if (remaining > 0) { remaining--; true } else false
    }

    private const val CACHE = "cartrip_geocache"
    /** Sentinel cached when the geocoder *responded* but had no usable name -- avoids retrying it. */
    private const val NONE = " "

    private sealed interface GeoResult {
        data class Ok(val name: String) : GeoResult
        /** Service answered but nothing usable here -> cache negative so we don't ask again. */
        object Empty : GeoResult
        /** Transient (offline / rate-limited / threw) -> do not cache; a later refresh may succeed. */
        object Failed : GeoResult
    }

    private fun describeAddress(a: Address): String? = describe(
        Spot(neighbourhood = a.subLocality, city = a.locality, road = a.thoroughfare)
    )

    @Suppress("DEPRECATION") // The blocking getFromLocation is fine: we're already off the main thread.
    private fun reverseGeocode(geocoder: Geocoder, lat: Double, lon: Double): GeoResult =
        try {
            val name = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let(::describeAddress)
            if (name != null) GeoResult.Ok(name) else GeoResult.Empty
        } catch (t: Throwable) {
            GeoResult.Failed
        }

    /** Resolve one endpoint to a place name via cache -> budgeted geocode. Never throws. */
    private fun endpointName(
        geocoder: Geocoder,
        prefs: android.content.SharedPreferences,
        lat: Double,
        lon: Double,
        budget: Budget,
    ): String? {
        val key = cellKey(lat, lon)
        prefs.getString(key, null)?.let { return it.takeIf { c -> c != NONE } }
        if (!budget.tryConsume()) return null
        return when (val r = reverseGeocode(geocoder, lat, lon)) {
            is GeoResult.Ok -> { prefs.edit().putString(key, r.name).apply(); r.name }
            GeoResult.Empty -> { prefs.edit().putString(key, NONE).apply(); null }
            GeoResult.Failed -> null
        }
    }

    /**
     * Reverse-geocode a trip's start/end into a "start -> end" label, or null to fall back to
     * [TripLabeler]. [budget] caps the live geocoder calls spent on this refresh. Never throws and
     * never touches the main thread itself -- call from a background dispatcher.
     */
    fun nameTrip(
        context: Context,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        budget: Budget,
    ): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val prefs = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val from = endpointName(geocoder, prefs, startLat, startLon, budget)
        val to = endpointName(geocoder, prefs, endLat, endLon, budget)
        return compose(from, to)
    }
}
