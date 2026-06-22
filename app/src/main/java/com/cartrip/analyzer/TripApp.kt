package com.cartrip.analyzer

import android.app.Application
import com.google.android.gms.maps.MapsInitializer

class TripApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { }
    }
}
