package com.cartrip.analyzer.record

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest receiver for the car's Bluetooth connect/disconnect — an optional secondary in-car signal
 * (e.g. Android Auto / the car stereo). Inert until the user enables Bluetooth auto-record AND picks
 * the car device, so a stray headset never triggers it. Only the device *address* is read (no
 * `BLUETOOTH_CONNECT` permission needed for that). The decision lives in [AutoRecordController].
 *
 * NB: manifest registration of `ACTION_ACL_CONNECTED` may be restricted on newer Android — verify on
 * device; if it doesn't fire from the manifest, register it at runtime from a lightweight service or
 * move to `CompanionDeviceManager` device-presence (see HANDOFF §9).
 */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!AutoRecordPrefs.useBluetooth(app)) return
        val carAddr = AutoRecordPrefs.carBtAddress(app) ?: return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        if (device?.address != carAddr) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                AutoRecordController.setCarBtConnected(true)
                AutoRecordController.reevaluate(app)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                AutoRecordController.setCarBtConnected(false)
                AutoRecordController.reevaluate(app)
            }
        }
    }
}
