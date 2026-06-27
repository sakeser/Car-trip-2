package com.cartrip.analyzer.record

import kotlin.math.sqrt

/**
 * Pure, testable decision for when to RE-ARM auto-record from accelerometer motion. It closes the gap
 * where you plug in, sit longer than the motion-confirm window (so the provisional discards), then drive
 * -- with nothing left to restart recording.
 *
 * The watcher feeds raw accelerometer samples only while *armed-but-not-recording* (trigger present, not
 * recording), so the phone is essentially always charging when this runs -- accelerometer cost is moot.
 *
 * Robustness:
 *  - A gravity-cancelling **jerk EMA** (sample-to-sample delta, same as the recorder) must stay at/above
 *    [vibThreshold] **continuously** for [sustainMs] before firing, so a single jostle or phone handling
 *    won't trip it, and a smooth idle below the threshold never does.
 *  - [vibThreshold] sits above the recorder's 0.30 vibration confirm gate, so a re-arm always clears
 *    motion-confirm; and the re-armed provisional's own GPS/vibration confirm (plus the <5 m/<10 s
 *    discard) is the final filter -- a false fire only ever costs one discarded provisional.
 *  - A [cooldownMs] backstop prevents rapid re-fire if something keeps shaking the phone.
 */
class MotionRearmDetector(
    private val vibThreshold: Double = 0.40,
    private val sustainMs: Long = 4_000L,
    private val cooldownMs: Long = 15_000L,
) {
    private var ema = 0.0
    private var pax = 0.0
    private var pay = 0.0
    private var paz = 0.0
    private var havePrev = false
    private var motionSinceT = 0L
    private var cooldownUntil = 0L

    val vibrationEma: Double get() = ema

    /** Clear transient state when the watch turns off (unplugged, or recording started). */
    fun reset() {
        ema = 0.0
        havePrev = false
        motionSinceT = 0L
        cooldownUntil = 0L
    }

    /** Feed one raw accelerometer sample (gravity included). Returns true exactly when a re-arm should fire. */
    fun onSample(tMs: Long, ax: Double, ay: Double, az: Double): Boolean {
        if (!havePrev) {
            pax = ax; pay = ay; paz = az; havePrev = true
            return false
        }
        val jerk = sqrt((ax - pax) * (ax - pax) + (ay - pay) * (ay - pay) + (az - paz) * (az - paz))
        pax = ax; pay = ay; paz = az
        ema = 0.94 * ema + 0.06 * jerk
        if (ema < vibThreshold) {
            motionSinceT = 0L
            return false
        }
        if (motionSinceT == 0L) {
            motionSinceT = tMs
            return false
        }
        if (tMs - motionSinceT >= sustainMs && tMs >= cooldownUntil) {
            cooldownUntil = tMs + cooldownMs
            motionSinceT = 0L
            return true
        }
        return false
    }
}
