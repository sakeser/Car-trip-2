package com.cartrip.analyzer.cloud

import android.content.Context
import com.cartrip.analyzer.data.TripDao
import com.cartrip.analyzer.data.TripFinalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Idempotent Google Sheets sync for trips — including ones that ended via crash, kill, or
 * recovery (which the recording service can't sync at the time). A trip's [syncedAt] gate means
 * automatic passes never double-post, while a forced (user-initiated) sync always runs.
 */
object TripSync {

    /** Sync a single trip. [force] re-sends even if already synced (the manual button). */
    suspend fun syncOne(context: Context, dao: TripDao, tripId: Long, force: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trip = dao.getTrip(tripId)
                ?: return@withContext Result.failure(IllegalStateException("Trip not found"))
            if (!force && trip.syncedAt > 0L) return@withContext Result.success(Unit)
            if (GoogleAuth.lastAccount(context) == null) {
                return@withContext Result.failure(IllegalStateException("Not signed in"))
            }
            val analysis = TripFinalizer.storedAnalysis(dao, tripId)
                ?: return@withContext Result.failure(IllegalStateException("No analysis to sync"))

            val result = CloudSync.syncTrip(context, trip, analysis)
            dao.updateTrip(
                trip.copy(
                    syncedAt = if (result.isSuccess) System.currentTimeMillis() else trip.syncedAt,
                    syncError = if (result.isSuccess) "" else (result.exceptionOrNull()?.message ?: "Sync failed")
                )
            )
            result
        }

    /**
     * Sweep every finished-but-unsynced trip (recovered partials, prior failures). Runs on app
     * launch and after a normal stop. No-op unless signed in with auto-sync on. Returns count synced.
     */
    suspend fun syncPending(context: Context, dao: TripDao): Int = withContext(Dispatchers.IO) {
        if (!CloudPrefs.autoSync(context) || GoogleAuth.lastAccount(context) == null) return@withContext 0
        var synced = 0
        dao.getUnsyncedTrips().forEach { trip ->
            if (syncOne(context, dao, trip.id, force = false).isSuccess) synced++
        }
        synced
    }
}
