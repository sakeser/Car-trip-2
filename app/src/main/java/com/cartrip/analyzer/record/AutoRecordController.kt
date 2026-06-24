package com.cartrip.analyzer.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cartrip.analyzer.R

/**
 * Applies [AutoRecordPolicy] to live device state and drives [RecordingService]. Called from the
 * power/Bluetooth broadcast receivers (which run in the app process). Stateless except for the
 * last-known car-Bluetooth connection, which the BT receiver feeds in (the charge path can't query a
 * classic-BT connection synchronously).
 *
 * ⚠️ Android 12+ blocks starting a `location` foreground service from the background. [arm] wraps the
 * start in try/catch and, on failure, posts a **tap-to-start** notification (a notification tap is a
 * user interaction that DOES permit the FGS start). The fully hands-free path is
 * `CompanionDeviceManager.startObservingDevicePresence` — see HANDOFF §9. MUST be verified on device.
 */
object AutoRecordController {

    @Volatile private var carBtConnected = false
    fun setCarBtConnected(v: Boolean) { carBtConnected = v }

    private const val FALLBACK_NOTIF_ID = 4242

    /** (charging, wireless) from the sticky battery intent. */
    private fun chargeState(context: Context): Pair<Boolean, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return charging to (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS)
    }

    /** Re-evaluate after any in-car signal (charging or Bluetooth) changes. */
    fun reevaluate(context: Context) {
        val cfg = AutoRecordPrefs.config(context)
        if (!cfg.enabled) return
        val (charging, wireless) = chargeState(context)
        val bt = carBtConnected
        val recording = RecordingState.state.value.recording
        when {
            AutoRecordPolicy.shouldArm(cfg, recording, charging, wireless, bt) -> arm(context)
            AutoRecordPolicy.shouldStop(cfg, recording, charging, wireless, bt) ->
                send(context, RecordingService.ACTION_AUTO_STOP_GRACE)
            recording -> // trigger still present mid-recording → cancel any pending grace stop
                send(context, RecordingService.ACTION_CHARGING_RESUMED)
        }
    }

    private fun arm(context: Context) {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_AUTO_ARM)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            postStartFallback(context)
        }
    }

    /** Stop/grace/resume only ever target an already-running (foreground) service, so plain start is fine. */
    private fun send(context: Context, action: String) {
        val intent = Intent(context, RecordingService::class.java).setAction(action)
        try { context.startService(intent) } catch (_: Exception) {}
    }

    private fun postStartFallback(context: Context) {
        ensureChannel(context)
        val tap = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_AUTO_ARM)
        val pi = PendingIntent.getForegroundService(
            context, 1, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, RecordingService.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Drive detected")
            .setContentText("Tap to start recording your trip")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(context).notify(FALLBACK_NOTIF_ID, n) } catch (_: SecurityException) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(RecordingService.CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(RecordingService.CHANNEL_ID, "Trip Recording", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}
