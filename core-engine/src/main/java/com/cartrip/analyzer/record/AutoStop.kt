package com.cartrip.analyzer.record

/**
 * Retrospective auto-stop end-time logic, isolated as a pure function so it can be unit-tested and
 * reasoned about independently of the Android service.
 *
 * The problem it solves: the service decides to auto-stop only after a long stationary window
 * (~6 min). Using that trigger moment — or a fixed grace after the last *fast* sample — as the trip
 * end time is wrong; it tacks the idle period onto the drive. The trip really ended the instant the
 * car came to rest.
 *
 * Rule (per field spec): find the last sample still moving above [movingMps], then walk forward to
 * the first sample at/below [stationaryMps] — that timestamp is the true end. If the car never
 * settles inside the captured window, fall back to the last moving sample's time.
 */
object AutoStop {
    const val MOVING_MPS = 4.0 / 3.6      // 4 km/h — "still driving / coasting to a stop"
    const val STATIONARY_MPS = 0.7        // ~2.5 km/h — GPS-noise floor for "stopped"

    /**
     * @param samples time-ordered (tMonotonicMs, speedMps) pairs.
     * @return the retrospective end timestamp, or null if the window shows no movement at all.
     */
    fun retrospectiveEndTime(
        samples: List<Pair<Long, Double>>,
        movingMps: Double = MOVING_MPS,
        stationaryMps: Double = STATIONARY_MPS
    ): Long? {
        if (samples.isEmpty()) return null
        val lastMovingIdx = samples.indexOfLast { it.second > movingMps }
        if (lastMovingIdx < 0) return null
        for (i in lastMovingIdx + 1 until samples.size) {
            if (samples[i].second <= stationaryMps) return samples[i].first
        }
        // Never settled inside the captured window — end at the last moving sample we saw.
        return samples[lastMovingIdx].first
    }
}
