package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEntity

/**
 * Heuristic: was this "trip" actually a non-drive (a walk / run / other) rather than a car drive?
 * A real drive almost always exceeds a walking top speed somewhere; a walk tops out ~5-7 km/h.
 *
 * Used to suppress the (driving) traffic comparison and keep non-drives out of drive-centric
 * Insights aggregates. From field data 2026-06-24 two walks (max ~5 km/h) were still given a ~4 min
 * *driving* ETA and a 0.65 g "peak" from gait. See memory walk-non-drive-finding.
 */
object TripKind {
    /** A real drive almost always crosses this top speed somewhere; a walk does not. */
    const val DRIVE_MIN_TOP_KMH = 12.0

    /** True when the top speed is positive but below a driving threshold. 0 (no GPS) -> treated as a drive. */
    fun isLikelyNonDrive(maxSpeedKmh: Double): Boolean =
        maxSpeedKmh in 0.1..DRIVE_MIN_TOP_KMH

    /**
     * A manual override wins over the auto heuristic: [userIsDrive] true -> drive, false -> non-drive,
     * null -> fall back to the top-speed guess. Lets the owner fix a mislabeled trip (a walk recorded as
     * a drive, or a slow crawl that was really a drive).
     */
    fun isLikelyNonDrive(maxSpeedKmh: Double, userIsDrive: Boolean?): Boolean =
        when (userIsDrive) {
            true -> false
            false -> true
            null -> isLikelyNonDrive(maxSpeedKmh)
        }

    fun isLikelyNonDrive(trip: TripEntity): Boolean =
        isLikelyNonDrive(trip.maxSpeedMps * 3.6, trip.userIsDrive)
}
