package com.cartrip.analyzer.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportRetentionTest {

    private val day = 24L * 60L * 60L * 1000L
    private fun e(name: String, ageDays: Long, now: Long) = ExportRetention.Entry(name, now - ageDays * day)

    @Test fun deletesOnlyFilesPastMaxAge() {
        val now = 1_000L * day
        val entries = listOf(e("fresh", 1, now), e("old", 31, now), e("ancient", 90, now))
        val del = ExportRetention.toDelete(entries, now, maxAgeMs = 30 * day, maxFiles = 100).map { it.name }
        assertEquals(setOf("old", "ancient"), del.toSet())
    }

    @Test fun capsCountKeepingNewest() {
        val now = 1_000L * day
        // 5 recent files (none past age), cap = 3 -> the 2 oldest are pruned.
        val entries = (1..5).map { e("f$it", it.toLong(), now) }  // f1 newest .. f5 oldest
        val del = ExportRetention.toDelete(entries, now, maxAgeMs = 365 * day, maxFiles = 3).map { it.name }
        assertEquals(setOf("f4", "f5"), del.toSet())
    }

    @Test fun ageAndCapCombine() {
        val now = 1_000L * day
        val entries = listOf(e("old", 40, now)) + (1..4).map { e("r$it", it.toLong(), now) }
        // old pruned by age; among the 4 recent, cap=2 prunes the 2 oldest (r3, r4).
        val del = ExportRetention.toDelete(entries, now, maxAgeMs = 30 * day, maxFiles = 2).map { it.name }
        assertEquals(setOf("old", "r3", "r4"), del.toSet())
    }

    @Test fun emptyAndUnderLimitDeleteNothing() {
        val now = 1_000L * day
        assertTrue(ExportRetention.toDelete(emptyList(), now).isEmpty())
        val few = (1..3).map { e("f$it", it.toLong(), now) }
        assertTrue(ExportRetention.toDelete(few, now, maxAgeMs = 365 * day, maxFiles = 50).isEmpty())
    }
}
