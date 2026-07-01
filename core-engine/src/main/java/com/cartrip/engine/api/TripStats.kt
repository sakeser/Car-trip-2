package com.cartrip.engine.api

/**
 * Raw, factual trip stats for a detail "at a glance" grid — plain measured quantities (speeds, moving/idle time,
 * hard-event counts), NOT scores. An engine-API value type so `:ui-next` can show them without importing the
 * Room entity. Speeds are km/h; times are seconds. All default 0 (an empty / ongoing trip reads as zeros).
 */
data class TripStats(
    val maxSpeedKmh: Double = 0.0,
    val avgMovingSpeedKmh: Double = 0.0,
    val movingSeconds: Double = 0.0,
    val idleSeconds: Double = 0.0,
    val hardBrakeCount: Int = 0,
    val hardAccelCount: Int = 0,
    val hardCornerCount: Int = 0,
)
