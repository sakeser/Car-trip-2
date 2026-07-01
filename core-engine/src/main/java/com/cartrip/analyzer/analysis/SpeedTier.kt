package com.cartrip.analyzer.analysis

/**
 * How far over the posted limit a point is, bucketed for route/replay colouring:
 *  - YELLOW: 0 < over < 10 km/h
 *  - RED:    over >= 10 km/h
 *
 * Aggregate speeding statistics keep their own noise tolerance; this is purely for colouring.
 */
object SpeedTier {
    enum class Tier { NONE, YELLOW, RED }

    const val RED_OVER_KMH = 10.0

    fun of(speedKmh: Double, limitKmh: Double): Tier {
        if (limitKmh <= 0.0) return Tier.NONE
        val over = speedKmh - limitKmh
        return when {
            over >= RED_OVER_KMH -> Tier.RED
            over > 0.0 -> Tier.YELLOW
            else -> Tier.NONE
        }
    }

    /** The more severe of two tiers (for a segment between two points). */
    fun worse(a: Tier, b: Tier): Tier = if (a.ordinal >= b.ordinal) a else b
}
