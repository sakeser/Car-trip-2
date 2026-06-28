package com.cartrip.analyzer.ui

import com.cartrip.analyzer.analysis.EventType
import kotlin.math.roundToInt

/**
 * Cross-trip recurring-event detection (roadmap item 9): as trips accumulate and routes overlap, find
 * places where the *same kind* of event happens on **multiple distinct trips** — a pothole/rough patch you
 * hit every commute, a turn you always take sharply, a regular hard-brake spot. Grows naturally with data.
 *
 * Pure and Android-free (grid clustering over tagged (tripId, kind, lat, lon) events) so it is unit-tested;
 * the caller loads events, maps them to a [kind], and renders the result. The **distinct-trip** count is
 * the guard: one eventful drive can't manufacture a hotspot — it must recur across trips.
 */
object EventHotspots {

    /** ~0.0005 deg latitude ≈ 55 m — a single intersection / road defect / turn. */
    private const val CELL = 0.0005
    /** A hotspot must recur on at least this many distinct trips. */
    const val MIN_TRIPS = 2

    /** Group the raw [EventType]s into the human "kind" shown to the user (swerve+corner = one turn). */
    fun kindOf(type: EventType): String = when (type) {
        EventType.BRAKE -> "Hard braking"
        EventType.ACCEL -> "Hard acceleration"
        EventType.CORNER, EventType.SWERVE -> "Sharp turn"
        EventType.POTHOLE -> "Rough spot"
        EventType.HARSH_STOP -> "Hard stop"
    }

    data class Ev(val tripId: Long, val kind: String, val lat: Double, val lon: Double)

    data class Hotspot(
        val kind: String,
        val lat: Double,
        val lon: Double,
        val trips: Int,   // distinct trips with this event-kind here
        val count: Int,   // total events
        val where: String = "",  // optional friendly location ("near Home"/"near Work"), set by the caller
    )

    private fun cell(v: Double) = (v / CELL).roundToInt()

    /**
     * Find recurring hotspots. Events are grouped by (~55 m cell, kind); a cell-kind becomes a hotspot when
     * it appears on >= [minTrips] distinct trips. Returned strongest-first (most trips, then most events).
     */
    fun find(events: List<Ev>, minTrips: Int = MIN_TRIPS): List<Hotspot> {
        if (events.isEmpty()) return emptyList()
        val byCell = events.groupBy { Triple(cell(it.lat), cell(it.lon), it.kind) }
        return byCell.mapNotNull { (key, items) ->
            val trips = items.map { it.tripId }.distinct().size
            if (trips < minTrips) return@mapNotNull null
            Hotspot(
                kind = key.third,
                lat = items.sumOf { it.lat } / items.size,
                lon = items.sumOf { it.lon } / items.size,
                trips = trips,
                count = items.size,
            )
        }.sortedWith(compareByDescending<Hotspot> { it.trips }.thenByDescending { it.count })
    }
}
