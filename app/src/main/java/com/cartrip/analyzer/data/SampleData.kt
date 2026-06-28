package com.cartrip.analyzer.data

import android.content.Context
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.analysis.TripAnalyzer
import com.cartrip.analyzer.cloud.RoutesClient
import com.cartrip.analyzer.cloud.RoutesConfig
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Generates realistic demo trips.
 *
 * Rather than linearly interpolating between a few waypoints (which produces
 * straight lines through buildings and a speed curve that never actually stops),
 * each trip is produced by a small longitudinal "driver" simulation that follows
 * a road-aligned polyline: it accelerates from rest, cruises at the road-class
 * speed, brakes to a full stop at intersections, idles, and launches again.
 * Acceleration, cornering and hard events are then detected from that motion with
 * the same thresholds the real analyzer uses, so the replay lines up with reality.
 */
object SampleData {
    private const val HOME_LAT = 43.7597
    private const val HOME_LON = -79.4106
    private const val WORK_LAT = 43.5148
    private const val WORK_LON = -79.6677

    // Simulation cadence. Step the physics each [SIM_DT] s, store a fix every [EMIT_S] s
    // (~real GPS rate). EMIT_S = 2 also makes per-fix speed deltas map cleanly onto the
    // analyzer's hard-event thresholds (a 4 m/s² brake shows up as a real hard brake).
    private const val SIM_DT = 1.0
    private const val EMIT_S = 2.0
    private const val IDLE_SPEED_KMH = 1.8       // below this the car is "stopped"
    private const val MAX_LEG_S = 1500           // safety guard against runaway legs

    private data class Place(val name: String, val lat: Double, val lon: Double)
    private data class RoutePt(val lat: Double, val lon: Double, val cruiseKmh: Double)
    private data class Stop(val dist: Double, val dwellS: Double, val hardBrake: Boolean, val hardLaunch: Boolean)
    private data class Creds(val apiKey: String, val pkg: String, val sha1: String?)

    /** Google Routes estimate + road-following geometry for a demo trip, when available. */
    private class Routing(
        val route: List<RoutePt>,
        val trafficS: Double,
        val freeFlowS: Double,
        val source: String, // "typical" when from Routes API, "" when synthetic fallback
        val congestion: Double? // traffic/free-flow speed ratio for this slot; null => unknown
    )

    @Volatile private var routeWarning: String? = null

    private val home = Place("Harrison Garden", HOME_LAT, HOME_LON)
    private val work = Place("Speakman", WORK_LAT, WORK_LON)
    private val randomPlaces = listOf(
        Place("Yorkdale", 43.7255, -79.4523),
        Place("Fairview", 43.7781, -79.3443),
        Place("Don Mills", 43.7350, -79.3462),
        Place("Leslieville", 43.6624, -79.3350),
        Place("Downtown", 43.6537, -79.3839),
        Place("High Park", 43.6465, -79.4637),
        Place("Scarborough Town", 43.7764, -79.2574),
        Place("Vaughan Mills", 43.8256, -79.5396),
        Place("North York Centre", 43.7685, -79.4126),
        Place("The Beaches", 43.6692, -79.2996),
        Place("Yonge and Eglinton", 43.7064, -79.3986),
        Place("Liberty Village", 43.6371, -79.4246),
        Place("Distillery District", 43.6503, -79.3596),
        Place("Kensington Market", 43.6545, -79.4004),
        Place("Danforth", 43.6783, -79.3487),
        Place("Etobicoke", 43.6205, -79.5132),
        Place("Downsview", 43.7387, -79.4897)
    )

    /** Set after [resetWithDemoTrips] if Routes API geometry was unavailable; null when all good. */
    fun lastRouteWarning(): String? = routeWarning

    suspend fun resetWithDemoTrips(context: Context, dao: TripDao) {
        // Only ever clear previously-generated sample trips — real recorded drives are preserved.
        dao.deleteSampleLocations()
        dao.deleteSampleMotions()
        dao.deleteSampleAnalysisPoints()
        dao.deleteSampleDriveEvents()
        dao.deleteSampleTrips()

        routeWarning = null
        val creds = RoutesConfig.apiKey(context)?.let {
            Creds(it, RoutesConfig.androidPackage(context), RoutesConfig.signingSha1(context))
        }
        if (creds == null) routeWarning = "No Maps API key - demo routes use varied local fallback paths."

        val rng = Random(System.currentTimeMillis())
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -29)
        }

        for (offset in 0 until 30) {
            val day = start.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, offset)
            val dow = day.get(Calendar.DAY_OF_WEEK)
            val weekday = dow !in listOf(Calendar.SATURDAY, Calendar.SUNDAY)

            if (weekday && rng.nextDouble() < 0.74) {
                createTrip(creds, dao, rng, day, home, work, 6, 30 + rng.nextInt(60), commute = true)
                createTrip(creds, dao, rng, day, work, home, 12 + rng.nextInt(5), rng.nextInt(60), commute = true)
            }

            if (rng.nextDouble() < if (weekday) 0.33 else 0.78) {
                val from = if (rng.nextBoolean()) home else randomPlaces.random(rng)
                val to = randomPlaces.filter { it.name != from.name }.random(rng)
                val hour = if (weekday) 18 + rng.nextInt(4) else 9 + rng.nextInt(10)
                createTrip(creds, dao, rng, day, from, to, hour, rng.nextInt(60), commute = false)
            }
        }
    }

    private suspend fun createTrip(
        creds: Creds?,
        dao: TripDao,
        rng: Random,
        date: Calendar,
        startPlace: Place,
        endPlace: Place,
        departureHour: Int,
        departureMinute: Int,
        commute: Boolean
    ) {
        val startCal = (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, departureHour)
            set(Calendar.MINUTE, departureMinute)
            set(Calendar.SECOND, rng.nextInt(60))
        }
        val startTime = startCal.timeInMillis
        // Spread driving styles across the fleet so scores exercise the full range: skew toward
        // calmer drivers but give a meaningful tail of aggressive ones.
        val aggression = rng.nextDouble().let { it * it }.let { it + rng.nextDouble() * 0.25 }.coerceIn(0.0, 1.0)

        val routing = resolveRouting(creds, rng, startPlace, endPlace, startTime, commute)
        // Prefer the real traffic/free-flow ratio for this route+time so the 401 actually crawls
        // at rush hour; fall back to a random factor only when no Google estimate is available.
        // A per-trip "pace" then makes some drives beat traffic and others fall behind it.
        val baseCongestion = routing.congestion ?: (0.62 + rng.nextDouble() * 0.34)
        val pace = 0.80 + rng.nextDouble() * 0.45
        val congestion = (baseCongestion * pace).coerceIn(0.30, 1.25)
        val track: MutableList<AnalysisPointEntity> =
            simulate(rng, routing.route, startTime, aggression, congestion)
        if (track.size < 2) return

        val events = detectEvents(track)
        addCorners(rng, track, events, commute, aggression)

        val distance = track.zipWithNext().sumOf { (a, b) ->
            GeoUtils.haversine(a.lat, a.lon, b.lat, b.lon)
        }
        val durationS = (track.last().t - track.first().t) / 1000.0
        val movingS = track.count { it.speedKmh > IDLE_SPEED_KMH } * EMIT_S
        val idleS = max(0.0, durationS - movingS)
        val maxSpeedMps = (track.maxOfOrNull { it.speedKmh } ?: 0.0) / 3.6
        val avgMovingMps = if (movingS > 0.0) distance / movingS else 0.0

        val hardAccel = events.count { it.type == EventType.ACCEL.name }
        val hardBrake = events.count { it.type == EventType.BRAKE.name }
        val hardCorner = events.count { it.type == EventType.CORNER.name }
        val km = max(0.1, distance / 1000.0)
        val smoothness = (100 - ((hardBrake + hardAccel * 0.8 + hardCorner * 0.8) / km * 8))
            .coerceIn(40.0, 99.0)
            .toInt()

        // Exposure factors (fraction of moving time over a g threshold). The synthetic track only
        // spikes events instantaneously, so synthesize realistic sustained percentages from the
        // driver's aggression instead — this is what the Tesla-style safety score reads.
        val sq = aggression * aggression
        val hardBrakePct = (sq * 0.05 + rng.nextDouble() * 0.008).coerceIn(0.0, 0.09)
        val aggressiveTurnPct = (sq * 0.03 + rng.nextDouble() * 0.005).coerceIn(0.0, 0.06)
        val hardAccelPct = (sq * 0.035 + rng.nextDouble() * 0.006).coerceIn(0.0, 0.07)
        val speedBias = 1.0 + aggression * 0.42
        val speedingPct = (((speedBias - 1.0) * 2.2) * (0.4 + 0.6 * congestion))
            .coerceIn(0.0, 0.85)
        val maxOverLimitKmh = ((speedBias - 1.0) * 55.0).coerceIn(0.0, 35.0) + rng.nextDouble() * 5.0
        val limitCoverage = 0.85 + rng.nextDouble() * 0.15
        // Rough demo value for the magnitude-weighted speeding metric (real trips compute it from points):
        // mean(max(0, over-5)^2) ~ time-over x (typical excess)^2, with typical excess ~ maxOver/2.
        val speedingSeverity = speedingPct * Math.pow(maxOf(0.0, maxOverLimitKmh / 2.0 - 5.0), 2.0)
        val jerkyPct = (sq * 0.045 + rng.nextDouble() * 0.01).coerceIn(0.0, 0.10)
        val maxJerk = 1.0 + aggression * 6.0 + rng.nextDouble() * 1.5
        val roughRoadPct = (rng.nextDouble() * 0.18).coerceIn(0.0, 0.30)
        val potholeCount = if (rng.nextDouble() < 0.4) rng.nextInt(1, 4) else 0
        val harshStopCount = (aggression * rng.nextInt(0, 4)).toInt()
        // Demo: the sensor detector tends to catch a few more than GPS.
        val motionBrakeCount = hardBrake + (if (rng.nextDouble() < 0.5) rng.nextInt(0, 2) else 0)
        val motionAccelCount = hardAccel + (if (rng.nextDouble() < 0.4) 1 else 0)
        val motionTurnCount = hardCorner + (if (rng.nextDouble() < 0.45) rng.nextInt(0, 2) else 0)
        val fusedConfidence = 0.62 + rng.nextDouble() * 0.34

        val tripId = dao.insertTrip(
            TripEntity(
                startTime = startTime,
                endTime = track.last().t,
                distanceM = distance,
                durationS = durationS,
                movingS = movingS,
                idleS = idleS,
                maxSpeedMps = maxSpeedMps,
                avgMovingSpeedMps = avgMovingMps,
                maxAccelMps2 = track.maxOfOrNull { it.longAccel } ?: 0.0,
                maxBrakeMps2 = abs(track.minOfOrNull { it.longAccel } ?: 0.0),
                maxLateralMps2 = track.maxOfOrNull { abs(it.latAccel) } ?: 0.0,
                peakGForce = 0.22 + aggression * 0.7 + rng.nextDouble() * 0.12,
                hardAccelCount = hardAccel,
                hardBrakeCount = hardBrake,
                hardCornerCount = hardCorner,
                smoothness = smoothness,
                analyzed = true,
                googleEtaTrafficS = routing.trafficS,
                googleEtaFreeFlowS = routing.freeFlowS,
                etaSource = routing.source,
                etaFetchedAt = if (routing.source.isNotEmpty()) System.currentTimeMillis() else 0L,
                hardBrakePct = hardBrakePct,
                aggressiveTurnPct = aggressiveTurnPct,
                hardAccelPct = hardAccelPct,
                speedingPct = speedingPct,
                maxOverLimitKmh = maxOverLimitKmh,
                limitCoverage = limitCoverage,
                speedingSeverity = speedingSeverity,
                maxJerk = maxJerk,
                jerkyPct = jerkyPct,
                isSample = true,
                roughRoadPct = roughRoadPct,
                potholeCount = potholeCount,
                harshStopCount = harshStopCount,
                motionBrakeCount = motionBrakeCount,
                motionAccelCount = motionAccelCount,
                motionTurnCount = motionTurnCount,
                fusedConfidence = fusedConfidence
            )
        )
        dao.insertAnalysisPoints(track.map { it.copy(tripId = tripId) })
        if (events.isNotEmpty()) dao.insertDriveEvents(events.map { it.copy(tripId = tripId) })
    }

    /**
     * Prefer real road geometry + a "typical for this time" estimate from the Routes API;
     * fall back to the synthetic straight-line route when the API is unavailable.
     */
    private fun resolveRouting(
        creds: Creds?,
        rng: Random,
        start: Place,
        end: Place,
        startTime: Long,
        commute: Boolean
    ): Routing {
        if (creds != null) {
            val res = runCatching {
                RoutesClient.computeRoute(
                    apiKey = creds.apiKey,
                    androidPackage = creds.pkg,
                    androidCertSha1 = creds.sha1,
                    originLat = start.lat, originLon = start.lon,
                    destLat = end.lat, destLon = end.lon,
                    departureRfc3339 = RoutesConfig.typicalDepartureFor(startTime)
                )
            }
            val r = res.getOrNull()
            if (r != null && r.polyline.size >= 2) {
                val cong = if (r.trafficS > 0 && r.freeFlowS > 0) {
                    (r.freeFlowS / r.trafficS).coerceIn(0.38, 1.0)
                } else null
                return Routing(polylineToRoute(r.polyline), r.trafficS, r.freeFlowS, "typical", cong)
            }
            val msg = res.exceptionOrNull()?.message
            if (routeWarning == null && msg != null) {
                routeWarning = "Routes API unavailable - using varied local fallback routes. ($msg)"
            }
        }
        return Routing(routeFor(start, end, commute, rng), 0.0, 0.0, "", null)
    }

    /**
     * Turn a decoded Google polyline into cruise-tagged route points. Cruise is inferred from
     * geometry: surface-street speeds near the endpoints, highway speeds on long straight runs,
     * arterial speeds through twisty stretches — so the stop-and-go model stops in the right places.
     */
    private fun polylineToRoute(rawPoly: List<DoubleArray>): List<RoutePt> {
        val poly = downsample(rawPoly, 250)
        if (poly.size < 2) return poly.map { RoutePt(it[0], it[1], 50.0) }
        val cum = DoubleArray(poly.size)
        for (i in 1 until poly.size) {
            cum[i] = cum[i - 1] + GeoUtils.haversine(poly[i - 1][0], poly[i - 1][1], poly[i][0], poly[i][1])
        }
        val total = cum.last()
        return poly.mapIndexed { i, p ->
            val fromEnd = kotlin.math.min(cum[i], total - cum[i])
            val cruise = when {
                fromEnd < 1200.0 -> 48.0
                curvaturePer100m(poly, i) > 9.0 -> 55.0
                else -> 96.0
            }
            RoutePt(p[0], p[1], cruise)
        }
    }

    private fun curvaturePer100m(poly: List<DoubleArray>, i: Int): Double {
        if (i <= 0 || i >= poly.size - 1) return 0.0
        val a = poly[i - 1]; val b = poly[i]; val c = poly[i + 1]
        val ang = abs(GeoUtils.angleDiffDeg(
            GeoUtils.bearing(b[0], b[1], c[0], c[1]),
            GeoUtils.bearing(a[0], a[1], b[0], b[1])
        ))
        val d = (GeoUtils.haversine(a[0], a[1], b[0], b[1]) +
            GeoUtils.haversine(b[0], b[1], c[0], c[1])) / 2.0
        return if (d < 1.0) 0.0 else ang / (d / 100.0)
    }

    private fun downsample(poly: List<DoubleArray>, maxPoints: Int): List<DoubleArray> {
        if (poly.size <= maxPoints) return poly
        val stride = poly.size.toDouble() / maxPoints
        val out = ArrayList<DoubleArray>(maxPoints)
        var acc = 0.0
        var i = 0
        while (i < poly.size) {
            out += poly[i]
            acc += stride
            i = acc.toInt()
        }
        if (out.last() !== poly.last()) out += poly.last()
        return out
    }

    /** Road-aligned polyline with a cruise speed (km/h) for the segment leading to each point. */
    private fun routeFor(start: Place, end: Place, commute: Boolean, rng: Random): List<RoutePt> {
        if (commute) {
            // Harrison Garden <-> Speakman roughly follows Hwy 401/403 west.
            val highway = listOf(
                RoutePt(43.7597, -79.4106, 50.0),   // home, surface streets to the on-ramp
                RoutePt(43.7584, -79.4539, 55.0),
                RoutePt(43.7400, -79.5100, 100.0),  // on Hwy 401
                RoutePt(43.7195, -79.5608, 100.0),
                RoutePt(43.6700, -79.6050, 100.0),
                RoutePt(43.6227, -79.6424, 95.0),
                RoutePt(43.5534, -79.6690, 65.0),   // off-ramp + arterial
                RoutePt(43.5148, -79.6677, 45.0)    // work
            )
            return if (start.name == home.name) highway else highway.reversed()
        }
        // Local arterial drive: varied bent paths at city speeds with frequent lights.
        val bend = if (rng.nextBoolean()) 1.0 else -1.0
        val mid = RoutePt(
            lat = (start.lat + end.lat) / 2 + bend * 0.010 + rng.nextDouble(-0.012, 0.012),
            lon = (start.lon + end.lon) / 2 - bend * 0.010 + rng.nextDouble(-0.012, 0.012),
            cruiseKmh = 45.0 + rng.nextDouble() * 18.0
        )
        val extra = RoutePt(
            lat = mid.lat + rng.nextDouble(-0.010, 0.010),
            lon = mid.lon + rng.nextDouble(-0.010, 0.010),
            cruiseKmh = 42.0 + rng.nextDouble() * 16.0
        )
        return listOf(
            RoutePt(start.lat, start.lon, 45.0),
            mid,
            extra,
            RoutePt(end.lat, end.lon, 45.0)
        )
    }

    /**
     * Longitudinal driver simulation along [route]. Produces fixes at [EMIT_S] cadence with
     * realistic stop-and-go motion; longitudinal/lateral acceleration are derived afterward.
     */
    private fun simulate(
        rng: Random,
        route: List<RoutePt>,
        startTime: Long,
        aggression: Double,
        congestion: Double
    ): MutableList<AnalysisPointEntity> {
        val segDist = route.zipWithNext().map { (a, b) -> GeoUtils.haversine(a.lat, a.lon, b.lat, b.lon) }
        val cum = ArrayList<Double>(route.size).apply {
            add(0.0); segDist.forEach { add(last() + it) }
        }
        val total = cum.last()
        if (total < 50.0) return mutableListOf()

        fun cruiseAt(d: Double): Double {
            val i = cum.indexOfLast { it <= d }.coerceIn(0, route.size - 2)
            return route[i + 1].cruiseKmh
        }
        fun posAt(d: Double): Pair<Double, Double> {
            val i = cum.indexOfLast { it <= d }.coerceIn(0, route.size - 2)
            val f = ((d - cum[i]) / segDist[i].coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
            return lerp(route[i].lat, route[i + 1].lat, f) to lerp(route[i].lon, route[i + 1].lon, f)
        }

        // Aggressive drivers cruise faster — this pushes max speed past the limit on free-flowing
        // roads so the speeding component of the safety score actually engages.
        val speedBias = 1.0 + aggression * 0.42

        // Place stops. Urban stretches get lights/signs; highways stay free-flowing unless the
        // congestion factor is low (rush hour), in which case they break into stop-and-go crawl.
        val jamProb = ((0.6 - congestion) * 1.4).coerceIn(0.0, 0.55)  // 0 when free, up to ~0.55 when jammed
        val stops = ArrayList<Stop>()
        var d = 220.0
        while (d < total - 180.0) {
            if (cruiseAt(d) <= 60.0) {
                if (rng.nextDouble() < 0.55) {
                    stops.add(
                        Stop(
                            dist = d,
                            dwellS = 4.0 + rng.nextInt(34),
                            hardBrake = rng.nextDouble() < aggression * 0.65,
                            hardLaunch = rng.nextDouble() < aggression * 0.55
                        )
                    )
                }
                d += 320 + rng.nextInt(560)
            } else if (rng.nextDouble() < jamProb) {
                // Rush-hour highway crawl: brief near-stops, occasionally a hard brake into the jam.
                stops.add(
                    Stop(
                        dist = d,
                        dwellS = 1.0 + rng.nextInt(6),
                        hardBrake = rng.nextDouble() < aggression * 0.5,
                        hardLaunch = false
                    )
                )
                d += 260 + rng.nextInt(520)
            } else {
                d += 800 + rng.nextInt(1300)   // highway flowing freely
            }
        }

        val out = ArrayList<AnalysisPointEntity>(1024)
        var pos = 0.0
        var v = 0.0          // m/s
        var tMs = startTime
        var sinceEmit = EMIT_S
        fun emit() {
            val (lat, lon) = posAt(pos.coerceIn(0.0, total))
            out += AnalysisPointEntity(
                tripId = 0, t = tMs, lat = lat, lon = lon,
                speedKmh = v * 3.6, longAccel = 0.0, latAccel = 0.0
            )
        }
        emit()

        val targets = stops.map { it.dist } + total
        var legStart = 0.0
        for ((legIdx, target) in targets.withIndex()) {
            if (target - legStart < 30.0) { legStart = target; continue }
            val stop = stops.getOrNull(legIdx)
            // Comfortable everyday accel ~1.1-1.8 m/s², braking ~1.5-2.2; flagged maneuvers reach
            // into the hard (3+) and very-hard (4.5+) range so the demo shows a realistic mix.
            val launchAccel = if (stop?.hardLaunch == true) 2.6 + rng.nextDouble() * 1.3
            else 1.1 + rng.nextDouble() * 0.7
            val brakeDecel = if (stop?.hardBrake == true) 3.2 + rng.nextDouble() * 2.6
            else 1.5 + rng.nextDouble() * 0.7

            var legT = 0
            while (pos < target - 0.5 && legT < MAX_LEG_S) {
                val cruiseMps = cruiseAt(pos) * congestion * speedBias / 3.6
                val distToStop = target - pos
                val brakingDist = v * v / (2 * brakeDecel)
                val desired = if (distToStop <= brakingDist + 1.0) 0.0 else cruiseMps

                v = if (v < desired) min(desired, v + launchAccel * SIM_DT)
                else max(desired, v - brakeDecel * SIM_DT)
                if (v < 0.05 && desired == 0.0) { v = 0.0; pos = target }
                pos += v * SIM_DT

                tMs += (SIM_DT * 1000).toLong()
                sinceEmit -= SIM_DT
                if (sinceEmit <= 0.0) { emit(); sinceEmit = EMIT_S }
                legT++
            }
            // Dwell at the stop (skip the final arrival point).
            val dwell = stop?.dwellS ?: 0.0
            var dwelled = 0.0
            while (dwelled < dwell) {
                v = 0.0
                tMs += (SIM_DT * 1000).toLong()
                dwelled += SIM_DT
                sinceEmit -= SIM_DT
                if (sinceEmit <= 0.0) { emit(); sinceEmit = EMIT_S }
            }
            legStart = target
        }
        if (out.last().t != tMs) emit()

        return withAccel(out)
    }

    /** Fill in longitudinal/lateral acceleration from the emitted speed + bearing series. */
    private fun withAccel(points: List<AnalysisPointEntity>): MutableList<AnalysisPointEntity> {
        if (points.size < 2) return points.toMutableList()
        val out = ArrayList<AnalysisPointEntity>(points.size)
        out += points.first()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dt = ((b.t - a.t) / 1000.0).coerceAtLeast(0.5)
            val vB = b.speedKmh / 3.6
            val longAccel = (vB - a.speedKmh / 3.6) / dt
            var latAccel = 0.0
            if (vB > 1.5) {
                val bearingA = GeoUtils.bearing(
                    points[maxOf(0, i - 2)].lat, points[maxOf(0, i - 2)].lon, a.lat, a.lon
                )
                val bearingB = GeoUtils.bearing(a.lat, a.lon, b.lat, b.lon)
                val yawRate = Math.toRadians(GeoUtils.angleDiffDeg(bearingB, bearingA)) / dt
                latAccel = (vB * yawRate).coerceIn(-2.5, 2.5)   // gentle by default; sharp turns injected separately
            }
            out += b.copy(longAccel = longAccel, latAccel = latAccel)
        }
        return out
    }

    /** Detect hard accel/brake events from the finished track, mirroring the real analyzer. */
    private fun detectEvents(track: List<AnalysisPointEntity>): MutableList<DriveEventEntity> {
        val events = ArrayList<DriveEventEntity>()
        var lastAccel = -1e9
        var lastBrake = -1e9
        track.forEach { p ->
            val tSec = p.t / 1000.0
            if (p.longAccel >= TripAnalyzer.HARD_ACCEL && tSec - lastAccel >= 2.0) {
                events += DriveEventEntity(tripId = 0, t = p.t, type = EventType.ACCEL.name, magnitude = p.longAccel)
                lastAccel = tSec
            }
            if (-p.longAccel >= TripAnalyzer.HARD_BRAKE && tSec - lastBrake >= 2.0) {
                events += DriveEventEntity(tripId = 0, t = p.t, type = EventType.BRAKE.name, magnitude = -p.longAccel)
                lastBrake = tSec
            }
        }
        return events
    }

    /** Inject a few realistic hard corners into the track and record them as events. */
    private fun addCorners(
        rng: Random,
        track: MutableList<AnalysisPointEntity>,
        events: MutableList<DriveEventEntity>,
        commute: Boolean,
        aggression: Double
    ) {
        val maxCorners = if (commute) 2 else 1
        val count = (rng.nextDouble() * (1 + aggression) * (maxCorners + 1)).toInt().coerceIn(0, maxCorners)
        repeat(count) {
            val i = rng.nextInt(track.size / 5, track.size - track.size / 5)
            val p = track[i]
            if (p.speedKmh < 18.0) return@repeat            // need to be moving to corner hard
            val mag = 3.6 + rng.nextDouble() * 2.2          // up into the very-sharp (5+) range
            val signed = if (rng.nextBoolean()) mag else -mag
            track[i.coerceIn(0, track.lastIndex)] = p.copy(latAccel = signed)
            events += DriveEventEntity(tripId = 0, t = p.t, type = EventType.CORNER.name, magnitude = mag)
        }
        events.sortBy { it.t }
    }

    private fun min(a: Double, b: Double) = if (a < b) a else b
    private fun lerp(a: Double, b: Double, f: Double): Double = a + (b - a) * f

}
