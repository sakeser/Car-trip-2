package com.cartrip.analyzer.cloud

import kotlin.math.floor

/**
 * Fixed-grid spatial tiling for the speed-limit cache. A tile is ~2.2 km square (0.02°), small
 * enough that "this area was fetched" is meaningful but large enough to keep the tile count per
 * trip modest. Pure and testable; keys are "y_x" integer grid indices.
 */
object Tiles {
    const val TILE_DEG = 0.02

    fun key(lat: Double, lon: Double): String {
        val y = floor(lat / TILE_DEG).toLong()
        val x = floor(lon / TILE_DEG).toLong()
        return "${y}_${x}"
    }

    /** Distinct tile keys touched by a route (one per point, deduped). */
    fun routeTiles(latLon: List<Pair<Double, Double>>): Set<String> =
        latLon.mapTo(LinkedHashSet()) { key(it.first, it.second) }

    /** [minLat, minLon, maxLat, maxLon] for a tile key. */
    fun bounds(key: String): DoubleArray {
        val sep = key.indexOf('_')
        val y = key.substring(0, sep).toLong()
        val x = key.substring(sep + 1).toLong()
        val minLat = y * TILE_DEG
        val minLon = x * TILE_DEG
        return doubleArrayOf(minLat, minLon, minLat + TILE_DEG, minLon + TILE_DEG)
    }
}
