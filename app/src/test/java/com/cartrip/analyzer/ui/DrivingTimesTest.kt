package com.cartrip.analyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DrivingTimesTest {
    @Before fun fixTimeZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    private fun at(hour: Int, minute: Int = 0): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2026, 5, 24, hour, minute, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test fun dayparts() {
        assertEquals(DrivingTimes.Daypart.MORNING, DrivingTimes.daypartOf(7))
        assertEquals(DrivingTimes.Daypart.MIDDAY, DrivingTimes.daypartOf(13))
        assertEquals(DrivingTimes.Daypart.EVENING, DrivingTimes.daypartOf(18))
        assertEquals(DrivingTimes.Daypart.NIGHT, DrivingTimes.daypartOf(23))
        assertEquals(DrivingTimes.Daypart.NIGHT, DrivingTimes.daypartOf(3))
    }

    @Test fun summarizeCountsAndAverages() {
        val entries = listOf(
            DrivingTimes.Entry(at(8), 90, 5.0),    // morning
            DrivingTimes.Entry(at(9), 100, 3.0),   // morning
            DrivingTimes.Entry(at(18), 70, 10.0)   // evening
        )
        val buckets = DrivingTimes.summarize(entries).associateBy { it.part }
        assertEquals(2, buckets[DrivingTimes.Daypart.MORNING]!!.tripCount)
        assertEquals(95, buckets[DrivingTimes.Daypart.MORNING]!!.avgSafety)
        assertEquals(8.0, buckets[DrivingTimes.Daypart.MORNING]!!.totalKm, 1e-6)
        assertEquals(1, buckets[DrivingTimes.Daypart.EVENING]!!.tripCount)
        // Midday had no trips.
        assertEquals(0, buckets[DrivingTimes.Daypart.MIDDAY]!!.tripCount)
        assertNull(buckets[DrivingTimes.Daypart.MIDDAY]!!.avgSafety)
    }
}
