package com.cartrip.analyzer.record

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Hands-free auto-record via [CompanionDeviceManager] (Rev AG). The owner's manifest broadcast receivers
 * (charger / classic-Bluetooth) never fire while the app is backgrounded on the S25, so auto-start only
 * worked with the app open. CompanionDeviceManager fixes that: the user associates the car once (a system
 * dialog), then the OS calls [CarPresenceService] directly when the car comes into range -- even
 * backgrounded -- and on API 34+ grants a background foreground-service start so the trip starts silently.
 *
 * Gated to API 33+ (clean [AssociationInfo] + [CompanionDeviceManager.myAssociations]); the owner is on
 * Android 16. Everything is wrapped in logging because [CarPresenceService.onDeviceAppeared] can only be
 * exercised with the real car in range -- the field log is the only post-hoc evidence.
 */
object CompanionCarManager {

    /** Hands-free pairing is available (CDM present and API new enough for the AssociationInfo flow). */
    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    private fun cdm(context: Context): CompanionDeviceManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.getSystemService(CompanionDeviceManager::class.java)
        else null

    /** True once the user has associated at least one car device with the app. */
    fun hasAssociation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return runCatching { cdm(context)?.myAssociations?.isNotEmpty() == true }.getOrDefault(false)
    }

    /**
     * Launch the system association dialog. On success the OS delivers an [IntentSender] to [onChooser];
     * the caller starts it with an activity-result launcher and finishes in [onAssociationResult].
     */
    fun requestAssociation(
        context: Context,
        onChooser: (IntentSender) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { onError("needs Android 13+"); return }
        val manager = cdm(context) ?: run { onError("no CompanionDeviceManager"); return }
        // Open Bluetooth filter -- the chooser lists nearby/paired devices and the user taps their car.
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        AutoRecordLog.add(context, "cdm-associate: requesting (chooser)")
        manager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                // Also delivered via the activity result; nothing to do here.
            }
            override fun onFailure(error: CharSequence?) {
                AutoRecordLog.add(context, "cdm-associate FAILED: ${error ?: "unknown"}")
                onError(error?.toString() ?: "association failed")
            }
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Begin observing every associated device's presence, so [CarPresenceService] is called on
     * appear/disappear. Safe to call repeatedly (e.g. every app start and after reboot). Returns the
     * number of devices now observed.
     */
    fun startObserving(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 0
        val manager = cdm(context) ?: return 0
        val assocs = runCatching { manager.myAssociations }.getOrNull().orEmpty()
        if (assocs.isEmpty()) { AutoRecordLog.add(context, "cdm-observe: no associations"); return 0 }
        var started = 0
        for (a in assocs) {
            val addr = a.deviceMacAddress?.toString()?.uppercase() ?: continue
            try {
                @Suppress("DEPRECATION")
                manager.startObservingDevicePresence(addr)
                AutoRecordLog.add(context, "cdm-observe started ($addr)")
                started++
            } catch (e: Exception) {
                AutoRecordLog.add(context, "cdm-observe FAILED ($addr): ${e.javaClass.simpleName}")
            }
        }
        return started
    }

    /** The MAC of the most recently associated device, if any (for the settings UI). */
    fun associatedAddress(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            cdm(context)?.myAssociations?.lastOrNull()?.deviceMacAddress?.toString()?.uppercase()
        }.getOrNull()
    }
}
