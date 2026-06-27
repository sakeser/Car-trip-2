package com.cartrip.analyzer.record

/**
 * Retrospective auto-START trim — the mirror of [AutoStop]. An auto-armed trip begins recording the
 * instant the in-car trigger appears (charger / car-Bluetooth / motion re-arm), which is usually while
 * the car is still parked: seatbelt, warm-up, backing out. Those leading stationary seconds contaminate
 * the trip's start — the start location/name is the parking spot, idle time and fuel idle-burn count the
 * warm-up, and door-slam / settling jostle lands near t0. This finds where the drive actually began so
 * the parked prefix can be trimmed (the stop side already trims the idle tail via [AutoStop]).
 *
 * **Why it needs more than "first sample above the moving threshold"** (learned from replaying real trips):
 * GPS at trip start is noisy — a cold fix reports speed 0 while the position jitters, and a car backing
 * out does a brief parking-lot *creep* (a second or two above walking pace) and then pauses before truly
 * pulling away. Picking the first above-threshold sample latches onto that creep/jitter and under-trims.
 * So a departure only counts once motion **sustains** for [departSustainMs] without dropping back to a
 * full stop — that filters creeps and single jitter spikes and finds the real pull-away.
 *
 * Rule: find the first motion run (speed leaving [stationaryMps]) that reaches above [movingMps] AND
 * stays out of a full stop for at least [departSustainMs]; the true start is the last at/below-stationary
 * sample just before that run (kept as a zero-speed ZUPT anchor). Data is deleted strictly *before* the
 * returned timestamp, so the anchor sample itself is retained. Returns null (don't trim) when no sustained
 * departure is found (no GPS / a non-drive — left for the too-short discard) or the car was already
 * departing from the very first sample (re-armed mid-motion → no parked prefix).
 *
 * By construction it never trims a sample above [movingMps], so real driving distance is structurally
 * preserved and a real drive can never be trimmed down into the too-short discard.
 */
object AutoStart {
    const val DEPART_SUSTAIN_MS = 3_000L  // motion must persist this long to count as a real pull-away

    /**
     * @param samples time-ordered (tMonotonicMs, speedMps) pairs from the start of the trip across the
     *   warm-up and pull-away (the service captures the leading minutes for exactly this).
     * @return the retrospective start timestamp, or null if there is no parked prefix to trim.
     */
    fun retrospectiveStartTime(
        samples: List<Pair<Long, Double>>,
        movingMps: Double = AutoStop.MOVING_MPS,
        stationaryMps: Double = AutoStop.STATIONARY_MPS,
        departSustainMs: Long = DEPART_SUSTAIN_MS
    ): Long? {
        var runStartIdx = -1        // index where the current motion run left "stopped"
        var runReachedMoving = false // did this run exceed movingMps (not just creep above stationary)?
        for (i in samples.indices) {
            val speed = samples[i].second
            if (speed <= stationaryMps) {
                runStartIdx = -1; runReachedMoving = false   // back to a full stop — reset the run
                continue
            }
            if (runStartIdx < 0) runStartIdx = i             // a motion run begins
            if (speed > movingMps) runReachedMoving = true
            if (runReachedMoving && samples[i].first - samples[runStartIdx].first >= departSustainMs) {
                // Sustained pull-away confirmed. The true start is the last stop before this run; since
                // runStartIdx is the first sample above stationary, the sample before it is that stop.
                if (runStartIdx == 0) return null            // departing from sample 0 → no parked prefix
                return samples[runStartIdx - 1].first
            }
        }
        return null
    }
}
