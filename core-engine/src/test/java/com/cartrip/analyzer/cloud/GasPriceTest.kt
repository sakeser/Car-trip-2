package com.cartrip.analyzer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure parse of the Ontario weekly fuel CSV → latest Toronto regular price ($/L). */
class GasPriceTest {

    private val header = "Date,Ottawa,Toronto West/Ouest,Toronto East/Est,Ontario Average/Moyenne provinciale,Fuel Type,Type de carburant"

    @Test fun picksLatestWeekTorontoRegularAverage() {
        val csv = listOf(
            header,
            "2026-06-15,150,160.0,164.0,158,Regular Unleaded Gasoline,Essence",
            "2026-06-22,150,158.0,162.0,156,Regular Unleaded Gasoline,Essence",   // latest -> (158+162)/2=160 c
            "2026-06-22,99,99.9,99.9,99,Auto Propane,Propane",                     // ignored (not regular)
        ).joinToString("\n")
        assertEquals(1.60, GasPrice.parseLatestTorontoRegular(csv)!!, 1e-9)
    }

    @Test fun usesOneSideWhenOtherIsZero() {
        val csv = "$header\n2026-06-22,150,0,162.0,156,Regular Unleaded Gasoline,Essence"
        assertEquals(1.62, GasPrice.parseLatestTorontoRegular(csv)!!, 1e-9)
    }

    @Test fun outOfRangeRejected() {
        val csv = "$header\n2026-06-22,150,9000,9000,156,Regular Unleaded Gasoline,Essence"
        assertNull(GasPrice.parseLatestTorontoRegular(csv))
    }

    @Test fun missingColumnsOrNoRegularYieldsNull() {
        assertNull(GasPrice.parseLatestTorontoRegular("Date,Foo,Bar\n2026-06-22,1,2"))
        assertNull(GasPrice.parseLatestTorontoRegular("$header\n2026-06-22,150,160,164,158,Diesel,Diesel"))
        assertNull(GasPrice.parseLatestTorontoRegular(""))
    }
}
