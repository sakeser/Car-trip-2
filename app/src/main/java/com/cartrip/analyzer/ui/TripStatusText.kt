package com.cartrip.analyzer.ui

import com.cartrip.analyzer.data.TripEndReason
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.data.TripStatus

fun TripEntity.isPartialRecording(): Boolean = TripStatus.isPartial(status)

fun TripEntity.partialReasonText(): String =
    when (endReason) {
        TripEndReason.GPS_SIGNAL_LOST -> "GPS signal was lost before the trip ended."
        TripEndReason.NO_GPS_TRACK -> "No usable GPS track was captured."
        TripEndReason.SERVICE_DESTROYED -> "Recording was interrupted by Android."
        TripEndReason.APP_RECOVERY -> "Recovered after the app stopped unexpectedly."
        TripEndReason.LEGACY_UNFINISHED -> "Recovered from an older unfinished recording."
        else -> "Recording is incomplete."
    }
