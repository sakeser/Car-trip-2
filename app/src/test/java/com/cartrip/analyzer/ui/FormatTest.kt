package com.cartrip.analyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun durationFloorMinRoundsDown() {
        assertEquals("<1 min", Format.durationFloorMin(0.0))
        assertEquals("<1 min", Format.durationFloorMin(59.9))
        assertEquals("1 min", Format.durationFloorMin(60.0))
        assertEquals("3 min", Format.durationFloorMin(227.0)) // 3m47s -> 3 min
        assertEquals("60 min", Format.durationFloorMin(3600.0))
    }

    @Test fun avgSpeedKmhTwoDecimals() {
        // 1.321 m/s ~= 4.76 km/h (a real walk: trip 1173 = 1418 m / 1073 s moving)
        assertEquals("4.76 km/h", Format.avgSpeedKmh(1.3215))
        assertEquals("—", Format.avgSpeedKmh(0.0))   // unknown / no moving time
        assertEquals("—", Format.avgSpeedKmh(-1.0))
        assertEquals("18.00 km/h", Format.avgSpeedKmh(5.0))
    }
}
