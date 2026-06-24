package com.cartrip.analyzer.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTierTest {
    @Test fun noneWhenNoLimitOrAtOrUnder() {
        assertEquals(SpeedTier.Tier.NONE, SpeedTier.of(100.0, 0.0))
        assertEquals(SpeedTier.Tier.NONE, SpeedTier.of(48.0, 50.0))
        assertEquals(SpeedTier.Tier.NONE, SpeedTier.of(50.0, 50.0))
    }

    @Test fun yellowBetweenZeroAndTenOver() {
        assertEquals(SpeedTier.Tier.YELLOW, SpeedTier.of(51.0, 50.0))
        assertEquals(SpeedTier.Tier.YELLOW, SpeedTier.of(59.9, 50.0))
    }

    @Test fun redAtTenOrMoreOver() {
        assertEquals(SpeedTier.Tier.RED, SpeedTier.of(60.0, 50.0))
        assertEquals(SpeedTier.Tier.RED, SpeedTier.of(125.0, 100.0))
    }

    @Test fun worsePicksHigherSeverity() {
        assertEquals(SpeedTier.Tier.RED, SpeedTier.worse(SpeedTier.Tier.YELLOW, SpeedTier.Tier.RED))
        assertEquals(SpeedTier.Tier.YELLOW, SpeedTier.worse(SpeedTier.Tier.NONE, SpeedTier.Tier.YELLOW))
    }
}
