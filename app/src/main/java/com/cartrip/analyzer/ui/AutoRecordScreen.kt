package com.cartrip.analyzer.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cartrip.analyzer.record.AutoRecordPrefs

/**
 * Auto-record settings (Rev U). Off by default. Charging (the phone wireless-charges on the car mount)
 * is the primary trigger; the car's Bluetooth is an optional secondary one. A trip is only kept if the
 * car actually moves after a trigger, and it stops a few seconds after the trigger drops.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AutoRecordPrefs.enabled(context)) }
    var requireWireless by remember { mutableStateOf(AutoRecordPrefs.requireWireless(context)) }
    var useBluetooth by remember { mutableStateOf(AutoRecordPrefs.useBluetooth(context)) }
    var carBtName by remember { mutableStateOf(AutoRecordPrefs.carBtName(context)) }
    var showPicker by remember { mutableStateOf(false) }

    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showPicker = true }

    fun pickCarDevice() {
        if (Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showPicker = true
        } else {
            btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "Start a trip automatically when you're in the car and moving, and stop a few seconds " +
                        "after you unplug. Charging is the main signal (your phone charges on the car mount); " +
                        "a trip is only kept if the car actually moves, so charging while parked won't record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }

            ToggleRow("Auto-record drives", "Hands-free start/stop", enabled) {
                enabled = it; AutoRecordPrefs.setEnabled(context, it)
            }

            if (enabled) {
                ToggleRow("Require wireless charging", "Ignore wired charging (e.g. at home)", requireWireless) {
                    requireWireless = it; AutoRecordPrefs.setRequireWireless(context, it)
                }
                ToggleRow("Also start on car Bluetooth", "Optional — also arm when your car device connects", useBluetooth) {
                    useBluetooth = it; AutoRecordPrefs.setUseBluetooth(context, it)
                    if (it && AutoRecordPrefs.carBtAddress(context) == null) pickCarDevice()
                }
                if (useBluetooth) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Car device: ${carBtName ?: "not set"}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { pickCarDevice() }) { Text("Choose") }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        "Note: Android may not let the app auto-start fully in the background. If so, you'll " +
                            "get a \"Drive detected — tap to start\" notification instead of a silent start. " +
                            "Manual Start/Stop always works.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        val devices = remember { bondedDevices(context) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Choose your car") },
            text = {
                if (devices.isEmpty()) {
                    Text("No paired Bluetooth devices found. Pair your car first, then try again.")
                } else {
                    Column {
                        devices.forEach { (name, address) ->
                            TextButton(onClick = {
                                AutoRecordPrefs.setCarBt(context, address, name)
                                carBtName = name
                                showPicker = false
                            }) { Text(name) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Paired devices as (name, address); empty if Bluetooth/permission is unavailable. */
private fun bondedDevices(context: Context): List<Pair<String, String>> = try {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    adapter?.bondedDevices.orEmpty().map { d: BluetoothDevice ->
        (runCatching { d.name }.getOrNull() ?: d.address) to d.address
    }
} catch (_: SecurityException) {
    emptyList()
}
