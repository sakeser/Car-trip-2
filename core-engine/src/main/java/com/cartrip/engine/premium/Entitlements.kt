package com.cartrip.engine.premium

/**
 * Feature-gating seam for the freemium model.
 *
 * Phase 0: a single source of truth for "is this premium capability available right now?".
 * There is no Play Billing and no paywall yet. [AlwaysPremiumEntitlements] grants everything so the
 * new UI can be built and dogfooded with all features visible. When the product value is proven, a
 * BillingEntitlements implementation (backed by Play Billing) replaces the binding. UI call sites that
 * ask Entitlements.has(...) do not change when that swap happens -- that is the whole point of the seam.
 */
interface Entitlements {
    fun has(feature: PremiumFeature): Boolean
}

/**
 * Capabilities that will eventually sit behind the subscription. These are stable code seams, not final
 * marketing copy, and the free/premium split is not locked until the analytics behind each are validated.
 */
enum class PremiumFeature {
    ADVANCED_INSIGHTS,
    LONG_TERM_TRENDS,
    AI_COACHING_EXPORT,
    TROUBLE_SPOTS_MAP,
    ENHANCED_PLACE_NAMES,
    CLOUD_SYNC_BACKUP,
    ADVANCED_ANALYTICS,
}

/**
 * Phase 0 default: everything unlocked. Lets the premium UI be built and validated before any billing
 * exists. Swap for a BillingEntitlements later without touching call sites.
 */
object AlwaysPremiumEntitlements : Entitlements {
    override fun has(feature: PremiumFeature): Boolean = true
}
