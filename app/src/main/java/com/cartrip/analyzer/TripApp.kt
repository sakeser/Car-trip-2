package com.cartrip.analyzer

import android.app.Application
import com.cartrip.analyzer.record.AutoRecordPrefs
import com.cartrip.analyzer.record.AutoRecordWatchService
import com.cartrip.analyzer.record.CompanionCarManager
import com.google.android.gms.maps.MapsInitializer

class TripApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { }
        if (AutoRecordPrefs.enabled(this)) {
            // Start the persistent "armed" watcher (the reliable hands-free trigger on this device).
            // Foreground launch permits the FGS start; if the process started in the background it fails
            // soft and the next foreground launch / boot receiver re-arms it.
            AutoRecordWatchService.start(this)
            // CompanionDeviceManager presence observation is kept as a secondary path (associations
            // persist, but the observe registration must be re-established each launch).
            if (AutoRecordPrefs.companionAssociated(this)) {
                runCatching { CompanionCarManager.startObserving(this) }
            }
        }
    }
}
