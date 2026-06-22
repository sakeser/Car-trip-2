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
    // True horizontal spike (max g) — the hardest brief brake/turn that the p99 peakGForce washes out.
    val maxHorizGForce: Double = 0.0,
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
    val etaFetchedAt: Long = 0L,
    val status: String = TripStatus.COMPLETED,
    val endReason: String = "",
    val lastCheckpointAt: Long = 0L,
    val lastLocationAt: Long = 0L,
    val lastMotionAt: Long = 0L,
    val locationSampleCount: Int = 0,
    val motionSampleCount: Int = 0,
    val gpsGapCount: Int = 0,
    // Exposure-normalized driving factors (fraction of moving time over a g threshold), à la
    // Tesla's Safety Score: hard braking >0.30g, aggressive turning >0.40g, hard accel >0.30g.
    val hardBrakePct: Double = 0.0,
    val aggressiveTurnPct: Double = 0.0,
    val hardAccelPct: Double = 0.0,
    // Speeding vs OSM posted limits. speedingPct = fraction of covered moving time over the limit;
    // limitCoverage = fraction of the route we found a limit for (confidence in the speeding number).
    val speedingPct: Double = 0.0,
    val maxOverLimitKmh: Double = 0.0,
    val limitCoverage: Double = 0.0,
    // Jerk = rate of change of acceleration (abruptness). maxJerk in m/s^3; jerkyPct = fraction of
    // moving time the ride was jerky. A jerky stab feels worse than a smooth firm brake of equal g.
    val maxJerk: Double = 0.0,
    val jerkyPct: Double = 0.0,
    // Google Sheets sync state. syncedAt = success timestamp (0 = never synced, the retry gate);
    // syncError = last failure message for the UI. Recovered/partial trips sync on next app launch.
    val syncedAt: Long = 0L,
    val syncError: String = "",
    // True only for generated demo trips. "Load demo data" deletes sample trips only, never real ones.
    val isSample: Boolean = false,
    // Accelerometer-fusion road/ride metrics (need recorded gravity).
    val roughRoadPct: Double = 0.0,
    val potholeCount: Int = 0,
    val harshStopCount: Int = 0,
    // Parallel sensor-fused event detector counts (for comparison vs GPS; not scored yet).
    val motionBrakeCount: Int = 0,
    val motionAccelCount: Int = 0,
    val motionTurnCount: Int = 0,
    val fusedConfidence: Double = 0.0
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
    val gz: Double,
    // Gravity vector (device frame). Gives the "down" axis so a later pass can separate vertical
    // bumps from horizontal driving accel and project onto the vehicle's forward/lateral axes.
    val grx: Double = 0.0,
    val gry: Double = 0.0,
    val grz: Double = 0.0
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
    val latAccel: Double,
    // Posted speed limit (km/h) matched from OSM for this point; 0 = unknown / not looked up.
    val speedLimitKmh: Double = 0.0
)

@Entity(tableName = "drive_events", indices = [Index("tripId")])
data class DriveEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val t: Long,
    val type: String,
    val magnitude: Double,
    // Which detector found it: "gps", "motion" (pothole), or "fused" (Rev D sensor detector).
    val source: String = "gps",
    val confidence: Double = 1.0
)
