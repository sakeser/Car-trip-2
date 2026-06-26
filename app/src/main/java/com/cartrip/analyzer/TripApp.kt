package com.cartrip.analyzer

import android.app.Application
import com.cartrip.analyzer.record.AutoRecordPrefs
import com.cartrip.analyzer.record.CompanionCarManager
import com.google.android.gms.maps.MapsInitializer

class TripApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { }
        // Re-arm CompanionDeviceManager presence observation on every process start (and after reboot):
        // associations persist, but the observe registration must be re-established each launch.
        if (AutoRecordPrefs.enabled(this) && AutoRecordPrefs.companionAssociated(this)) {
            runCatching { CompanionCarManager.startObserving(this) }
        }
    }
}
