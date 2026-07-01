package com.cartrip.engine.api

import android.content.Context
import com.cartrip.analyzer.analysis.DrivingIntelligence
import com.cartrip.analyzer.analysis.StressScore
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.AppDatabase
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

/** DAO-backed [TripRepository]; a thin adapter (map the entity stream/lookup to summaries + route). */
internal class DefaultTripRepository(private val dao: TripDao) : TripRepository {

    override fun observeTrips(): Flow<List<TripSummary>> =
        dao.observeTrips().map { trips -> trips.map(TripEntity::toSummary) }

    override suspend fun getTrip(id: Long): TripSummary? =
        dao.getTrip(id)?.toSummary()

    override suspend fun getRoute(id: Long): List<RoutePoint> =
        dao.getAnalysisPoints(id).toRoute()
}
