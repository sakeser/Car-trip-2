package com.cartrip.analyzer.data

object TripStatus {
    const val RECORDING = "recording"
    const val COMPLETED = "completed"
    const val PARTIAL = "partial"

    fun isPartial(status: String): Boolean = status == PARTIAL
}

object TripEndReason {
    const val MANUAL_STOP = "manual_stop"
    const val AUTO_STOP = "auto_stop"
    const val GPS_SIGNAL_LOST = "gps_signal_lost"
    const val NO_GPS_TRACK = "no_gps_track"
    const val SERVICE_DESTROYED = "service_destroyed"
    const val APP_RECOVERY = "app_recovery"
    const val LEGACY_UNFINISHED = "legacy_unfinished"
}
