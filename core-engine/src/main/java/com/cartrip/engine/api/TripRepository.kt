package com.cartrip.engine.api

import android.content.Context
import com.cartrip.analyzer.analysis.DrivingIntelligence
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.analysis.TripKind
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.TripDao
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Read-side engine API for trips — the first facade seam (Phase 1 engine-API). Exposes [TripSummary]s, never
 * the Room entity, so the future `:ui-next` module depends only on `com.cartrip.engine.api`, not on
 * persistence internals.
 *
 * Intentionally tiny: only what the first `:ui-next` screen (a trip list) needs. Add methods one at a time as
 * screens require them; do **not** grow this into a god interface, and do **not** add write/recording/cloud
 * surfaces here — those are separate gateways for later.
 */
interface TripRepository {

    /** All trips as summaries (in the DAO's order), reactive. Cold [Flow] — the consumer does `.stateIn`. */
    fun observeTrips(): Flow<List<TripSummary>>

    /** One trip as a summary, or `null` if no trip has that id. */
    suspend fun getTrip(id: Long): TripSummary?

    /**
     * The trip's route as lat/lon [RoutePoint]s (from the persisted 1 Hz analysis track), with invalid /
     * zero-coordinate fixes dropped. Empty when the trip has no usable track (or the raw data was purged).
     */
    suspend fun getRoute(id: Long): List<RoutePoint>

    /**
     * The trip's speed timeline (from the persisted 1 Hz analysis track) as [TripTrackPoint]s, ordered by time,
     * with each point's offset measured from the trip's start. Empty when the trip is unknown or has no track.
     */
    suspend fun getTrack(id: Long): List<TripTrackPoint>

    /**
     * The trip's notable driving [TripEvent]s (hard brake/accel/corner, road events), ordered by time, offsets
     * measured from the trip's start (the same x-axis as [getTrack]). Empty when the trip is unknown or eventless.
     */
    suspend fun getEvents(id: Long): List<TripEvent>

    companion object {
        /** The default implementation, backed by the app's Room database. */
        fun create(context: Context): TripRepository =
            DefaultTripRepository(AppDatabase.get(context).tripDao())
    }
}

/**
 * Pure mapping from the persistence entity to the public summary (incl. deriving the Drive Stress score via the
 * pure `analysis.StressScore`, which reads only the entity's stored metrics). Kept as a separate `internal`
 * function so it is directly unit-testable without standing up a fake of the many-method Room [TripDao].
 */
internal fun TripEntity.toSummary(): TripSummary {
    val stress = StressScore.from(this)
    // Driving Intelligence without a vehicle profile → Smoothness + Demand + the conditional headline (the
    // Efficiency pillar needs a vehicle the engine-api mapper doesn't hold, so it's omitted here).
    val di = DrivingIntelligence.from(this)
    // "You vs traffic" is only meaningful for a real drive with a fetched with-traffic ETA (mirrors the legacy
    // TripDetail gate). Expose the raw seconds; the UI derives the verdict/colours.
    val hasEta = !TripKind.isLikelyNonDrive(this) && etaSource.isNotEmpty() && googleEtaTrafficS > 0.0
    return TripSummary(
        id = id,
        startEpochMs = startTime,
        endEpochMs = endTime,
        distanceMeters = distanceM,
        durationSeconds = durationS,
        stressScore = stress?.score,
        stressBand = stress?.band,
        smoothnessScore = di?.smoothness?.score,
        smoothnessBand = di?.smoothness?.label,
        driveQuality = di?.headline,
        etaTrafficSeconds = if (hasEta) googleEtaTrafficS else null,
        etaFreeFlowSeconds = if (hasEta) googleEtaFreeFlowS.takeIf { it > 0.0 } else null,
    )
}

/**
 * Pure: map persisted analysis points to [RoutePoint]s, dropping invalid / (0,0) coordinates (gap fills and
 * cold-fix rows). Directly unit-testable without a Room fake.
 */
internal fun List<AnalysisPointEntity>.toRoute(): List<RoutePoint> =
    mapNotNull { p ->
        if ((p.lat == 0.0 && p.lon == 0.0) || abs(p.lat) > 90.0 || abs(p.lon) > 180.0) null
        else RoutePoint(p.lat, p.lon)
    }

/**
 * Pure: map persisted analysis points to the speed-timeline [TripTrackPoint]s. Offsets are seconds since the
 * track's own first sample — NB the point `t` is a monotonic recording clock (e.g. elapsedRealtime), NOT epoch
 * ms, so the origin must come from the samples themselves, never from the trip's epoch `startTime`. Points are
 * sorted by time so the line is monotonic in x, and the origin is the earliest `t`. Unknown limits (`<= 0`)
 * surface as `null` rather than 0 km/h. Unlike [toRoute] this keeps every sample — a speed line needs no
 * valid coordinate.
 */
internal fun List<AnalysisPointEntity>.toTrack(): List<TripTrackPoint> {
    val sorted = sortedBy { it.t }
    val origin = sorted.firstOrNull()?.t ?: return emptyList()
    return sorted.mapNotNull { p ->
        // Defend the boundary: drop any non-finite speed (a corrupt sample would poison a chart's scaling with
        // NaN/Infinity), and treat a non-finite or <= 0 limit as "unknown" (null) rather than a real 0 km/h.
        if (!p.speedKmh.isFinite()) return@mapNotNull null
        TripTrackPoint(
            offsetSeconds = (((p.t - origin) / 1000L).toInt()).coerceAtLeast(0),
            speedKmh = p.speedKmh,
            speedLimitKmh = if (p.speedLimitKmh.isFinite() && p.speedLimitKmh > 0.0) p.speedLimitKmh else null,
            lat = p.lat,
            lon = p.lon,
        )
    }
}

/**
 * Pure: map persisted drive events to timeline [TripEvent]s, sorted by time. Offsets are seconds since
 * [originMs] — pass the SAME origin as [toTrack] (the track's first-sample `t`) so events land on the same
 * x-axis; events use the same monotonic recording clock as the analysis points. The raw detector `type` (an
 * `analysis.EventType.name`) is folded into the UI-stable [TripEventKind]; an unrecognized type maps to
 * [TripEventKind.OTHER] rather than being dropped.
 */
internal fun List<DriveEventEntity>.toEvents(originMs: Long): List<TripEvent> =
    sortedBy { it.t }.map { e ->
        TripEvent(
            offsetSeconds = (((e.t - originMs) / 1000L).toInt()).coerceAtLeast(0),
            kind = eventKindOf(e.type),
            magnitude = e.magnitude,
        )
    }

/** Fold a raw `analysis.EventType.name` into the UI-stable [TripEventKind]. Unknown -> [TripEventKind.OTHER]. */
internal fun eventKindOf(rawType: String): TripEventKind = when (rawType) {
    "BRAKE", "HARSH_STOP" -> TripEventKind.HARD_BRAKE
    "ACCEL" -> TripEventKind.HARD_ACCEL
    "CORNER", "SWERVE" -> TripEventKind.HARD_CORNER
    "POTHOLE" -> TripEventKind.ROUGH_ROAD
    else -> TripEventKind.OTHER
}

/** DAO-backed [TripRepository]; a thin adapter (map the entity stream/lookup to summaries + route). */
internal class DefaultTripRepository(private val dao: TripDao) : TripRepository {

    override fun observeTrips(): Flow<List<TripSummary>> =
        dao.observeTrips().map { trips -> trips.map(TripEntity::toSummary) }

    override suspend fun getTrip(id: Long): TripSummary? =
        dao.getTrip(id)?.toSummary()

    override suspend fun getRoute(id: Long): List<RoutePoint> =
        dao.getAnalysisPoints(id).toRoute()

    override suspend fun getTrack(id: Long): List<TripTrackPoint> =
        dao.getAnalysisPoints(id).toTrack()

    override suspend fun getEvents(id: Long): List<TripEvent> {
        // Align events to the track's clock origin (the first analysis sample), not the trip's epoch startTime.
        val origin = dao.getFirstAnalysisPointTime(id) ?: return emptyList()
        return dao.getDriveEvents(id).toEvents(origin)
    }
}
