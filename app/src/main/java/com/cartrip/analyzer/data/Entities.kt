package com.cartrip.analyzer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceM: Double = 0.0,
    val durationS: Double = 0.0,
    val movingS: Double = 0.0,
    val idleS: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgMovingSpeedMps: Double = 0.0,
    val maxAccelMps2: Double = 0.0,
    val maxBrakeMps2: Double = 0.0,
    val maxLateralMps2: Double = 0.0,
    val peakGForce: Double = 0.0,
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardCornerCount: Int = 0,
    val smoothness: Int = 100,
    val analyzed: Boolean = false,
    // Google Routes API estimate for this trip's start->end, for actual-vs-estimate comparison.
    // etaSource: "live" (snapshot at trip end), "typical" (modeled for this time of day), or "".
    val googleEtaTrafficS: Double = 0.0,
    val googleEtaFreeFlowS: Double = 0.0,
    val etaSource: String = "",
    val etaFetchedAt: Long = 0L
)

@Entity(tableName = "locations", indices = [Index("tripId")])
data class LocationSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val t: Long,
    val lat: Double,
    val lon: Double,
    val speed: Double,
    val bearing: Double,
    val accuracy: Double
)

@Entity(tableName = "motions", indices = [Index("tripId")])
data class MotionSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val t: Long,
    val ax: Double,
    val ay: Double,
    val az: Double,
    val gx: Double,
    val gy: Double,
    val gz: Double
)

@Entity(tableName = "analysis_points", indices = [Index("tripId")])
data class AnalysisPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val t: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val longAccel: Double,
    val latAccel: Double
)

@Entity(tableName = "drive_events", indices = [Index("tripId")])
data class DriveEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val t: Long,
    val type: String,
    val magnitude: Double
)
