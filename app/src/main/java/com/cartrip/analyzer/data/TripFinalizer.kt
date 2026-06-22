package com.cartrip.analyzer.data

import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.DriveMetrics
import com.cartrip.analyzer.analysis.EventType
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.analysis.TripAnalyzer
import kotlin.math.max

object TripFinalizer {
    private const val GPS_STALE_MS = 2L * 60L * 1000L
    // A live recording checkpoints every ~2s; if a RECORDING trip's last heartbeat is more recent
    // than this, it is still in progress and must NOT be recovered out from under the service.
    private const val ACTIVE_CHECKPOINT_MS = 60L * 1000L

    data class Result(
        val trip: TripEntity,
        val analysis: TripAnalysis
    )

    suspend fun finalizeTrip(
        dao: TripDao,
        tripId: Long,
        endWall: Long,
        requestedStatus: String,
        requestedReason: String
    ): Result? {
        val trip = dao.getTrip(tripId) ?: return null
        val locs = dao.getLocations(tripId)
        val motions = dao.getMotions(tripId)
        val analysis = TripAnalyzer.analyze(locs, motions)
        val finalQuality = qualityFor(trip, locs.size, endWall, requestedStatus, requestedReason)
        // The analyzer is authoritative for a clean completed trip; only fall back to merging the
        // (noisier, un-smoothed) live checkpoint when the trip is partial and analysis may be truncated.
        val metrics = if (TripStatus.isPartial(finalQuality.status)) {
            analysis.metrics.mergeWithCheckpoint(trip, endWall, finalQuality.status)
        } else {
            analysis.metrics
        }
        val updated = trip.copy(
            endTime = endWall,
            distanceM = metrics.distanceM,
            durationS = metrics.durationS,
            movingS = metrics.movingS,
            idleS = metrics.idleS,
            maxSpeedMps = metrics.maxSpeedMps,
            avgMovingSpeedMps = metrics.avgMovingSpeedMps,
            maxAccelMps2 = metrics.maxAccelMps2,
            maxBrakeMps2 = metrics.maxBrakeMps2,
            maxLateralMps2 = metrics.maxLateralMps2,
            peakGForce = metrics.peakGForce,
            hardAccelCount = metrics.hardAccelCount,
            hardBrakeCount = metrics.hardBrakeCount,
            hardCornerCount = metrics.hardCornerCount,
            smoothness = metrics.smoothness,
            analyzed = true,
            status = finalQuality.status,
            endReason = finalQuality.reason,
            lastCheckpointAt = endWall,
            locationSampleCount = locs.size,
            motionSampleCount = motions.size,
            hardBrakePct = metrics.hardBrakePct,
            aggressiveTurnPct = metrics.aggressiveTurnPct,
            hardAccelPct = metrics.hardAccelPct,
            maxJerk = metrics.maxJerk,
            jerkyPct = metrics.jerkyPct,
            roughRoadPct = metrics.roughRoadPct,
            potholeCount = metrics.potholeCount,
            harshStopCount = metrics.harshStopCount
        )
        val pointEntities = samplePoints(analysis.points).map { it.toEntity(tripId) }
        val eventEntities = analysis.events.map { it.toEntity(tripId) }
        dao.finalizeTripTx(updated, pointEntities, eventEntities)
        return Result(updated, analysis.copy(metrics = metrics))
    }

    suspend fun recoverInterruptedTrips(dao: TripDao, now: Long = System.currentTimeMillis()): Int {
        val interrupted = dao.getTripsWithStatus(TripStatus.RECORDING)
        var repaired = 0
        interrupted.forEach { trip ->
            val lastBeat = maxOf(trip.lastCheckpointAt, trip.lastLocationAt, trip.lastMotionAt)
            // Skip a recording that is still actively checkpointing — it's live, not interrupted.
            if (lastBeat > 0L && now - lastBeat < ACTIVE_CHECKPOINT_MS) return@forEach
            val endWall = lastBeat.takeIf { it > 0L } ?: now
            if (finalizeTrip(
                    dao = dao,
                    tripId = trip.id,
                    endWall = endWall,
                    requestedStatus = TripStatus.PARTIAL,
                    requestedReason = TripEndReason.APP_RECOVERY
                ) != null
            ) {
                repaired++
            }
        }
        return repaired
    }

    /** Rebuild a [TripAnalysis] from already-persisted points/events — for re-export and sync. */
    suspend fun storedAnalysis(dao: TripDao, tripId: Long): TripAnalysis? {
        val trip = dao.getTrip(tripId) ?: return null
        val pts = dao.getAnalysisPoints(tripId)
        val evs = dao.getDriveEvents(tripId)
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
            rawFixes = trip.locationSampleCount,
            usedFixes = pts.size
        )
        val points = pts.map {
            TrackPoint(it.t, it.lat, it.lon, it.speedKmh, it.longAccel, it.latAccel, it.speedLimitKmh)
        }
        val events = evs.mapNotNull { e ->
            runCatching { EventType.valueOf(e.type) }.getOrNull()?.let { DriveEvent(e.t, it, e.magnitude) }
        }
        return TripAnalysis(metrics, points, events)
    }

    private data class Quality(val status: String, val reason: String)

    private fun qualityFor(
        trip: TripEntity,
        locationCount: Int,
        endWall: Long,
        requestedStatus: String,
        requestedReason: String
    ): Quality {
        if (requestedStatus == TripStatus.PARTIAL) {
            return Quality(TripStatus.PARTIAL, requestedReason)
        }
        if (locationCount < 2) {
            return Quality(TripStatus.PARTIAL, TripEndReason.NO_GPS_TRACK)
        }
        val lastLocationAt = trip.lastLocationAt
        if (lastLocationAt > 0L && endWall - lastLocationAt >= GPS_STALE_MS) {
            return Quality(TripStatus.PARTIAL, TripEndReason.GPS_SIGNAL_LOST)
        }
        return Quality(TripStatus.COMPLETED, requestedReason)
    }

    private fun com.cartrip.analyzer.analysis.DriveMetrics.mergeWithCheckpoint(
        trip: TripEntity,
        endWall: Long,
        status: String
    ): com.cartrip.analyzer.analysis.DriveMetrics {
        val wallDurationS = ((endWall - trip.startTime) / 1000.0).coerceAtLeast(0.0)
        val finalDurationS = if (TripStatus.isPartial(status)) max(durationS, wallDurationS) else durationS
        val extraIdleS = (finalDurationS - durationS).coerceAtLeast(0.0)
        return copy(
            distanceM = max(distanceM, trip.distanceM),
            durationS = finalDurationS,
            idleS = idleS + extraIdleS,
            maxSpeedMps = max(maxSpeedMps, trip.maxSpeedMps),
            hardAccelCount = max(hardAccelCount, trip.hardAccelCount),
            hardBrakeCount = max(hardBrakeCount, trip.hardBrakeCount),
            hardCornerCount = max(hardCornerCount, trip.hardCornerCount)
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
