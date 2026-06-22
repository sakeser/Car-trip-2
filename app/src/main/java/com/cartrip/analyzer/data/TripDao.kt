package com.cartrip.analyzer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

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
}
