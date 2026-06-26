package com.cartrip.analyzer.record

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import androidx.annotation.RequiresApi
import android.os.Build

/**
 * The OS binds this and calls [onDeviceAppeared] when the associated car comes into range -- even with the
 * app backgrounded -- after [CompanionCarManager.startObserving]. This is the reliable hands-free trigger
 * that the manifest charger/Bluetooth receivers could not provide. On API 34+ the callback also carries a
 * temporary grant to start the location foreground service from the background (see
 * REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND), so [AutoRecordController] can arm silently.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CarPresenceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        AutoRecordLog.add(applicationContext, "cdm-service onDeviceAppeared (${associationInfo.deviceMacAddress})")
        AutoRecordController.onCompanionPresence(applicationContext, present = true)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        AutoRecordLog.add(applicationContext, "cdm-service onDeviceDisappeared (${associationInfo.deviceMacAddress})")
        AutoRecordController.onCompanionPresence(applicationContext, present = false)
    }

    // API 31-32 delivered MAC strings instead of AssociationInfo. The owner is on API 36 (the
    // AssociationInfo path above), but keep these so the service is correct on older OS versions too.
    @SuppressLint("MissingSuperCall")
    @Deprecated("Replaced by onDeviceAppeared(AssociationInfo) on API 33+")
    override fun onDeviceAppeared(address: String) {
        AutoRecordLog.add(applicationContext, "cdm-service onDeviceAppeared str ($address)")
        AutoRecordController.onCompanionPresence(applicationContext, present = true)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Replaced by onDeviceDisappeared(AssociationInfo) on API 33+")
    override fun onDeviceDisappeared(address: String) {
        AutoRecordLog.add(applicationContext, "cdm-service onDeviceDisappeared str ($address)")
        AutoRecordController.onCompanionPresence(applicationContext, present = false)
    }
}
