package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripEntity
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.hypot

object TripLabeler {
    private data class Place(val name: String, val lat: Double, val lon: Double)
    private data class Corridor(val name: String, val points: List<Place>)

    private val home = Place("Harrison Garden", 43.7597, -79.4106)
    private val work = Place("Speakman Drive", 43.5148, -79.6677)

    private val places = listOf(
        home,
        work,
        Place("North York Centre", 43.7685, -79.4126),
        Place("Yorkdale", 43.7255, -79.4523),
        Place("Fairview", 43.7781, -79.3443),
        Place("Don Mills", 43.7350, -79.3462),
        Place("Leslieville", 43.6624, -79.3350),
        Place("Downtown", 43.6537, -79.3839),
        Place("High Park", 43.6465, -79.4637),
        Place("Scarborough Town Centre", 43.7764, -79.2574),
        Place("Vaughan Mills", 43.8256, -79.5396),
        Place("The Beaches", 43.6692, -79.2996),
        Place("Yonge and Eglinton", 43.7064, -79.3986),
        Place("Liberty Village", 43.6371, -79.4246),
        Place("Distillery District", 43.6503, -79.3596)
    )

    private val corridors = listOf(
        Corridor(
            "Highway 401",
            listOf(
                Place("", 43.7590, -79.5420),
                Place("", 43.7570, -79.4520),
                Place("", 43.7588, -79.3480),
                Place("", 43.7750, -79.2500)
            )
        ),
        Corridor(
            "Don Valley Parkway",
            listOf(
                Place("", 43.7950, -79.3480),
                Place("", 43.7350, -79.3340),
                Place("", 43.6770, -79.3560),
                Place("", 43.6520, -79.3600)
            )
        ),
        Corridor(
            "Gardiner Expressway",
            listOf(
                Place("", 43.6310, -79.4750),
                Place("", 43.6370, -79.4160),
                Place("", 43.6420, -79.3810),
                Place("", 43.6480, -79.3330)
            )
        ),
        Corridor(
            "Yonge Street",
            listOf(
                Place("", 43.6380, -79.3820),
                Place("", 43.7064, -79.3986),
                Place("", 43.7685, -79.4126),
                Place("", 43.8200, -79.4230)
            )
        )
    )

    fun label(trip: TripEntity, points: List<AnalysisPointEntity>): String {
        val start = points.firstOrNull() ?: return "Trip"
        val end = points.lastOrNull() ?: return "Trip"
        val hour = Calendar.getInstance().apply { timeInMillis = trip.startTime }
            .get(Calendar.HOUR_OF_DAY)

        val startsAtHome = near(start.lat, start.lon, home, 1_500.0)
        val endsAtHome = near(end.lat, end.lon, home, 1_500.0)
        val startsAtWork = near(start.lat, start.lon, work, 1_800.0)
        val endsAtWork = near(end.lat, end.lon, work, 1_800.0)

        if (startsAtHome && endsAtWork && hour in 5..10) return "AM commute"
        if (startsAtWork && endsAtHome && hour in 11..19) return "PM commute"

        val from = nearestPlace(start.lat, start.lon)
        val to = nearestPlace(end.lat, end.lon)
        if (from.name != to.name) return "${from.name} to ${to.name}"

        return mainCorridor(points)?.let { "$it drive" } ?: "${from.name} drive"
    }

    private fun nearestPlace(lat: Double, lon: Double): Place =
        places.minBy { GeoUtils.haversine(lat, lon, it.lat, it.lon) }

    private fun near(lat: Double, lon: Double, place: Place, radiusM: Double): Boolean =
        GeoUtils.haversine(lat, lon, place.lat, place.lon) <= radiusM

    private fun mainCorridor(points: List<AnalysisPointEntity>): String? {
        if (points.size < 3) return null
        val best = corridors
            .map { corridor ->
                val close = points.count { point ->
                    corridor.points.zipWithNext().any { (a, b) ->
                        distanceToSegmentM(point.lat, point.lon, a, b) <= 900.0
                    }
                }
                corridor.name to close / points.size.toDouble()
            }
            .maxByOrNull { it.second }
            ?: return null
        return best.first.takeIf { best.second >= 0.35 }
    }

    private fun distanceToSegmentM(lat: Double, lon: Double, a: Place, b: Place): Double {
        val latScale = 111_320.0
        val lonScale = 111_320.0 * cos(Math.toRadians((lat + a.lat + b.lat) / 3.0))
        val px = lon * lonScale
        val py = lat * latScale
        val ax = a.lon * lonScale
        val ay = a.lat * latScale
        val bx = b.lon * lonScale
        val by = b.lat * latScale
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
