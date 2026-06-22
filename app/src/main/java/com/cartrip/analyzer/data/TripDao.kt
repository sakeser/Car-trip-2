package com.cartrip.analyzer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    /** Atomically replace a trip's row + its analysis points/events, so finalize can't half-apply. */
    @Transaction
    suspend fun finalizeTripTx(
        trip: TripEntity,
        points: List<AnalysisPointEntity>,
        events: List<DriveEventEntity>
    ) {
        updateTrip(trip)
        deleteAnalysisPoints(trip.id)
        deleteDriveEvents(trip.id)
        if (points.isNotEmpty()) insertAnalysisPoints(points)
        if (events.isNotEmpty()) insertDriveEvents(events)
    }

    @Insert
    suspend fun insertLocations(items: List<LocationSample>)

    @Insert
    suspend fun insertMotions(items: List<MotionSample>)

    @Insert
    suspend fun insertAnalysisPoints(items: List<AnalysisPointEntity>)

    @Insert
    suspend fun insertDriveEvents(items: List<DriveEventEntity>)

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    /**
     * Real (non-sample), finished, analyzed trips not yet synced — the auto-sync retry queue.
     * Sample/demo trips are excluded: they're synthetic and must not pollute the user's real Sheet
     * (and syncing dozens at once would blow the Sheets per-minute quota).
     */
    @Query(
        "SELECT * FROM trips WHERE analyzed = 1 AND endTime > 0 AND syncedAt = 0 AND isSample = 0 " +
            "ORDER BY startTime ASC"
    )
    suspend fun getUnsyncedTrips(): List<TripEntity>

    @Query(
        """
        SELECT * FROM trips
        WHERE status = :status OR endTime = 0
        ORDER BY startTime ASC
        """
    )
    suspend fun getTripsWithStatus(status: String): List<TripEntity>

    @Query(
        """
        SELECT * FROM trips
        WHERE status = :status OR endTime = 0
        ORDER BY startTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestTripWithStatus(status: String): TripEntity?

    @Query("SELECT * FROM locations WHERE tripId = :id ORDER BY t ASC")
    suspend fun getLocations(id: Long): List<LocationSample>

    @Query("SELECT * FROM motions WHERE tripId = :id ORDER BY t ASC")
    suspend fun getMotions(id: Long): List<MotionSample>

    @Query("SELECT * FROM analysis_points WHERE tripId = :id ORDER BY t ASC")
    suspend fun getAnalysisPoints(id: Long): List<AnalysisPointEntity>

    @Query(
        """
        SELECT analysis_points.* FROM analysis_points
        INNER JOIN trips ON trips.id = analysis_points.tripId
        WHERE trips.startTime >= :cutoff
        ORDER BY analysis_points.tripId ASC, analysis_points.t ASC
        """
    )
    suspend fun getAnalysisPointsSince(cutoff: Long): List<AnalysisPointEntity>

    @Query("SELECT * FROM drive_events WHERE tripId = :id ORDER BY t ASC")
    suspend fun getDriveEvents(id: Long): List<DriveEventEntity>

    @Query(
        """
        UPDATE trips SET
            durationS = :durationS,
            distanceM = :distanceM,
            maxSpeedMps = :maxSpeedMps,
            hardAccelCount = :hardAccelCount,
            hardBrakeCount = :hardBrakeCount,
            hardCornerCount = :hardCornerCount,
            lastCheckpointAt = :checkpointAt,
            lastLocationAt = :lastLocationAt,
            lastMotionAt = :lastMotionAt,
            locationSampleCount = :locationSampleCount,
            motionSampleCount = :motionSampleCount,
            gpsGapCount = :gpsGapCount
        WHERE id = :id
        """
    )
    suspend fun updateRecordingCheckpoint(
        id: Long,
        durationS: Double,
        distanceM: Double,
        maxSpeedMps: Double,
        hardAccelCount: Int,
        hardBrakeCount: Int,
        hardCornerCount: Int,
        checkpointAt: Long,
        lastLocationAt: Long,
        lastMotionAt: Long,
        locationSampleCount: Int,
        motionSampleCount: Int,
        gpsGapCount: Int
    )

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM locations WHERE tripId = :id")
    suspend fun deleteLocations(id: Long)

    @Query("DELETE FROM motions WHERE tripId = :id")
    suspend fun deleteMotions(id: Long)

    @Query("DELETE FROM locations WHERE tripId = :id AND t > :after")
    suspend fun deleteLocationsAfter(id: Long, after: Long)

    @Query("DELETE FROM motions WHERE tripId = :id AND t > :after")
    suspend fun deleteMotionsAfter(id: Long, after: Long)

    @Query("DELETE FROM analysis_points WHERE tripId = :id")
    suspend fun deleteAnalysisPoints(id: Long)

    @Query("DELETE FROM drive_events WHERE tripId = :id")
    suspend fun deleteDriveEvents(id: Long)

    @Query(
        """
        DELETE FROM locations
        WHERE tripId IN (
            SELECT trips.id FROM trips
            WHERE trips.analyzed = 1
                AND trips.endTime > 0
                AND trips.endTime < :cutoff
                AND EXISTS (
                    SELECT 1 FROM analysis_points
                    WHERE analysis_points.tripId = trips.id
                )
        )
        """
    )
    suspend fun deleteRawLocationsForCompletedTripsBefore(cutoff: Long)

    @Query(
        """
        DELETE FROM motions
        WHERE tripId IN (
            SELECT trips.id FROM trips
            WHERE trips.analyzed = 1
                AND trips.endTime > 0
                AND trips.endTime < :cutoff
                AND EXISTS (
                    SELECT 1 FROM analysis_points
                    WHERE analysis_points.tripId = trips.id
                )
        )
        """
    )
    suspend fun deleteRawMotionsForCompletedTripsBefore(cutoff: Long)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun tripCount(): Int

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()

    @Query("DELETE FROM motions")
    suspend fun deleteAllMotions()

    @Query("DELETE FROM analysis_points")
    suspend fun deleteAllAnalysisPoints()

    @Query("DELETE FROM drive_events")
    suspend fun deleteAllDriveEvents()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    // --- Sample-only deletes: "Load demo data" must never touch real recorded trips ---
    @Query("DELETE FROM locations WHERE tripId IN (SELECT id FROM trips WHERE isSample = 1)")
    suspend fun deleteSampleLocations()

    @Query("DELETE FROM motions WHERE tripId IN (SELECT id FROM trips WHERE isSample = 1)")
    suspend fun deleteSampleMotions()

    @Query("DELETE FROM analysis_points WHERE tripId IN (SELECT id FROM trips WHERE isSample = 1)")
    suspend fun deleteSampleAnalysisPoints()

    @Query("DELETE FROM drive_events WHERE tripId IN (SELECT id FROM trips WHERE isSample = 1)")
    suspend fun deleteSampleDriveEvents()

    @Query("DELETE FROM trips WHERE isSample = 1")
    suspend fun deleteSampleTrips()
}
