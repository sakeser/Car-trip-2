package com.cartrip.engine.api

import android.content.Context
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.TripDao
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    companion object {
        /** The default implementation, backed by the app's Room database. */
        fun create(context: Context): TripRepository =
            DefaultTripRepository(AppDatabase.get(context).tripDao())
    }
}

/**
 * Pure mapping from the persistence entity to the public summary. Kept as a separate `internal` function so it
 * is directly unit-testable without standing up a fake of the many-method Room [TripDao].
 */
internal fun TripEntity.toSummary(): TripSummary =
    TripSummary(
        id = id,
        startEpochMs = startTime,
        endEpochMs = endTime,
        distanceMeters = distanceM,
        durationSeconds = durationS,
    )

/** DAO-backed [TripRepository]; a thin adapter (map the entity stream/lookup to summaries). */
internal class DefaultTripRepository(private val dao: TripDao) : TripRepository {

    override fun observeTrips(): Flow<List<TripSummary>> =
        dao.observeTrips().map { trips -> trips.map(TripEntity::toSummary) }

    override suspend fun getTrip(id: Long): TripSummary? =
        dao.getTrip(id)?.toSummary()
}
