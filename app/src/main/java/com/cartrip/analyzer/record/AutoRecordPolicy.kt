package com.cartrip.analyzer.record

/**
 * Pure decision logic for hands-free auto start/stop. All Android I/O (receivers, the service, prefs,
 * timers, GPS) lives elsewhere, so this is fully unit-testable.
 *
 * The owner's phone is always wireless-charging on a mount in the car and pairs to the car stereo, so
 * **charging is the primary in-car signal** and the car's Bluetooth is a corroborating/alternative one.
 * Whether the car is actually *moving* is NOT decided here — [RecordingService] arms a provisional
 * recording on a trigger and confirms motion (speed ≥ min for a few seconds) before committing, so
 * charging while parked never creates a trip.
 */
object AutoRecordPolicy {

    data class Config(
        val enabled: Boolean,
        val requireCharging: Boolean = true,
        val requireWireless: Boolean = false,
        val useBluetooth: Boolean = false,
    )

    /** Is an in-car trigger currently present, given the user's config? */
    fun triggerPresent(cfg: Config, charging: Boolean, wireless: Boolean, carBtConnected: Boolean): Boolean {
        if (!cfg.enabled) return false
        val chargeOk = charging && (!cfg.requireWireless || wireless)
        val btOk = cfg.useBluetooth && carBtConnected
        return if (cfg.requireCharging) chargeOk else (chargeOk || btOk)
    }

    /** Arm a provisional recording when a trigger appears and nothing is recording yet. */
    fun shouldArm(cfg: Config, recording: Boolean, charging: Boolean, wireless: Boolean, carBtConnected: Boolean): Boolean =
        !recording && triggerPresent(cfg, charging, wireless, carBtConnected)

    /** Begin the stop grace when the in-car trigger drops during a recording. */
    fun shouldStop(cfg: Config, recording: Boolean, charging: Boolean, wireless: Boolean, carBtConnected: Boolean): Boolean =
        cfg.enabled && recording && !triggerPresent(cfg, charging, wireless, carBtConnected)
}
