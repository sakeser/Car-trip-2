package com.cartrip.analyzer.ui

import kotlin.math.ceil

/**
 * Shared bar/axis scaling so charts and progress bars across the app size consistently instead of each
 * one picking its own ad-hoc max. Two jobs:
 *  - [niceAxisMax]: round the data max UP to a tidy number with a little headroom, so the longest bar
 *    fills ~80-90% of the track (not edge-to-edge) and the scale reads in round units.
 *  - [fillFraction]: a value's fill against that axis, floored to a [minVisible] sliver so a small but
 *    non-zero value still shows a bar instead of vanishing.
 *
 * Pure (no Compose) so it is unit-testable. The tiers are tuned for minute / count axes (the app's bars
 * are trip durations and event counts). Extracted from the you-vs-traffic ETA axis so every bar shares
 * the same judgement.
 */
object BarScale {
    /**
     * A tidy round number >= [dataMax] * [headroom]. The step widens with magnitude (1 / 5 / 10 / 30) so
     * the axis reads in clean increments. A non-positive [dataMax] still returns a valid positive axis
     * (so an empty/zero dataset doesn't divide by zero downstream).
     */
    fun niceAxisMax(dataMax: Double, headroom: Double = 1.2): Double {
        val target = (dataMax * headroom).coerceAtLeast(1.0)
        val step = when {
            target <= 10 -> 1.0
            target <= 30 -> 5.0
            target <= 120 -> 10.0
            else -> 30.0
        }
        return ceil(target / step) * step
    }

    /**
     * [value]'s fill against [axisMax], in 0..1. A strictly-positive value is floored to [minVisible] so
     * a tiny bar is still visible; a non-positive value (or a non-positive [axisMax]) yields 0.
     */
    fun fillFraction(value: Double, axisMax: Double, minVisible: Float = 0.03f): Float {
        if (axisMax <= 0.0 || value <= 0.0) return 0f
        return (value / axisMax).toFloat().coerceIn(0f, 1f).coerceAtLeast(minVisible)
    }
}
