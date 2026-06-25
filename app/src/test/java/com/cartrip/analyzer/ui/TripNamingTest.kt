package com.cartrip.analyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TripNamingTest {
    @Before fun fixTimeZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    private fun ts(year: Int, month0: Int, day: Int, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(year, month0, day, hour, minute, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test fun uniqueNamesUnchanged() {
        val r = TripNaming.disambiguate(
            listOf(
                TripNaming.Entry(1, "North York Loop", ts(2026, 5, 24, 10, 14)),
                TripNaming.Entry(2, "Scarborough Run", ts(2026, 5, 24, 16, 2))
            )
        )
        assertEquals("North York Loop", r[1])
        assertEquals("Scarborough Run", r[2])
    }

    @Test fun sameDayDuplicatesGetDistinctTimeSuffix() {
        val r = TripNaming.disambiguate(
            listOf(
                TripNaming.Entry(1, "North York Loop", ts(2026, 5, 24, 10, 14)),
                TripNaming.Entry(2, "North York Loop", ts(2026, 5, 24, 16, 2))
            )
        )
        assertTrue(r[1]!!.startsWith("North York Loop ("))
        assertTrue(r[2]!!.startsWith("North York Loop ("))
        assertNotEquals(r[1], r[2])
        // Same-day clash -> time only, no date comma.
        assertFalse(r[1]!!.contains(","))
        assertTrue(r[1]!!.contains("am") || r[1]!!.contains("pm"))
    }

    @Test fun crossDayDuplicatesGetDateAndTime() {
        val r = TripNaming.disambiguate(
            listOf(
                TripNaming.Entry(1, "North York Loop", ts(2026, 5, 24, 10, 14)),
                TripNaming.Entry(2, "North York Loop", ts(2026, 5, 25, 10, 14))
            )
        )
        assertTrue(r[1]!!.contains(","))
        assertTrue(r[1]!!.contains("Jun")) // month index 5 == June
        assertNotEquals(r[1], r[2])
    }
}
