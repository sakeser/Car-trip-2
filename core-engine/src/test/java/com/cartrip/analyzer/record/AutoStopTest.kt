package com.cartrip.analyzer.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoStopTest {

    /** The trip ends when the car first settles after its last real movement — not 6 min later. */
    @Test fun placesEndAtFirstStationaryAfterLastMovement() {
        val samples = listOf(
            0L to 15.0, 1000L to 14.0, 2000L to 8.0,
            3000L to 2.0,      // 7.2 km/h, still moving
            4000L to 1.5,      // 5.4 km/h, still moving
            5000L to 0.5,      // settled  <-- true end
            6000L to 0.2, 7000L to 0.0, 8000L to 0.0
        )
        assertEquals(5000L, AutoStop.retrospectiveEndTime(samples))
    }

    /** A long idle tail (the reason auto-stop fires) must not push the end time out. */
    @Test fun ignoresLongIdleTail() {
        val s = ArrayList<Pair<Long, Double>>()
        s += 0L to 20.0
        s += 10_000L to 5.0
        s += 11_000L to 1.5
        s += 12_000L to 0.4   // settled here
        var t = 13_000L
        while (t <= 360_000L) { s += t to 0.0; t += 1000L }
        assertEquals(12_000L, AutoStop.retrospectiveEndTime(s))
    }

    /** A red-light stop mid-drive must not be mistaken for the end if driving resumes. */
    @Test fun reMovementResetsStopSearch() {
        val samples = listOf(
            0L to 12.0,
            1000L to 0.3,   // brief stop (red light)
            2000L to 12.0,  // drives again
            3000L to 6.0,
            4000L to 1.2,
            5000L to 0.2    // final stop  <-- true end
        )
        assertEquals(5000L, AutoStop.retrospectiveEndTime(samples))
    }

    @Test fun fallsBackToLastMovingWhenNeverSettles() {
        val samples = listOf(0L to 10.0, 1000L to 9.0, 2000L to 8.0)
        assertEquals(2000L, AutoStop.retrospectiveEndTime(samples))
    }

    @Test fun nullWhenNoMovementAtAll() {
        val samples = listOf(0L to 0.0, 1000L to 0.3, 2000L to 0.5)
        assertNull(AutoStop.retrospectiveEndTime(samples))
    }

    @Test fun emptyInputIsNull() {
        assertNull(AutoStop.retrospectiveEndTime(emptyList()))
    }
}
