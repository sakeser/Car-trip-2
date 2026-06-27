package com.cartrip.analyzer.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure re-arm decision ([MotionRearmDetector]). Samples are fed as if from the accelerometer
 * while armed-but-not-recording; the detector fires only on sustained driving-level vibration.
 *
 * Defaults under test: vibThreshold=0.40, sustainMs=4000, cooldownMs=15000, EMA=0.94/0.06.
 */
class MotionRearmDetectorTest {

    private val dt = 20L // ~50 Hz, matching SENSOR_DELAY_GAME used by the watcher

    /** Feed [count] samples that alternate by [amp] on each axis-step so the sample-to-sample jerk ~= amp.
     *  Returns the (1-based) sample index that fired, or -1 if none did. */
    private fun feedAlternating(d: MotionRearmDetector, count: Int, amp: Double, startT: Long = 0L): Int {
        var t = startT
        for (i in 0 until count) {
            // Alternate sign so each step is a delta of `amp` on x; jerk magnitude ~= amp every sample.
            val x = if (i % 2 == 0) 0.0 else amp
            if (d.onSample(t, x, 0.0, 0.0)) return i + 1
            t += dt
        }
        return -1
    }

    @Test fun smoothIdleNeverFires() {
        val d = MotionRearmDetector()
        // Tiny jerk well under threshold for a long time (10 s) -> EMA stays low, never fires.
        assertEquals(-1, feedAlternating(d, count = 500, amp = 0.05))
        assertTrue(d.vibrationEma < 0.40)
    }

    @Test fun deadStillNeverFires() {
        val d = MotionRearmDetector()
        var t = 0L
        repeat(500) { if (d.onSample(t, 9.81, 0.0, 0.0)) throw AssertionError("fired on no motion"); t += dt }
        assertEquals(0.0, d.vibrationEma, 1e-9)
    }

    @Test fun sustainedDrivingVibrationFiresOnce() {
        val d = MotionRearmDetector()
        // Strong steady vibration: EMA climbs above 0.40 and, once sustained >=4 s, fires.
        val fired = feedAlternating(d, count = 1000, amp = 1.0)
        assertTrue("expected a fire on sustained vibration", fired > 0)
        assertTrue(d.vibrationEma >= 0.40)
    }

    @Test fun firesOnlyAfterSustainWindow() {
        val d = MotionRearmDetector()
        // The EMA must first climb past threshold, THEN hold for >=4 s. So the fire can't happen before
        // 4 s of samples (200 at 50 Hz) have elapsed past the threshold crossing.
        val fired = feedAlternating(d, count = 1000, amp = 1.0)
        // 4 s / 20 ms = 200 samples of sustain, plus the climb to threshold -> comfortably after 200.
        assertTrue("fired too early (no sustain): sample=$fired", fired > 200)
    }

    @Test fun briefJostleDoesNotFire() {
        val d = MotionRearmDetector()
        // A 1 s burst of strong vibration (picking up the phone), then still -> never sustains 4 s.
        assertEquals(-1, feedAlternating(d, count = 50, amp = 1.5))
        // Then settle quietly; the brief burst must not have armed a fire.
        assertEquals(-1, feedAlternating(d, count = 200, amp = 0.02, startT = 50 * dt))
    }

    @Test fun motionThatDipsBelowThresholdRestartsSustain() {
        val d = MotionRearmDetector()
        var t = 0L
        // Climb to just above threshold for ~3 s (not yet 4 s), then drop below -> sustain resets.
        repeat(200) { i -> d.onSample(t, if (i % 2 == 0) 0.0 else 0.6, 0.0, 0.0); t += dt }
        // Drop to near-zero motion long enough for the EMA to fall back under threshold.
        repeat(400) { d.onSample(t, 0.0, 0.0, 0.0); t += dt }
        assertTrue("EMA should have decayed below threshold", d.vibrationEma < 0.40)
    }

    @Test fun cooldownPreventsImmediateRefire() {
        val d = MotionRearmDetector()
        val first = feedAlternating(d, count = 1000, amp = 1.0)
        assertTrue(first > 0)
        val firstT = (first - 1L) * dt
        // Keep shaking hard right after the fire: within the 15 s cooldown it must NOT fire again.
        var t = firstT + dt
        var refired = false
        val cooldownEnd = firstT + 15_000L
        while (t < cooldownEnd) {
            val i = ((t - firstT) / dt).toInt()
            if (d.onSample(t, if (i % 2 == 0) 0.0 else 1.0, 0.0, 0.0)) { refired = true; break }
            t += dt
        }
        assertFalse("must not re-fire inside the cooldown window", refired)
    }

    @Test fun firesAgainAfterCooldownElapses() {
        val d = MotionRearmDetector()
        val first = feedAlternating(d, count = 1000, amp = 1.0)
        assertTrue(first > 0)
        val firstT = (first - 1L) * dt
        // Shake continuously well past the cooldown -> a second fire is allowed once cooldown elapses.
        var t = firstT + dt
        var secondFired = false
        val end = firstT + 30_000L
        while (t < end) {
            val i = ((t - firstT) / dt).toInt()
            if (d.onSample(t, if (i % 2 == 0) 0.0 else 1.0, 0.0, 0.0)) { secondFired = true; break }
            t += dt
        }
        assertTrue("should re-fire after the cooldown elapses", secondFired)
    }

    @Test fun resetClearsStateSoFirstSampleIsBaseline() {
        val d = MotionRearmDetector()
        feedAlternating(d, count = 300, amp = 1.0)
        d.reset()
        assertEquals(0.0, d.vibrationEma, 1e-9)
        // After reset, the first sample is just the baseline (no prev) and a brief quiet spell can't fire.
        assertEquals(-1, feedAlternating(d, count = 100, amp = 0.02))
    }

    @Test fun customThresholdRaisesTheBar() {
        // A higher threshold means moderate vibration that would fire at default no longer does.
        val strict = MotionRearmDetector(vibThreshold = 1.5)
        assertEquals(-1, feedAlternating(strict, count = 1000, amp = 0.8))
        assertTrue(strict.vibrationEma < 1.5)
    }
}
