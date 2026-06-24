package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEntity

/**
 * Groups trips into recency buckets for the Past Trips list. Pure and testable; each trip lands in
 * exactly one bucket and buckets are returned in fixed order, newest-first within each.
 */
object TripBuckets {
    enum class Bucket(val label: String) {
        LAST_24H("Last 24 hours"),
        LAST_3D("Last 3 days"),
        LAST_7D("Last 7 days"),
        LAST_MONTH("Last month"),
        OLDER("Older")
    }

    private const val HOUR = 60L * 60L * 1000L
    private const val DAY = 24L * HOUR

    fun bucketOf(startTime: Long, now: Long): Bucket {
        val age = now - startTime
        return when {
            age < DAY -> Bucket.LAST_24H
            age < 3 * DAY -> Bucket.LAST_3D
            age < 7 * DAY -> Bucket.LAST_7D
            age < 30 * DAY -> Bucket.LAST_MONTH
            else -> Bucket.OLDER
        }
    }

    fun group(
        trips: List<TripEntity>,
        now: Long = System.currentTimeMillis()
    ): List<Pair<Bucket, List<TripEntity>>> {
        val byBucket = trips.groupBy { bucketOf(it.startTime, now) }
        return Bucket.values().mapNotNull { bucket ->
            byBucket[bucket]?.sortedByDescending { it.startTime }?.let { bucket to it }
        }
    }
}
