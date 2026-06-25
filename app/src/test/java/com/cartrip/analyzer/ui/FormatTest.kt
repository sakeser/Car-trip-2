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
}
