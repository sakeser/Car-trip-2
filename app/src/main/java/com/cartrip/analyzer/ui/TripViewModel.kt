package com.cartrip.analyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.analysis.TripAnalyzer
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.cloud.RoutesClient
import com.cartrip.analyzer.cloud.RoutesConfig
import com.cartrip.analyzer.cloud.SpeedLimits
import com.cartrip.analyzer.cloud.TripSync
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.SampleData
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.data.TripFinalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        /** Max live reverse-geocode lookups per trip-list refresh (an unnamed trip uses up to 3). */
        const val GEO_BUDGET_PER_REFRESH = 16
        /** Persisted learned-home location (lat/lon), shared with the single-trip title path. */
        const val HOME_PREFS = "cartrip_home"
    }

    private val dao = AppDatabase.get(app).tripDao()

    /** The farthest track point from the trip's start — the natural turnaround/destination of a loop. */
    private fun farthestFrom(points: List<AnalysisPointEntity>): AnalysisPointEntity? {
        val start = points.firstOrNull() ?: return null
        return points.maxByOrNull { GeoUtils.haversine(start.lat, start.lon, it.lat, it.lon) }
    }

    private fun loadHome(ctx: Application): HomeDetector.LatLon? {
        val p = ctx.getSharedPreferences(HOME_PREFS, android.content.Context.MODE_PRIVATE)
        if (!p.contains("lat")) return null
        return HomeDetector.LatLon(
            java.lang.Double.longBitsToDouble(p.getLong("lat", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("lon", 0))
        )
    }

    private fun saveHome(ctx: Application, home: HomeDetector.LatLon?) {
        val p = ctx.getSharedPreferences(HOME_PREFS, android.content.Context.MODE_PRIVATE)
        if (home == null) { p.edit().remove("lat").remove("lon").apply(); return }
        p.edit()
            .putLong("lat", java.lang.Double.doubleToRawLongBits(home.lat))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(home.lon))
            .apply()
    }

    private fun loadWork(ctx: Application): HomeDetector.LatLon? {
        val p = ctx.getSharedPreferences(HOME_PREFS, android.content.Context.MODE_PRIVATE)
        if (!p.contains("work_lat")) return null
        return HomeDetector.LatLon(
            java.lang.Double.longBitsToDouble(p.getLong("work_lat", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("work_lon", 0))
        )
    }

    private fun saveWork(ctx: Application, work: HomeDetector.LatLon?) {
        val p = ctx.getSharedPreferences(HOME_PREFS, android.content.Context.MODE_PRIVATE)
        if (work == null) { p.edit().remove("work_lat").remove("work_lon").apply(); return }
        p.edit()
            .putLong("work_lat", java.lang.Double.doubleToRawLongBits(work.lat))
            .putLong("work_lon", java.lang.Double.doubleToRawLongBits(work.lon))
            .apply()
    }

    val trips: StateFlow<List<TripEntity>> =
        dao.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadTrip(id: Long): TripEntity? = withContext(Dispatchers.IO) { dao.getTrip(id) }

    /**
     * A human title for the trip-detail header: the user's manual name if set, else a reverse-geocoded
     * "start -> end" place-name (cached, fail-soft), else a relative date ("Today" / "Yesterday" / date).
     */
    suspend fun loadTripTitle(trip: TripEntity): String = withContext(Dispatchers.IO) {
        if (trip.name.isNotBlank()) return@withContext trip.name
        val points = dao.getAnalysisPoints(trip.id)
        val start = points.firstOrNull()
        val end = points.lastOrNull()
        val via = farthestFrom(points)
        val home = loadHome(getApplication())
        val work = loadWork(getApplication())
        val geo = if (start != null && end != null) {
            GeoNamer.nameTrip(
                getApplication(), start.lat, start.lon, end.lat, end.lon,
                GeoNamer.Budget(3), home, via?.lat, via?.lon, work
            )
        } else null
        geo ?: Format.relativeDay(trip.startTime)
    }

    suspend fun recoverInterruptedTrips(): Int = withContext(Dispatchers.IO) {
        TripFinalizer.recoverInterruptedTrips(dao)
    }

    /**
     * One-time backfill (Rev BG) of the Rev BF magnitude-weighted `speedingSeverity` for trips that
     * pre-date it. Recomputed from each trip's *stored* per-point speed limits (`analysis_points`), so it
     * needs no network — only trips whose limits were already fetched (`limitCoverage > 0`) can be filled.
     * Guarded by a pref so it runs once; cheap and idempotent thereafter.
     */
    suspend fun backfillSpeedingSeverity() = withContext(Dispatchers.IO) {
        val prefs = getApplication<Application>()
            .getSharedPreferences("cartrip_maint", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("speedingSeverityBackfilled_v20", false)) return@withContext
        var updated = 0
        for (t in dao.getAllTrips()) {
            if (t.speedingSeverity > 0.0 || t.limitCoverage <= 0.0) continue
            val pts = dao.getAnalysisPoints(t.id)
            if (pts.size < 5) continue
            val limits = pts.map { if (it.speedLimitKmh > 0.0) it.speedLimitKmh else null }
            if (limits.none { it != null }) continue
            val r = SpeedLimits.speedingSummary(pts.map { it.t }, pts.map { it.speedKmh }, limits)
            if (r.severity > 0.0) { dao.updateTrip(t.copy(speedingSeverity = r.severity)); updated++ }
        }
        prefs.edit().putBoolean("speedingSeverityBackfilled_v20", true).apply()
        updated
    }

    /** Sweep any finished-but-unsynced trips to Google Sheets (recovered partials, past failures). */
    suspend fun syncPendingTrips(): Int = withContext(Dispatchers.IO) {
        TripSync.syncPending(getApplication(), dao)
    }

    /** Force a re-sync of one trip (the per-trip "Sync now" button). Null on success, else a message. */
    suspend fun resyncTrip(id: Long): String? = withContext(Dispatchers.IO) {
        val result = TripSync.syncOne(getApplication(), dao, id, force = true)
        if (result.isSuccess) null else (result.exceptionOrNull()?.message ?: "Sync failed.")
    }

    /**
     * Look up OSM speed limits for a trip on demand (colours the route + computes speeding).
     * Returns null on success, or a short message to surface to the user.
     */
    suspend fun fetchSpeedLimits(id: Long): String? = withContext(Dispatchers.IO) {
        val attempt = runCatching { SpeedLimits.refreshForTrip(dao, id) }
        val result = attempt.getOrNull()
        when {
            attempt.isFailure -> "Speed-limit lookup failed: ${attempt.exceptionOrNull()?.message}"
            result == null ->
                "Couldn't fetch speed limits. ${SpeedLimits.lastDiagnostic()}".trim()
            result.coverage < 0.4 ->
                "Limits found for only ${(result.coverage * 100).toInt()}% of the route — not enough to score speeding."
            else -> null
        }
    }

    /**
     * Fetch a "typical for this time of day" Google estimate for an existing trip and persist it.
     * Returns null on success, or a short error message to surface to the user.
     */
    suspend fun fetchTypicalEstimate(id: Long): String? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val trip = dao.getTrip(id) ?: return@withContext "Trip not found."
        val points = dao.getAnalysisPoints(id)
        val start = points.firstOrNull()
        val end = points.lastOrNull()
        if (start == null || end == null) return@withContext "No route recorded for this trip."
        if (GeoUtils.haversine(start.lat, start.lon, end.lat, end.lon) < 300.0) {
            return@withContext "Start and end are too close to estimate."
        }
        val key = RoutesConfig.apiKey(ctx) ?: return@withContext "No Maps API key configured."
        runCatching {
            val r = RoutesClient.computeRoute(
                apiKey = key,
                androidPackage = RoutesConfig.androidPackage(ctx),
                androidCertSha1 = RoutesConfig.signingSha1(ctx),
                originLat = start.lat, originLon = start.lon,
                destLat = end.lat, destLon = end.lon,
                departureRfc3339 = RoutesConfig.typicalDepartureFor(trip.startTime)
            )
            dao.updateTrip(
                trip.copy(
                    googleEtaTrafficS = r.trafficS,
                    googleEtaFreeFlowS = r.freeFlowS,
                    etaSource = "typical",
                    etaFetchedAt = System.currentTimeMillis()
                )
            )
        }.exceptionOrNull()?.message
    }

    /** Lightweight route (stored analysis points) for the Past Trips map preview. */
    suspend fun loadRoute(id: Long): List<TrackPoint> = withContext(Dispatchers.IO) {
        dao.getAnalysisPoints(id).map {
            TrackPoint(it.t, it.lat, it.lon, it.speedKmh, it.longAccel, it.latAccel, it.speedLimitKmh)
        }
    }

    suspend fun loadHeatmapPoints(days: Int): List<AnalysisPointEntity> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.getAnalysisPointsSince(cutoff)
    }

    /**
     * Cross-trip recurring-event hotspots (roadmap item 9): places where the same *maneuver* kind (braking,
     * acceleration, sharp turn, hard stop) recurs across distinct drives. Rough spots/potholes are excluded
     * (Rev BS). Each event is located via the nearest analysis point by time and carries its g-force + the
     * drive's date for the tap-to-detail view. Walks and sample trips are excluded. See [EventHotspots].
     */
    suspend fun loadEventHotspots(gForceFloor: Double): List<EventHotspots.Hotspot> = withContext(Dispatchers.IO) {
        val drives = dao.getAllTrips().filter {
            it.endTime > 0 && !it.isSample && !TripKind.isLikelyNonDrive(it)
        }
        val evs = ArrayList<EventHotspots.Ev>()
        for (t in drives) {
            val events = dao.getDriveEvents(t.id)
            if (events.isEmpty()) continue
            val pts = dao.getAnalysisPoints(t.id)
            if (pts.isEmpty()) continue
            for (e in events) {
                val type = runCatching { EventType.valueOf(e.type) }.getOrNull() ?: continue
                if (type == EventType.POTHOLE) continue   // rough spots removed from hotspots (Rev BS)
                val p = pts.minByOrNull { kotlin.math.abs(it.t - e.t) } ?: continue
                if (kotlin.math.abs(p.t - e.t) > 4000) continue   // no nearby fix -> can't place it
                evs.add(
                    EventHotspots.Ev(
                        t.id, EventHotspots.kindOf(type), p.lat, p.lon,
                        gForce = e.magnitude / 9.80665, tripStartWall = t.startTime
                    )
                )
            }
        }
        // Tag each hotspot with a friendly location: home/work for free, else a reverse-geocoded
        // neighbourhood for the strongest few (budget caps live geocoder calls; cache makes refreshes free).
        val home = loadHome(getApplication()); val work = loadWork(getApplication())
        val budget = GeoNamer.Budget(8)
        EventHotspots.find(evs, gForceFloor = gForceFloor).mapIndexed { i, h ->
            val where = when {
                HomeDetector.isHome(h.lat, h.lon, home) -> "near Home"
                HomeDetector.isWork(h.lat, h.lon, work) -> "near Work"
                i < 8 -> GeoNamer.areaName(getApplication(), h.lat, h.lon, budget) ?: ""
                else -> ""
            }
            h.copy(where = where)
        }
    }

    /** Per-trip endpoints distilled from its track — start, end, and the farthest point (loop "via"). */
    private data class TripEnds(
        val start: AnalysisPointEntity?,
        val end: AnalysisPointEntity?,
        val via: AnalysisPointEntity?,
        val static: String,
    )

    suspend fun loadTripLabels(trips: List<TripEntity>): Map<Long, String> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        // Caps live geocoder calls for this one refresh; cached cells are free, so over a few
        // refreshes (and across runs, via SharedPreferences) the whole list fills in.
        val budget = GeoNamer.Budget(GEO_BUDGET_PER_REFRESH)
        // Pass 1: load each trip's track once and keep only its lightweight endpoints (the full point
        // arrays are released between iterations). Also build the static fallback label here.
        val ends = trips.associate { trip ->
            val points = dao.getAnalysisPoints(trip.id)
            trip.id to TripEnds(points.firstOrNull(), points.lastOrNull(), farthestFrom(points), TripLabeler.label(trip, points))
        }
        // Learn home + work from every trip's endpoints; persist so the single-trip title path can use them.
        val endpoints = ends.values.flatMap { listOfNotNull(it.start, it.end) }
            .map { HomeDetector.LatLon(it.lat, it.lon) }
        val home = HomeDetector.detect(endpoints)
        val work = HomeDetector.detectWork(endpoints, home)
        if (home != null) { saveHome(ctx, home); saveWork(ctx, work) }
        // Pass 2: compose each label (home/work-aware, with a loop "via"). Only unnamed trips geocode.
        val effHome = home ?: loadHome(ctx)
        val effWork = if (home != null) work else loadWork(ctx)
        trips.associate { trip ->
            val e = ends.getValue(trip.id)
            val label = if (trip.name.isNotBlank()) e.static
            else geocodedLabel(ctx, e, effHome, effWork, budget) ?: e.static
            trip.id to label
        }
    }

    /** Reverse-geocoded label for a trip's stored route (home/work-aware, loop-via), or null to fall back. */
    private fun geocodedLabel(
        ctx: Application,
        e: TripEnds,
        home: HomeDetector.LatLon?,
        work: HomeDetector.LatLon?,
        budget: GeoNamer.Budget,
    ): String? {
        val start = e.start ?: return null
        val end = e.end ?: return null
        return GeoNamer.nameTrip(
            ctx, start.lat, start.lon, end.lat, end.lon, budget, home, e.via?.lat, e.via?.lon, work
        )
    }

    suspend fun loadAnalysis(id: Long): TripAnalysis = withContext(Dispatchers.IO) {
        val trip = dao.getTrip(id)
        val persistedPoints = dao.getAnalysisPoints(id)
        if (persistedPoints.isNotEmpty() && trip != null) {
            val persistedEvents = dao.getDriveEvents(id)
            return@withContext persistedAnalysis(trip, persistedPoints, persistedEvents)
        }

        val locs = dao.getLocations(id)
        val motions = dao.getMotions(id)
        val analysis = TripAnalyzer.analyze(locs, motions)
        persistAnalysis(id, analysis)
        analysis
    }

    /**
     * Re-run analysis on a past trip's stored raw samples with the current detector, then reload.
     * Used to validate detector changes (e.g. Rev D) against past labeled drives without re-driving.
     * Returns true if raw samples were still present and re-analysis ran.
     */
    suspend fun reanalyzeTrip(id: Long): Boolean = withContext(Dispatchers.IO) {
        TripFinalizer.reanalyzeTrip(dao, id) != null
    }

    fun renameTrip(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.renameTrip(id, name.trim()) }
    }

    /** Manual drive/non-drive override: true = drive, false = walk/non-drive, null = back to auto. */
    suspend fun setTripIsDrive(id: Long, isDrive: Boolean?) = withContext(Dispatchers.IO) {
        dao.setUserIsDrive(id, isDrive)
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteTripWithData(id)
        }
    }

    /** Delete several trips (multi-select on the Past Trips screen), each with all its raw + derived rows. */
    fun deleteTrips(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { dao.deleteTripWithData(it) }
        }
    }

    /** Clears only sample/demo trips — real recorded trips are never touched. */
    fun clearSampleTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSampleLocations()
            dao.deleteSampleMotions()
            dao.deleteSampleAnalysisPoints()
            dao.deleteSampleDriveEvents()
            dao.deleteSampleTrips()
            CloudState.set { it.copy(lastMessage = "Cleared sample trips. Your real trips are untouched.") }
        }
    }

    fun loadDemoData() {
        viewModelScope.launch(Dispatchers.IO) {
            SampleData.resetWithDemoTrips(getApplication(), dao)
            val warning = SampleData.lastRouteWarning()
            val message = warning ?: "Loaded 30 days of sample trips on real roads."
            CloudState.set { it.copy(lastMessage = message) }
        }
    }

    private suspend fun persistAnalysis(tripId: Long, analysis: TripAnalysis) {
        val sampled = samplePoints(analysis.points)
        if (sampled.isNotEmpty()) {
            dao.deleteAnalysisPoints(tripId)
            dao.insertAnalysisPoints(sampled.map { it.toEntity(tripId) })
        }
        val allEvents = analysis.events + analysis.fusedEvents
        if (allEvents.isNotEmpty()) {
            dao.deleteDriveEvents(tripId)
            dao.insertDriveEvents(allEvents.map { it.toEntity(tripId) })
        }
    }

    private fun persistedAnalysis(
        trip: TripEntity,
        points: List<AnalysisPointEntity>,
        events: List<DriveEventEntity>
    ): TripAnalysis {
        val metricPoints = points.size
        val metrics = DriveMetrics(
            distanceM = trip.distanceM,
            durationS = trip.durationS,
            movingS = trip.movingS,
            idleS = trip.idleS,
            maxSpeedMps = trip.maxSpeedMps,
            avgMovingSpeedMps = trip.avgMovingSpeedMps,
            maxAccelMps2 = trip.maxAccelMps2,
            maxBrakeMps2 = trip.maxBrakeMps2,
            maxLateralMps2 = trip.maxLateralMps2,
            peakGForce = trip.peakGForce,
            maxHorizGForce = trip.maxHorizGForce,
            hardAccelCount = trip.hardAccelCount,
            hardBrakeCount = trip.hardBrakeCount,
            hardCornerCount = trip.hardCornerCount,
            smoothness = trip.smoothness,
            // Raw fix count is the recorded GPS total; usedFixes is what survived to stored points.
            rawFixes = trip.locationSampleCount,
            usedFixes = metricPoints,
            motionBrakeCount = trip.motionBrakeCount,
            motionAccelCount = trip.motionAccelCount,
            motionTurnCount = trip.motionTurnCount,
            fusedConfidence = trip.fusedConfidence
        )
        val allEvents = events.mapNotNull { e ->
            val type = runCatching { EventType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
            DriveEvent(tMs = e.t, type = type, magnitude = e.magnitude, source = e.source, confidence = e.confidence)
        }
        return TripAnalysis(
            metrics = metrics,
            points = points.map {
                TrackPoint(
                    tMs = it.t,
                    lat = it.lat,
                    lon = it.lon,
                    speedKmh = it.speedKmh,
                    longAccel = it.longAccel,
                    latAccel = it.latAccel,
                    speedLimitKmh = it.speedLimitKmh
                )
            },
            events = allEvents.filter { it.source != "fused" },
            fusedEvents = allEvents.filter { it.source == "fused" }
        )
    }

    private fun samplePoints(points: List<TrackPoint>, minGapMs: Long = 1000L): List<TrackPoint> {
        if (points.size <= 2) return points
        val out = ArrayList<TrackPoint>()
        var lastT = Long.MIN_VALUE
        points.forEach { point ->
            if (out.isEmpty() || point.tMs - lastT >= minGapMs) {
                out += point
                lastT = point.tMs
            }
        }
        if (out.lastOrNull()?.tMs != points.last().tMs) out += points.last()
        return out
    }

    private fun TrackPoint.toEntity(tripId: Long): AnalysisPointEntity =
        AnalysisPointEntity(
            tripId = tripId,
            t = tMs,
            lat = lat,
            lon = lon,
            speedKmh = speedKmh,
            longAccel = longAccel,
            latAccel = latAccel,
            speedLimitKmh = speedLimitKmh
        )

    private fun DriveEvent.toEntity(tripId: Long): DriveEventEntity =
        DriveEventEntity(
            tripId = tripId,
            t = tMs,
            type = type.name,
            magnitude = magnitude,
            source = source,
            confidence = confidence
        )
}
