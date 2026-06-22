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
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.SampleData
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).tripDao()

    val trips: StateFlow<List<TripEntity>> =
        dao.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadTrip(id: Long): TripEntity? = withContext(Dispatchers.IO) { dao.getTrip(id) }

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

    suspend fun loadHeatmapPoints(days: Int): List<AnalysisPointEntity> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.getAnalysisPointsSince(cutoff)
    }

    suspend fun loadTripLabels(trips: List<TripEntity>): Map<Long, String> = withContext(Dispatchers.IO) {
        trips.associate { trip ->
            trip.id to TripLabeler.label(trip, dao.getAnalysisPoints(trip.id))
        }
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

    fun deleteTrip(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLocations(id)
            dao.deleteMotions(id)
            dao.deleteAnalysisPoints(id)
            dao.deleteDriveEvents(id)
            dao.deleteTrip(id)
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
        if (analysis.events.isNotEmpty()) {
            dao.deleteDriveEvents(tripId)
            dao.insertDriveEvents(analysis.events.map { it.toEntity(tripId) })
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
            hardAccelCount = trip.hardAccelCount,
            hardBrakeCount = trip.hardBrakeCount,
            hardCornerCount = trip.hardCornerCount,
            smoothness = trip.smoothness,
            rawFixes = metricPoints,
            usedFixes = metricPoints
        )
        return TripAnalysis(
            metrics = metrics,
            points = points.map {
                TrackPoint(
                    tMs = it.t,
                    lat = it.lat,
                    lon = it.lon,
                    speedKmh = it.speedKmh,
                    longAccel = it.longAccel,
                    latAccel = it.latAccel
                )
            },
            events = events.mapNotNull { e ->
                val type = runCatching { EventType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
                DriveEvent(tMs = e.t, type = type, magnitude = e.magnitude)
            }
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
            latAccel = latAccel
        )

    private fun DriveEvent.toEntity(tripId: Long): DriveEventEntity =
        DriveEventEntity(
            tripId = tripId,
            t = tMs,
            type = type.name,
            magnitude = magnitude
        )
}
