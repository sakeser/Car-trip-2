package com.cartrip.analyzer.cloud

import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripDao
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Posted speed limits from OpenStreetMap via the free Overpass API.
 *
 * For a trip we query OSM drivable `way`s near the route, match each track point to the nearest
 * way, and compute how much of the (covered) driving was over the limit. We use the posted
 * `maxspeed` where OSM has one, and otherwise assume a limit from the road class (residential 40,
 * arterial 50, freeway higher) so local streets that lack a posted value can still be scored.
 * Everything fails soft: on any error the result is null and the speeding factor simply doesn't apply.
 */
object SpeedLimits {

    // Several public Overpass mirrors — the free endpoints rate-limit aggressively, so we fall
    // through to the next one (and retry the first) rather than silently losing the speed limits.
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter"
    )
    private const val MATCH_RADIUS_M = 35.0     // a point this close to a way inherits its limit
    private const val OVER_TOL_KMH = 3.0        // ignore tiny GPS-noise overages
    private const val MOVING_KMH = 8.0          // only judge speeding while actually moving
    private const val BBOX_PAD_DEG = 0.0006     // ~65 m padding around the route bounding box
    private const val MAX_BBOX_DEG = 0.6        // skip absurdly large bboxes (~65 km) to stay fast

    private val FORM = "application/x-www-form-urlencoded".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS) // hard ceiling so a slow mirror can never hang the spinner
        .build()

    data class Result(
        val speedingPct: Double,    // fraction of covered moving time over the limit
        val maxOverKmh: Double,     // worst overage
        val coverage: Double        // fraction of moving points we found a limit for
    )

    /** The aggregate plus each input point annotated with its matched limit (0 = unknown). */
    data class Annotated(val result: Result, val points: List<AnalysisPointEntity>)

    // Last per-mirror outcome, surfaced to the UI so a failure is diagnosable instead of opaque.
    @Volatile
    private var lastDiag: String = ""
    fun lastDiagnostic(): String = lastDiag

    private class Way(val limitKmh: Double, val geom: List<DoubleArray>) // geom = [lat,lon] nodes

    /**
     * Look up limits for a trip's stored analysis points, write the per-point limits back, and
     * update the trip's speeding aggregate. Returns the aggregate, or null if nothing was matched.
     */
    suspend fun refreshForTrip(dao: TripDao, tripId: Long): Result? {
        val points = dao.getAnalysisPoints(tripId)
        val annotated = annotate(points) ?: return null
        dao.deleteAnalysisPoints(tripId)
        dao.insertAnalysisPoints(annotated.points.map { it.copy(id = 0) })
        dao.getTrip(tripId)?.let { trip ->
            dao.updateTrip(
                trip.copy(
                    speedingPct = annotated.result.speedingPct,
                    maxOverLimitKmh = annotated.result.maxOverKmh,
                    limitCoverage = annotated.result.coverage
                )
            )
        }
        return annotated.result
    }

    /**
     * Look up OSM limits along [points], returning the speeding aggregate and the same points with
     * `speedLimitKmh` filled in (so the route can be coloured where the driver was over). Fails soft.
     */
    fun annotate(points: List<AnalysisPointEntity>): Annotated? {
        if (points.size < 5) return null
        val ways = runCatching { fetchWays(points) }.getOrNull() ?: return null
        if (ways.isEmpty()) return null

        var covered = 0
        var movingPts = 0
        var speedingPts = 0
        var maxOver = 0.0
        val out = ArrayList<AnalysisPointEntity>(points.size)
        for (p in points) {
            val limit = nearestLimit(p.lat, p.lon, ways)
            out.add(if (limit != null) p.copy(speedLimitKmh = limit) else p.copy(speedLimitKmh = 0.0))
            if (p.speedKmh < MOVING_KMH) continue
            movingPts++
            if (limit == null) continue
            covered++
            val over = p.speedKmh - limit
            if (over > OVER_TOL_KMH) {
                speedingPts++
                if (over > maxOver) maxOver = over
            }
        }
        if (movingPts == 0) return null
        val coverage = covered.toDouble() / movingPts
        val speedingPct = if (covered > 0) speedingPts.toDouble() / covered else 0.0
        return Annotated(Result(speedingPct, maxOver, coverage), out)
    }

    private fun fetchWays(points: List<AnalysisPointEntity>): List<Way> {
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (p in points) {
            if (p.lat < minLat) minLat = p.lat; if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon; if (p.lon > maxLon) maxLon = p.lon
        }
        // A single bounding-box query is far lighter for Overpass than dozens of "around" clauses,
        // so it returns in a second or two instead of timing out. We match points to ways locally.
        if (maxLat - minLat > MAX_BBOX_DEG || maxLon - minLon > MAX_BBOX_DEG) return emptyList()
        val loc = java.util.Locale.US
        val bbox = "%.6f,%.6f,%.6f,%.6f".format(
            loc, minLat - BBOX_PAD_DEG, minLon - BBOX_PAD_DEG, maxLat + BBOX_PAD_DEG, maxLon + BBOX_PAD_DEG
        )
        // Fetch all drivable roads (not just those tagged maxspeed) so we can assume a limit by road
        // class where OSM has no posted value — otherwise most local streets have no coverage at all.
        val query = "[out:json][timeout:20];" +
            "way[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"]($bbox);" +
            "out geom;"
        val body = "data=${URLEncoder.encode(query, "UTF-8")}".toRequestBody(FORM)

        val diag = StringBuilder()
        ENDPOINTS.forEachIndexed { i, endpoint ->
            val host = endpoint.substringAfter("//").substringBefore("/")
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "CarTripAnalyzer/1.0 (Android; OSM speed-limit lookup)")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ways = parseWays(resp.body?.string().orEmpty())
                        if (ways.isNotEmpty()) { lastDiag = ""; return ways }
                        diag.append("$host: 200 but no ways. ")
                    } else {
                        diag.append("$host: HTTP ${resp.code}. ")
                    }
                }
            } catch (e: Exception) {
                diag.append("$host: ${e.javaClass.simpleName}. ")
            }
            if (i < ENDPOINTS.lastIndex) runCatching { Thread.sleep(400) }
        }
        lastDiag = diag.toString().trim()
        return emptyList()
    }

    private fun parseWays(jsonBody: String): List<Way> {
        val elements = runCatching { JSONObject(jsonBody).optJSONArray("elements") }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<Way>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.optString("type") != "way") continue
            val tags = el.optJSONObject("tags")
            // Use the posted maxspeed when present; otherwise assume a limit from the road class.
            val limit = parseMaxspeedKmh(tags?.optString("maxspeed"))
                ?: assumedLimitFor(tags?.optString("highway").orEmpty())
                ?: continue
            val geomArr = el.optJSONArray("geometry") ?: continue
            val nodes = ArrayList<DoubleArray>(geomArr.length())
            for (j in 0 until geomArr.length()) {
                val g = geomArr.getJSONObject(j)
                nodes.add(doubleArrayOf(g.getDouble("lat"), g.getDouble("lon")))
            }
            if (nodes.size >= 2) out.add(Way(limit, nodes))
        }
        return out
    }

    /** Nearest way's limit (km/h) if a way passes within [MATCH_RADIUS_M] of the point. */
    private fun nearestLimit(lat: Double, lon: Double, ways: List<Way>): Double? {
        var best = MATCH_RADIUS_M
        var bestLimit: Double? = null
        val lonScale = 111_320.0 * cos(Math.toRadians(lat))
        val latScale = 111_320.0
        val px = lon * lonScale
        val py = lat * latScale
        for (w in ways) {
            for (k in 0 until w.geom.size - 1) {
                val a = w.geom[k]; val b = w.geom[k + 1]
                val ax = a[1] * lonScale; val ay = a[0] * latScale
                val bx = b[1] * lonScale; val by = b[0] * latScale
                val d = pointSegmentDist(px, py, ax, ay, bx, by)
                if (d < best) { best = d; bestLimit = w.limitKmh }
            }
        }
        return bestLimit
    }

    private fun pointSegmentDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * Assumed limit (km/h) for a road class when OSM has no posted maxspeed. Residential streets →
     * 40, ordinary arterials → 50, freeways → higher so a highway run isn't flagged as speeding.
     */
    private fun assumedLimitFor(highway: String): Double? = when (highway) {
        "residential", "living_street", "unclassified", "service", "road" -> 40.0
        "tertiary", "tertiary_link", "secondary", "secondary_link", "primary", "primary_link" -> 50.0
        "trunk", "trunk_link" -> 80.0
        "motorway", "motorway_link" -> 100.0
        else -> null
    }

    /** Parse an OSM maxspeed tag to km/h. Handles "50", "30 mph"; ignores "none"/"signals"/etc. */
    private fun parseMaxspeedKmh(raw: String?): Double? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        return when {
            s.endsWith("mph") -> s.removeSuffix("mph").trim().toDoubleOrNull()?.let { it * 1.60934 }
            s.endsWith("knots") -> null
            else -> s.toDoubleOrNull() // bare number is km/h by OSM convention
        }?.takeIf { it in 5.0..200.0 }
    }
}
