package com.cartrip.analyzer.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    // Lazy so loading this object in a plain JUnit test never touches the Android main Looper.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    // At a connect edge the sticky battery intent may not have settled the *plug type* yet, so a
    // requireWireless user could miss a real wireless mount. Re-read once after this delay to settle it.
    private const val WIRELESS_SETTLE_MS = 1500L

    /** (charging, wireless) from the sticky battery intent. */
    private fun chargeState(context: Context): Pair<Boolean, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return charging to (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS)
    }

    /**
     * Re-evaluate after any in-car signal (charging or Bluetooth) changes. [source] tags the log.
     * [chargingEdge] is the authoritative charging value from a power broadcast (`true` on
     * `ACTION_POWER_CONNECTED`, `false` on `ACTION_POWER_DISCONNECTED`); `null` for non-power callers
     * (CDM presence, watcher start) which trust the sticky read. See [AutoRecordPolicy.effectiveCharging].
     */
    fun reevaluate(context: Context, source: String = "?", chargingEdge: Boolean? = null) {
        val cfg = AutoRecordPrefs.config(context)
        if (!cfg.enabled) { AutoRecordLog.add(context, "$source: ignored (auto-record off)"); return }
        val (stickyCharging, stickyWireless) = chargeState(context)
        val charging = AutoRecordPolicy.effectiveCharging(chargingEdge, stickyCharging)
        // Wireless only meaningful while charging; never claim wireless when the edge says not charging.
        val wireless = charging && stickyWireless
        val bt = carBtConnected
        val recording = RecordingState.state.value.recording
        val lagged = chargingEdge != null && chargingEdge != stickyCharging
        val state = "chg=$charging wl=$wireless bt=$bt rec=$recording" + if (lagged) " (sticky lagged)" else ""
        when {
            AutoRecordPolicy.shouldArm(cfg, recording, charging, wireless, bt) -> {
                AutoRecordLog.add(context, "$source -> ARM [$state]"); arm(context)
            }
            AutoRecordPolicy.shouldStop(cfg, recording, charging, wireless, bt) -> {
                AutoRecordLog.add(context, "$source -> STOP-grace [$state]")
                send(context, RecordingService.ACTION_AUTO_STOP_GRACE)
            }
            recording -> { // trigger still present mid-recording → cancel any pending grace stop
                AutoRecordLog.add(context, "$source -> trigger still present, cancel grace [$state]")
                send(context, RecordingService.ACTION_CHARGING_RESUMED)
            }
            else -> AutoRecordLog.add(context, "$source -> no-op [$state]")
        }
        // requireWireless needs the plug *type*, which the sticky may not have caught up to at a connect
        // edge. If we just started charging but it doesn't yet look wireless, re-read shortly so a real
        // wireless mount isn't missed. The follow-up read has no edge, so it sees the settled sticky.
        if (chargingEdge == true && cfg.requireWireless && !wireless && !recording) {
            val app = context.applicationContext
            mainHandler.postDelayed({ reevaluate(app, "$source-settle") }, WIRELESS_SETTLE_MS)
        }
    }

    /**
     * True only in the **armed-but-not-recording** window — an in-car trigger is present yet no trip is
     * running. [AutoRecordWatchService] watches the accelerometer for real motion exactly during this
     * window and calls [reevaluate] to re-arm, closing the gap where the first provisional discards (long
     * idle after plug-in) and nothing restarts when the drive finally begins. Reads the same sticky charge
     * + car-BT state as [reevaluate], so the watch turns off the instant the trigger drops or a trip starts.
     */
    fun isArmedNotRecording(context: Context): Boolean {
        val cfg = AutoRecordPrefs.config(context)
        if (!cfg.enabled) return false
        if (RecordingState.state.value.recording) return false
        val (charging, stickyWireless) = chargeState(context)
        val wireless = charging && stickyWireless
        return AutoRecordPolicy.triggerPresent(cfg, charging, wireless, carBtConnected)
    }

    /**
     * Hands-free trigger from [CarPresenceService] (CompanionDeviceManager). The car coming into range IS
     * the in-car signal here, so -- unlike [reevaluate] -- this does not consult the charger/Bluetooth
     * policy; the service's motion-confirm still discards a non-drive, so a stray connect never keeps a
     * trip. Charging state is logged for diagnostics only.
     */
    fun onCompanionPresence(context: Context, present: Boolean) {
        if (!AutoRecordPrefs.enabled(context)) {
            AutoRecordLog.add(context, "cdm-${if (present) "appeared" else "disappeared"}: ignored (auto-record off)")
            return
        }
        val (charging, wireless) = chargeState(context)
        val recording = RecordingState.state.value.recording
        val state = "chg=$charging wl=$wireless rec=$recording"
        when {
            present -> { AutoRecordLog.add(context, "cdm-appeared -> ARM [$state]"); arm(context) }
            recording -> {
                AutoRecordLog.add(context, "cdm-disappeared -> STOP-grace [$state]")
                send(context, RecordingService.ACTION_AUTO_STOP_GRACE)
            }
            // Car left while nothing is recording: don't spin up the service for a no-op stop.
            else -> AutoRecordLog.add(context, "cdm-disappeared -> no-op (not recording) [$state]")
        }
    }

    private fun arm(context: Context) {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_AUTO_ARM)
        try {
            ContextCompat.startForegroundService(context, intent)
            AutoRecordLog.add(context, "  FGS start OK")
        } catch (e: Exception) {
            // Android 12+ blocks a background FGS start — this is the suspected cause of "auto-start
            // didn't work". Log which exception so the field test confirms it, then fall back.
            AutoRecordLog.add(context, "  FGS start BLOCKED (${e.javaClass.simpleName}: ${e.message}) -> tap-to-start notif")
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
