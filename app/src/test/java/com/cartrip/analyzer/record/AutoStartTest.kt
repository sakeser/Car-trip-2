package com.cartrip.analyzer.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoStartTest {

    /** The drive begins at the last rest sample before the car sustainedly pulls away. */
    @Test fun placesStartAtLastStationaryBeforeSustainedDeparture() {
        val samples = listOf(
            0L to 0.0,         // parked (trigger fired: warm-up / seatbelt)
            1000L to 0.0,
            2000L to 0.3,      // idle creep / GPS noise, still parked
            3000L to 0.5,      // <-- last rest sample before motion (true start, kept as ZUPT anchor)
            4000L to 2.0,      // 7.2 km/h, pulling away ...
            5000L to 6.0,      // ... and it sticks (sustained) ...
            6000L to 9.0,
            7000L to 12.0
        )
        assertEquals(3000L, AutoStart.retrospectiveStartTime(samples))
    }

    /** A brief parking-lot creep that then pauses must NOT be taken as the start — the real, sustained
     *  pull-away comes later. (Field case: trip 1169 crept ~1.2 m/s for 2 s, stopped, departed at ~16 s.) */
    @Test fun ignoresBriefCreepThenPause() {
        val samples = listOf(
            0L to 0.0,
            1000L to 0.0,
            2000L to 1.3,     // creep (above 4 km/h) ...
            3000L to 1.2,     // ... but only ~2 s ...
            4000L to 0.1,     // ... then a full stop -> not a real departure
            5000L to 0.1,
            6000L to 0.1,
            7000L to 0.2,     // <-- last rest before the REAL pull-away
            8000L to 3.0,     // sustained departure begins here ...
            9000L to 6.0,
            10000L to 9.0,
            11000L to 11.0
        )
        assertEquals(7000L, AutoStart.retrospectiveStartTime(samples))
    }

    /** A single jitter spike (one above-threshold sample, no sustain) must not be taken as the start. */
    @Test fun ignoresSingleJitterSpike() {
        val samples = listOf(
            0L to 0.0,
            1000L to 0.1,
            2000L to 8.0,     // lone cold-fix jitter spike
            3000L to 0.1,     // immediately back to stopped
            4000L to 0.1,
            5000L to 0.2,     // <-- last rest before the real departure
            6000L to 3.0,
            7000L to 6.0,
            8000L to 9.0,
            9000L to 12.0
        )
        assertEquals(5000L, AutoStart.retrospectiveStartTime(samples))
    }

    /** A long parked warm-up prefix is fully trimmed up to the last rest before the sustained pull-away. */
    @Test fun ignoresLongParkedPrefix() {
        val s = ArrayList<Pair<Long, Double>>()
        var t = 0L
        while (t < 120_000L) { s += t to 0.0; t += 1000L }  // 2 min parked, engine on
        s += 120_000L to 0.4    // still settled here <-- true start
        var v = 3.0
        for (k in 1..6) { s += (120_000L + k * 1000L) to v; v += 2.0 }  // sustained pull-away
        assertEquals(120_000L, AutoStart.retrospectiveStartTime(s))
    }

    /** Already moving (sustained) from the first captured sample → no parked prefix to trim. */
    @Test fun nullWhenAlreadyMovingAtFirstSample() {
        val samples = listOf(0L to 12.0, 1000L to 13.0, 2000L to 8.0, 3000L to 10.0, 4000L to 11.0)
        assertNull(AutoStart.retrospectiveStartTime(samples))
    }

    /** Never moves (a non-drive) → don't trim; the too-short discard handles deleting it. */
    @Test fun nullWhenNoMovementAtAll() {
        val samples = listOf(0L to 0.0, 1000L to 0.3, 2000L to 0.5, 3000L to 0.2)
        assertNull(AutoStart.retrospectiveStartTime(samples))
    }

    /** Motion that never sustains long enough (only creeps that keep pausing) → don't trim. */
    @Test fun nullWhenDepartureNeverSustains() {
        val samples = listOf(
            0L to 0.0, 1000L to 2.0, 2000L to 0.1,   // creep, stop
            3000L to 0.0, 4000L to 2.0, 5000L to 0.1 // creep, stop — never sustains 3 s
        )
        assertNull(AutoStart.retrospectiveStartTime(samples))
    }

    @Test fun emptyInputIsNull() {
        assertNull(AutoStart.retrospectiveStartTime(emptyList()))
    }
}
