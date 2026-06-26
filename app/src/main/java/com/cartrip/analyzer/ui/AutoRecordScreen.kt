package com.cartrip.analyzer.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cartrip.analyzer.record.AutoRecordPrefs
import com.cartrip.analyzer.record.AutoRecordWatchService
import com.cartrip.analyzer.record.CompanionCarManager

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

    val cdmSupported = remember { CompanionCarManager.supported(context) }
    var companionAssociated by remember { mutableStateOf(CompanionCarManager.hasAssociation(context)) }
    var companionMsg by remember { mutableStateOf<String?>(null) }

    // Background location ("Allow all the time") is required to start the location FGS from the background
    // (hands-free start while the app is closed). Re-check on resume so the warning clears after granting.
    var bgLocationGranted by remember { mutableStateOf(hasBackgroundLocation(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bgLocationGranted = hasBackgroundLocation(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { bgLocationGranted = hasBackgroundLocation(context) }
    fun requestBackgroundLocation() {
        // Background location can only be granted after fine/coarse. On Android 11+ the one-shot request
        // routes to system settings ("Allow all the time"); on 10 it shows an inline option.
        if (Build.VERSION.SDK_INT < 29) { bgLocationGranted = true; return }
        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    val pairLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && Build.VERSION.SDK_INT >= 33) {
            val info: AssociationInfo? =
                result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            val mac = info?.deviceMacAddress?.toString()?.uppercase()
            val nm = info?.displayName?.toString()
            AutoRecordPrefs.setCompanionAssociated(context, true)
            if (mac != null) AutoRecordPrefs.setCarBt(context, mac, nm ?: carBtName)
            companionAssociated = true
            if (nm != null) carBtName = nm
            val n = CompanionCarManager.startObserving(context)
            companionMsg = "Paired${nm?.let { " with $it" } ?: ""}. Now watching for your car ($n device${if (n == 1) "" else "s"})."
        } else {
            companionMsg = "Pairing cancelled."
        }
    }

    fun pairCar() {
        companionMsg = null
        CompanionCarManager.requestAssociation(
            context,
            onChooser = { sender -> pairLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            onError = { msg -> companionMsg = "Couldn't start pairing: $msg" }
        )
    }

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
                // Start/stop the persistent armed watcher (the reliable background trigger).
                if (it) AutoRecordWatchService.start(context) else AutoRecordWatchService.stop(context)
            }

            if (enabled) {
                if (!bgLocationGranted) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Action needed: allow location \"All the time\"",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Hands-free start (while the app is closed) needs background location. " +
                                    "Without it, a trip only auto-starts while the app is open - a background " +
                                    "attempt is safely skipped. Tap below, then choose \"Allow all the time\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = { requestBackgroundLocation() }) { Text("Allow all the time") }
                            TextButton(onClick = { openAppSettings(context) }) { Text("Open app settings instead") }
                        }
                    }
                }
                if (cdmSupported) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Hands-free background start (recommended)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Pair your car once below. Android then wakes the app the moment your car connects " +
                                    "- even when it's closed - so a trip can start silently in the background. This is " +
                                    "the only reliable way to auto-start without opening the app first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                if (companionAssociated) "Status: car paired" else "Status: not paired yet",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(onClick = { pairCar() }) {
                                Text(if (companionAssociated) "Re-pair car" else "Pair car for hands-free start")
                            }
                            companionMsg?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

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
                        "While auto-record is on, a small \"Auto-record on\" notification keeps a background " +
                            "watcher running so charging (and car Bluetooth, if enabled) can start a trip even " +
                            "when the app is closed. Car pairing above is an optional secondary path. If a " +
                            "background start is ever blocked you'll get a \"Drive detected - tap to start\" " +
                            "notification instead. Manual Start/Stop always works.",
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

/** Background location ("Allow all the time"); always true below API 29 where it doesn't exist. */
private fun hasBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Open this app's system settings page (so the user can set location to "Allow all the time"). */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
