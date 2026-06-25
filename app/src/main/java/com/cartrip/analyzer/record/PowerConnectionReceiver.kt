package com.cartrip.analyzer.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest receiver for charger connect/disconnect — the owner's phone wireless-charges on a mount in
 * the car, so this is the primary in-car signal. `ACTION_POWER_CONNECTED/DISCONNECTED` are deliverable
 * to manifest receivers even when the app isn't running. The actual decision lives in
 * [AutoRecordController] (which reads the full battery state + config).
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                AutoRecordController.reevaluate(context.applicationContext, "charger-on")
            Intent.ACTION_POWER_DISCONNECTED ->
                AutoRecordController.reevaluate(context.applicationContext, "charger-off")
        }
    }
}
