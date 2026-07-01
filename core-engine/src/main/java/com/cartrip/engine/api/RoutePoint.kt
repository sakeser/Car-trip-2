package com.cartrip.engine.api

/**
 * A minimal lat/lon point of a trip's route — an engine-API value type so UI consumers (e.g. `:ui-next`) can
 * draw a trip on a map without importing persistence (`AnalysisPointEntity`) or analysis (`TrackPoint`)
 * internals. Sourced from the persisted 1 Hz analysis track via [TripRepository.getRoute].
 */
data class RoutePoint(val lat: Double, val lon: Double)
