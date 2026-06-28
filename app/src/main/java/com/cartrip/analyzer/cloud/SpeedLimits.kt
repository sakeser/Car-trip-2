package com.cartrip.analyzer.cloud

import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.CachedTileEntity
import com.cartrip.analyzer.data.CachedWayEntity
import com.cartrip.analyzer.data.TripDao
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Posted speed limits from OpenStreetMap via the free Overpass API.
 *
 * For a trip we query OSM drivable ways near the route, match each track point to the nearest
 * way, and compute how much covered driving was over the limit. We use posted maxspeed when OSM
 * has one, and otherwise assume a limit from the road class so local streets without a posted tag
 * can still be scored. Everything fails soft.
 */
object SpeedLimits {

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter"
    )
    private const val MATCH_RADIUS_M = 35.0
    private const val OVER_TOL_KMH = 3.0
    private const val MOVING_KMH = 8.0
    // Speeding-severity tuning (Rev BF). SEV_TOL_KMH: small overages below this are forgiven (0 penalty).
    // LIMIT_DROP_GRACE_MS: after the matched limit drops (e.g. exiting a highway), keep crediting the
    // higher recent limit for this long so normal deceleration to the new limit isn't counted as speeding.
    private const val SEV_TOL_KMH = 5.0
    private const val LIMIT_DROP_GRACE_MS = 6_000L
    private const val BBOX_PAD_DEG = 0.0006
    private const val ROUTE_BOX_PAD_DEG = 0.0010
    private const val ROUTE_BOX_MAX_SPAN_DEG = 0.025
    private const val MAX_ROUTE_SPAN_DEG = 1.2
    private const val MAX_ROUTE_BOXES = 32
    private const val MAX_BOXES_PER_QUERY = 32
    // Cached OSM ways/tiles are reused for 30 days before re-validation (limits change rarely).
    private const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    private const val WAY_FILTER =
        "^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"

    private val FORM = "application/x-www-form-urlencoded".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()

    data class Result(
        val speedingPct: Double,
        val maxOverKmh: Double,
        val coverage: Double,
        val severity: Double = 0.0
    )

    data class Annotated(val result: Result, val points: List<AnalysisPointEntity>)

    @Volatile
    private var lastDiag: String = ""
    fun lastDiagnostic(): String = lastDiag

    // Last cache outcome, e.g. "tiles 18/20 cached · 2 fetched · 143 ways" — for debug visibility.
    @Volatile
    private var lastCacheStat: String = ""
    fun lastCacheStat(): String = lastCacheStat

    private data class BBox(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double
    ) {
        val latSpan: Double get() = maxLat - minLat
        val lonSpan: Double get() = maxLon - minLon

        fun merged(other: BBox): BBox =
            BBox(
                minOf(minLat, other.minLat),
                minOf(minLon, other.minLon),
                maxOf(maxLat, other.maxLat),
                maxOf(maxLon, other.maxLon)
            )

        fun overpass(): String =
            "%.6f,%.6f,%.6f,%.6f".format(Locale.US, minLat, minLon, maxLat, maxLon)
    }

    private class Way(
        val id: Long,
        val limitKmh: Double,
        val geom: List<DoubleArray>,
        val bounds: BBox
    )

    /**
     * Look up limits for a trip's stored analysis points, write per-point limits back, and update
     * the trip's aggregate speeding summary. Returns null if nothing useful was matched.
     */
    suspend fun refreshForTrip(dao: TripDao, tripId: Long): Result? {
        val points = dao.getAnalysisPoints(tripId)
        val annotated = annotate(points, dao) ?: return null
        // Bound cache growth: drop ways well past their useful life (tiles will re-fetch as needed).
        runCatching { dao.purgeCachedWaysBefore(System.currentTimeMillis() - 2 * CACHE_TTL_MS) }
        dao.getTrip(tripId)?.let { trip ->
            dao.updateTripSpeedLimits(
                trip.copy(
                    speedingPct = annotated.result.speedingPct,
                    maxOverLimitKmh = annotated.result.maxOverKmh,
                    limitCoverage = annotated.result.coverage,
                    speedingSeverity = annotated.result.severity
                ),
                annotated.points.map { it.copy(id = 0) }
            )
        }
        return annotated.result
    }

    suspend fun annotate(points: List<AnalysisPointEntity>, dao: TripDao? = null): Annotated? {
        if (points.size < 5) return null
        val ways = runCatching { fetchWays(points, dao) }.getOrNull() ?: return null
        if (ways.isEmpty()) return null

        val limits = smoothIsolatedLimitMismatches(points.map { nearestLimit(it.lat, it.lon, ways) })
        val out = points.mapIndexed { i, p ->
            p.copy(speedLimitKmh = limits[i] ?: 0.0)
        }
        if (points.none { it.speedKmh >= MOVING_KMH }) return null
        val result = speedingSummary(points.map { it.t }, points.map { it.speedKmh }, limits)
        return Annotated(result, out)
    }

    /**
     * Pure speeding summary (Rev BF) — testable without the network. For each moving point with a matched
     * limit it computes how far over an **effective** limit it is, then aggregates:
     *  - [Result.speedingPct] / [Result.maxOverKmh]: % of covered time over (by [OVER_TOL_KMH]) and the
     *    worst overage — kept for display.
     *  - [Result.severity]: the magnitude-weighted exposure that drives the Safety penalty — the mean over
     *    covered time of `max(0, over - sevTol)^2` (super-linear in how far over, small overages forgiven).
     *
     * **Effective limit = the max matched limit within the trailing [graceMs] window.** This is lenient
     * exactly when a transition would otherwise lie: right after a limit *drop* (highway exit) it keeps
     * crediting the higher recent limit while you decelerate; right after a *rise* it adopts the new higher
     * limit immediately (the window's max). So neither transition fabricates speeding.
     */
    internal fun speedingSummary(
        times: List<Long>,
        speeds: List<Double>,
        limits: List<Double?>,
        movingKmh: Double = MOVING_KMH,
        overTol: Double = OVER_TOL_KMH,
        sevTol: Double = SEV_TOL_KMH,
        graceMs: Long = LIMIT_DROP_GRACE_MS,
    ): Result {
        val n = times.size
        var covered = 0; var moving = 0; var speedingPts = 0; var maxOver = 0.0; var sevSum = 0.0
        var lo = 0
        for (i in 0 until n) {
            if (speeds[i] < movingKmh) continue
            moving++
            val lim = limits[i] ?: continue
            while (lo < i && times[lo] < times[i] - graceMs) lo++
            var eff = lim
            for (j in lo..i) { val lj = limits[j]; if (lj != null && lj > eff) eff = lj }
            covered++
            val over = speeds[i] - eff
            if (over > overTol) { speedingPts++; if (over > maxOver) maxOver = over }
            val exc = over - sevTol
            if (exc > 0) sevSum += exc * exc
        }
        val coverage = if (moving > 0) covered.toDouble() / moving else 0.0
        val speedingPct = if (covered > 0) speedingPts.toDouble() / covered else 0.0
        val severity = if (covered > 0) sevSum / covered else 0.0
        return Result(speedingPct, maxOver, coverage, severity)
    }

    private fun smoothIsolatedLimitMismatches(raw: List<Double?>): List<Double?> {
        if (raw.size < 3) return raw
        val out = raw.toMutableList()
        for (i in 1 until raw.lastIndex) {
            val prev = raw[i - 1] ?: continue
            val current = raw[i] ?: continue
            val next = raw[i + 1] ?: continue
            if (sameLimit(prev, next) && !sameLimit(current, prev)) {
                // A one-point island is usually a nearest-road snap to a parallel ramp or side road.
                out[i] = prev
            }
        }
        return out
    }

    private fun sameLimit(a: Double, b: Double): Boolean = abs(a - b) < 0.5

    private suspend fun fetchWays(points: List<AnalysisPointEntity>, dao: TripDao?): List<Way> {
        val routeBounds = boundsFor(points, BBOX_PAD_DEG)
        if (routeBounds.latSpan > MAX_ROUTE_SPAN_DEG || routeBounds.lonSpan > MAX_ROUTE_SPAN_DEG) {
            lastDiag = "Route is too large for one speed-limit lookup."
            return emptyList()
        }

        // No cache available (e.g. a direct call) — query the whole route as before.
        if (dao == null) {
            lastCacheStat = ""
            return queryWays(routeBoxes(points)).values.toList()
        }

        // Cache-first: only Overpass-query the tiles we haven't fetched recently, then serve the
        // whole route from the cache. A repeat drive on known roads makes zero network calls.
        val now = System.currentTimeMillis()
        val routeTiles = Tiles.routeTiles(points.map { it.lat to it.lon })
        val fresh = dao.freshTileKeys(now - CACHE_TTL_MS).toHashSet()
        val missing = routeTiles.filterNot { it in fresh }

        if (missing.isNotEmpty()) {
            val boxes = missing.map { key ->
                val b = Tiles.bounds(key)
                BBox(b[0] - ROUTE_BOX_PAD_DEG, b[1] - ROUTE_BOX_PAD_DEG, b[2] + ROUTE_BOX_PAD_DEG, b[3] + ROUTE_BOX_PAD_DEG)
            }
            val fetched = queryWays(boxes)
            if (fetched.isNotEmpty()) {
                dao.upsertCachedWays(fetched.values.map { it.toEntity(now) })
            }
            // Mark every queried tile as covered (even if it had no drivable ways), so empty areas
            // aren't re-queried every trip.
            dao.upsertCachedTiles(missing.map { CachedTileEntity(it, now, "overpass") })
        }

        val cached = dao.cachedWaysInBounds(
            routeBounds.minLat, routeBounds.minLon, routeBounds.maxLat, routeBounds.maxLon
        )
        lastCacheStat = "tiles ${routeTiles.size - missing.size}/${routeTiles.size} cached · " +
            "${missing.size} fetched · ${cached.size} ways"
        return cached.map { it.toWay() }
    }

    /** Run the Overpass query for a set of boxes (chunked), deduping ways by id. */
    private fun queryWays(boxes: List<BBox>): LinkedHashMap<Long, Way> {
        val diag = StringBuilder()
        val out = LinkedHashMap<Long, Way>()
        for (batch in boxes.chunked(MAX_BOXES_PER_QUERY)) {
            val body = "data=${URLEncoder.encode(overpassQuery(batch), "UTF-8")}".toRequestBody(FORM)
            fetchWayBatch(body, diag).forEach { out[it.id] = it }
        }
        lastDiag = if (out.isEmpty()) diag.toString().trim() else ""
        return out
    }

    private fun Way.toEntity(now: Long): CachedWayEntity = CachedWayEntity(
        wayId = id,
        limitKmh = limitKmh,
        source = "osm",
        minLat = bounds.minLat,
        minLon = bounds.minLon,
        maxLat = bounds.maxLat,
        maxLon = bounds.maxLon,
        geometry = geom.joinToString(";") { "%.6f,%.6f".format(Locale.US, it[0], it[1]) },
        fetchedAt = now
    )

    private fun CachedWayEntity.toWay(): Way {
        val nodes = geometry.split(";").mapNotNull { seg ->
            val c = seg.split(",")
            val la = c.getOrNull(0)?.toDoubleOrNull()
            val lo = c.getOrNull(1)?.toDoubleOrNull()
            if (la != null && lo != null) doubleArrayOf(la, lo) else null
        }
        return Way(wayId, limitKmh, nodes, BBox(minLat, minLon, maxLat, maxLon))
    }

    private fun fetchWayBatch(body: RequestBody, diag: StringBuilder): List<Way> {
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
                        if (ways.isNotEmpty()) return ways
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
        return emptyList()
    }

    private fun overpassQuery(boxes: List<BBox>): String {
        val clauses = boxes.joinToString(separator = "") { box ->
            "way[\"highway\"~\"$WAY_FILTER\"](${box.overpass()});"
        }
        return "[out:json][timeout:20];($clauses);out geom;"
    }

    private fun boundsFor(points: List<AnalysisPointEntity>, pad: Double): BBox {
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        for (p in points) {
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon
            if (p.lon > maxLon) maxLon = p.lon
        }
        return BBox(minLat - pad, minLon - pad, maxLat + pad, maxLon + pad)
    }

    private fun routeBoxes(points: List<AnalysisPointEntity>): List<BBox> {
        val full = boundsFor(points, BBOX_PAD_DEG)
        if (full.latSpan <= ROUTE_BOX_MAX_SPAN_DEG && full.lonSpan <= ROUTE_BOX_MAX_SPAN_DEG) {
            return listOf(full)
        }

        val boxes = ArrayList<BBox>()
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        var hasChunk = false

        fun closeChunk() {
            if (!hasChunk) return
            boxes += BBox(
                minLat - ROUTE_BOX_PAD_DEG,
                minLon - ROUTE_BOX_PAD_DEG,
                maxLat + ROUTE_BOX_PAD_DEG,
                maxLon + ROUTE_BOX_PAD_DEG
            )
            minLat = 90.0
            maxLat = -90.0
            minLon = 180.0
            maxLon = -180.0
            hasChunk = false
        }

        for (p in points) {
            if (!hasChunk) {
                minLat = p.lat
                maxLat = p.lat
                minLon = p.lon
                maxLon = p.lon
                hasChunk = true
                continue
            }

            val nextMinLat = minOf(minLat, p.lat)
            val nextMaxLat = maxOf(maxLat, p.lat)
            val nextMinLon = minOf(minLon, p.lon)
            val nextMaxLon = maxOf(maxLon, p.lon)
            if (nextMaxLat - nextMinLat > ROUTE_BOX_MAX_SPAN_DEG ||
                nextMaxLon - nextMinLon > ROUTE_BOX_MAX_SPAN_DEG
            ) {
                closeChunk()
                minLat = p.lat
                maxLat = p.lat
                minLon = p.lon
                maxLon = p.lon
                hasChunk = true
            } else {
                minLat = nextMinLat
                maxLat = nextMaxLat
                minLon = nextMinLon
                maxLon = nextMaxLon
            }
        }
        closeChunk()
        return coalesceBoxes(boxes, MAX_ROUTE_BOXES)
    }

    private fun coalesceBoxes(boxes: List<BBox>, maxBoxes: Int): List<BBox> {
        if (boxes.size <= maxBoxes) return boxes
        val groupSize = ceil(boxes.size.toDouble() / maxBoxes).toInt().coerceAtLeast(1)
        return boxes.chunked(groupSize).map { group ->
            group.reduce { acc, box -> acc.merged(box) }
        }
    }

    private fun parseWays(jsonBody: String): List<Way> {
        val elements = runCatching { JSONObject(jsonBody).optJSONArray("elements") }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<Way>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.optString("type") != "way") continue
            val id = el.optLong("id", -1L)
            if (id <= 0L) continue
            val tags = el.optJSONObject("tags")
            val limit = parseMaxspeedKmh(tags?.optString("maxspeed"))
                ?: assumedLimitFor(tags?.optString("highway").orEmpty())
                ?: continue
            val geomArr = el.optJSONArray("geometry") ?: continue
            val nodes = ArrayList<DoubleArray>(geomArr.length())
            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0
            for (j in 0 until geomArr.length()) {
                val g = geomArr.getJSONObject(j)
                val lat = g.getDouble("lat")
                val lon = g.getDouble("lon")
                nodes += doubleArrayOf(lat, lon)
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }
            if (nodes.size >= 2) out += Way(id, limit, nodes, BBox(minLat, minLon, maxLat, maxLon))
        }
        return out
    }

    private fun nearestLimit(lat: Double, lon: Double, ways: List<Way>): Double? {
        var best = MATCH_RADIUS_M
        var bestLimit: Double? = null
        val cosLat = abs(cos(Math.toRadians(lat))).coerceAtLeast(0.01)
        val lonScale = 111_320.0 * cosLat
        val latScale = 111_320.0
        val latPad = MATCH_RADIUS_M / latScale
        val lonPad = MATCH_RADIUS_M / lonScale
        val px = lon * lonScale
        val py = lat * latScale
        for (w in ways) {
            if (lat < w.bounds.minLat - latPad || lat > w.bounds.maxLat + latPad ||
                lon < w.bounds.minLon - lonPad || lon > w.bounds.maxLon + lonPad
            ) {
                continue
            }
            for (k in 0 until w.geom.size - 1) {
                val a = w.geom[k]
                val b = w.geom[k + 1]
                val ax = a[1] * lonScale
                val ay = a[0] * latScale
                val bx = b[1] * lonScale
                val by = b[0] * latScale
                val d = pointSegmentDist(px, py, ax, ay, bx, by)
                if (d < best) {
                    best = d
                    bestLimit = w.limitKmh
                }
            }
        }
        return bestLimit
    }

    private fun pointSegmentDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun assumedLimitFor(highway: String): Double? = when (highway) {
        "residential", "living_street", "unclassified", "service", "road" -> 40.0
        "tertiary", "tertiary_link", "secondary", "secondary_link", "primary", "primary_link" -> 50.0
        "trunk", "trunk_link" -> 80.0
        "motorway", "motorway_link" -> 100.0
        else -> null
    }

    private fun parseMaxspeedKmh(raw: String?): Double? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        return when {
            s.endsWith("mph") -> s.removeSuffix("mph").trim().toDoubleOrNull()?.let { it * 1.60934 }
            s.endsWith("knots") -> null
            else -> s.toDoubleOrNull()
        }?.takeIf { it in 5.0..200.0 }
    }
}
