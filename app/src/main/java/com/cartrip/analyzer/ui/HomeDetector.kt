package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.GeoUtils
import kotlin.math.roundToInt

/**
 * Learns the driver's **home** from where their trips actually begin and end, so trip names can say
 * "Home -> Scarborough" instead of a coarse neighbourhood ("North York loop"). Most trips start or end at
 * home, so across the whole history the home spot is by far the most frequent endpoint.
 *
 * Pure and Android-free (frequency clustering over (lat, lon) endpoints) so it is unit-testable; the
 * caller collects endpoints and persists/uses the result. Frequency-learned rather than hardcoded
 * (roadmap O6) -- it works for any driver, not just the original GTA owner.
 *
 * [detectWork] (Rev BH) finds the *second* regular place (a workplace / school / gym) as the most frequent
 * endpoint that isn't home, so names can read "Home -> Work".
 */
object HomeDetector {

    data class LatLon(val lat: Double, val lon: Double)

    /** ~0.0018 deg latitude ~= 200 m: tolerates day-to-day parking variance while keeping homes distinct. */
    private const val CELL = 0.0018
    /** Home must be an endpoint at least this many times, else we're not confident (new/sparse history). */
    const val MIN_ENDPOINT_HITS = 4
    /** An endpoint within this of the learned home is labelled "Home". */
    const val HOME_RADIUS_M = 200.0
    /** Around the densest grid cell, gather the full cluster within this radius (captures boundary splits). */
    private const val REFINE_RADIUS_M = 250.0

    private fun cell(lat: Double, lon: Double): Pair<Int, Int> =
        (lat / CELL).roundToInt() to (lon / CELL).roundToInt()

    private fun centroid(pts: List<LatLon>): LatLon =
        LatLon(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lon } / pts.size)

    /**
     * Detect home from all trips' endpoints (each trip contributes its start and end). Finds the densest
     * grid cell, then refines: re-centres on *all* endpoints within [REFINE_RADIUS_M] of that cell, so a
     * cluster split across a cell boundary is counted whole and the centroid is accurate. Returns null
     * when no cluster is frequent enough to trust ([minHits] guards a sparse history / one-off spots).
     */
    fun detect(endpoints: List<LatLon>, minHits: Int = MIN_ENDPOINT_HITS): LatLon? {
        if (endpoints.size < minHits) return null
        val byCell = endpoints.groupBy { cell(it.lat, it.lon) }
        val seed = centroid((byCell.maxByOrNull { it.value.size } ?: return null).value)
        val cluster = endpoints.filter {
            GeoUtils.haversine(it.lat, it.lon, seed.lat, seed.lon) <= REFINE_RADIUS_M
        }
        if (cluster.size < minHits) return null
        return centroid(cluster)
    }

    /** A learned place (home or work) is labelled when an endpoint is within this radius. */
    const val WORK_RADIUS_M = 250.0
    /** Work must be at least this far from home to count as a distinct second place (not home spillover). */
    private const val WORK_MIN_FROM_HOME_M = 1_000.0
    /** Work needs to recur at least this many times (you visit work less often than you're home). */
    const val MIN_WORK_HITS = 4

    /**
     * Detect the driver's second regular place ("work") from the endpoints that AREN'T home — the most
     * frequent remaining cluster, provided it's well clear of home ([WORK_MIN_FROM_HOME_M]) and recurs
     * enough ([minHits]). Returns null when home is unknown or no distinct second place stands out.
     */
    fun detectWork(endpoints: List<LatLon>, home: LatLon?, minHits: Int = MIN_WORK_HITS): LatLon? {
        if (home == null) return null
        val away = endpoints.filter { GeoUtils.haversine(it.lat, it.lon, home.lat, home.lon) > HOME_RADIUS_M }
        val work = detect(away, minHits) ?: return null
        if (GeoUtils.haversine(work.lat, work.lon, home.lat, home.lon) < WORK_MIN_FROM_HOME_M) return null
        return work
    }

    /** Is this point at the learned home (within [HOME_RADIUS_M])? False when home is unknown. */
    fun isHome(lat: Double, lon: Double, home: LatLon?): Boolean =
        home != null && GeoUtils.haversine(lat, lon, home.lat, home.lon) <= HOME_RADIUS_M

    /** Is this point at the learned work (within [WORK_RADIUS_M])? False when work is unknown. */
    fun isWork(lat: Double, lon: Double, work: LatLon?): Boolean =
        work != null && GeoUtils.haversine(lat, lon, work.lat, work.lon) <= WORK_RADIUS_M
}
