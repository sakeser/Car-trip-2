package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBucketsTest {
    private val now = 1_700_000_000_000L
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    private fun tripAt(start: Long) = TripEntity(startTime = start, endTime = start + 600_000L)

    @Test fun bucketBoundaries() {
        assertEquals(TripBuckets.Bucket.LAST_24H, TripBuckets.bucketOf(now - 2 * hour, now))
        assertEquals(TripBuckets.Bucket.LAST_3D, TripBuckets.bucketOf(now - 2 * day, now))
        assertEquals(TripBuckets.Bucket.LAST_7D, TripBuckets.bucketOf(now - 5 * day, now))
        assertEquals(TripBuckets.Bucket.LAST_MONTH, TripBuckets.bucketOf(now - 20 * day, now))
        assertEquals(TripBuckets.Bucket.OLDER, TripBuckets.bucketOf(now - 200 * day, now))
    }

    @Test fun eachTripInExactlyOneBucketAndOrdered() {
        val trips = listOf(
            tripAt(now - 1 * hour),    // 24h
            tripAt(now - 3 * hour),    // 24h
            tripAt(now - 2 * day),     // 3d
            tripAt(now - 10 * day),    // month
            tripAt(now - 400 * day)    // older
        )
        val grouped = TripBuckets.group(trips, now)
        // total preserved, no dupes
        assertEquals(trips.size, grouped.sumOf { it.second.size })
        // 24h bucket has 2, sorted newest-first
        val first = grouped.first()
        assertEquals(TripBuckets.Bucket.LAST_24H, first.first)
        assertEquals(2, first.second.size)
        assertTrue(first.second[0].startTime > first.second[1].startTime)
        // buckets in fixed order
        val order = grouped.map { it.first }
        assertEquals(order.sortedBy { it.ordinal }, order)
    }

    @Test fun emptyBucketsOmitted() {
        val grouped = TripBuckets.group(listOf(tripAt(now - 2 * hour)), now)
        assertEquals(1, grouped.size)
        assertEquals(TripBuckets.Bucket.LAST_24H, grouped.first().first)
    }
}
