package com.cartrip.analyzer.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the auto-record watcher after a reboot. `BOOT_COMPLETED` is exempt from the implicit-broadcast
 * restrictions and permits starting a foreground service, so [AutoRecordWatchService] comes back without
 * the user opening the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            AutoRecordWatchService.start(context.applicationContext)
        }
    }
}
