package com.cartrip.analyzer.analysis

/**
 * Turns per-trip GNSS capture aggregates (satellites used in fix, carrier-to-noise density) into a
 * coarse signal-quality level. Pure and testable. Used to explain trip data quality and to downweight
 * route/event confidence when the sky view was poor (urban canyon, tunnels) — not to position.
 *
 * C/N0 (dB-Hz) rule of thumb: >=35 strong, ~25-30 usable, <20 marginal. A clean open-sky fix uses
 * 8-15+ satellites; urban canyons drop both the count and C/N0.
 */
object GnssQuality {
    enum class Level(val label: String) {
        STRONG("Strong"), MODERATE("Moderate"), WEAK("Weak"), UNKNOWN("Unknown")
    }

    fun level(avgSatsUsed: Double, avgCn0: Double, sampleCount: Int): Level {
        if (sampleCount < 3 || avgSatsUsed <= 0.0) return Level.UNKNOWN
        return when {
            avgSatsUsed >= 8.0 && avgCn0 >= 30.0 -> Level.STRONG
            avgSatsUsed >= 5.0 && avgCn0 >= 22.0 -> Level.MODERATE
            else -> Level.WEAK
        }
    }

    /** Compact one-liner for the data-quality detail row, or null when there's no GNSS data. */
    fun summary(avgSatsUsed: Double, avgCn0: Double, l5Seen: Boolean, sampleCount: Int): String? {
        if (level(avgSatsUsed, avgCn0, sampleCount) == Level.UNKNOWN) return null
        return buildString {
            append("GNSS ${avgSatsUsed.toInt()} sats")
            append(" ${avgCn0.toInt()} dB-Hz")
            if (l5Seen) append(" · L5")
        }
    }
}
