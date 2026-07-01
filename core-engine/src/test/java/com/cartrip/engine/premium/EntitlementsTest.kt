package com.cartrip.engine.premium

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Phase 0 contract: the default binding unlocks every premium feature so the new UI can be
 * built and dogfooded before billing exists. When BillingEntitlements arrives, this test stays valid for
 * AlwaysPremiumEntitlements and new tests cover the gated implementation.
 */
class EntitlementsTest {

    @Test
    fun alwaysPremium_grantsEveryFeature() {
        val entitlements: Entitlements = AlwaysPremiumEntitlements
        for (feature in PremiumFeature.values()) {
            assertTrue("expected $feature to be unlocked under AlwaysPremiumEntitlements", entitlements.has(feature))
        }
    }
}
