package com.cartrip.analyzer.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cartrip.analyzer.MainActivity
import com.cartrip.analyzer.R

/**
 * Persistent "armed" foreground service for hands-free auto-record. Runs while auto-record is enabled.
 *
 * Why it exists (field-proven): a *manifest* receiver never gets `ACTION_POWER_CONNECTED` in the
 * background, and even a background Bluetooth receiver can't START the recording foreground service on
 * Android 12+. This service fixes both:
 *  1. it **runtime-registers** the charger/Bluetooth broadcasts — runtime receivers *do* fire while the
 *     service is alive, including `ACTION_POWER_CONNECTED`;
 *  2. because the app already has a running foreground service, [AutoRecordController.arm] is allowed to
 *     start [RecordingService] from the background.
 *
 * It reuses the existing [AutoRecordController] decision logic; the only cost is a low-importance ongoing
 * notification. It does no GPS/sensor work itself, so it's near-zero battery while idle.
 */
class AutoRecordWatchService : Service() {

    private val receivers = mutableListOf<BroadcastReceiver>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat()
        registerReceivers()
        AutoRecordLog.add(this, "watch service armed (foreground)")
        // A trigger may already be present (plugged in when enabled) — evaluate once.
        AutoRecordController.reevaluate(applicationContext, "watch-start")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun registerReceivers() {
        // Power: pass the broadcast *edge* as the authoritative charging value — the sticky battery
        // intent can lag the POWER_CONNECTED/DISCONNECTED broadcast and read inverted (field-observed).
        val power = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val app = applicationContext
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> AutoRecordController.reevaluate(app, "charger-on", chargingEdge = true)
                    Intent.ACTION_POWER_DISCONNECTED -> AutoRecordController.reevaluate(app, "charger-off", chargingEdge = false)
                }
            }
        }
        register(power, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }, exported = false)

        // Bluetooth ACL: registered EXPORTED. These privileged framework broadcasts can be dropped for a
        // not-exported runtime receiver on some OEM builds (suspected cause of "BT never fired"); they're
        // protected (system-only sender) so exported is safe, and we still filter by the saved car MAC.
        val bt = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val app = applicationContext
                if (!AutoRecordPrefs.useBluetooth(app)) return
                val car = AutoRecordPrefs.carBtAddress(app) ?: return
                @Suppress("DEPRECATION")
                val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (dev?.address != car) return
                val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                AutoRecordController.setCarBtConnected(connected)
                AutoRecordController.reevaluate(app, if (connected) "bt-connect" else "bt-disconnect")
            }
        }
        register(bt, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }, exported = true)

        AutoRecordLog.add(this, "receivers registered (power=not-exported, bt=exported)")
    }

    private fun register(r: BroadcastReceiver, filter: IntentFilter, exported: Boolean) {
        val flag = if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, r, filter, flag)
        receivers.add(r)
    }

    override fun onDestroy() {
        receivers.forEach { runCatching { unregisterReceiver(it) } }
        receivers.clear()
        AutoRecordLog.add(this, "watch service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto-record on")
            .setContentText("Watching for a drive (charger / car Bluetooth)")
            .setSmallIcon(R.drawable.ic_stat_record)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Auto-record", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "auto_record_watch"
        private const val NOTIF_ID = 43

        /** Start the watcher if auto-record is enabled (idempotent). Call from a foreground context. */
        fun start(context: Context) {
            if (!AutoRecordPrefs.enabled(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, AutoRecordWatchService::class.java)
                )
            }.onFailure { AutoRecordLog.add(context, "watch start blocked: ${it.javaClass.simpleName}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AutoRecordWatchService::class.java)) }
        }
    }
}
