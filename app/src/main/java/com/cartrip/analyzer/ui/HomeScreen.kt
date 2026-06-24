package com.cartrip.analyzer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.cloud.CloudPrefs
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.record.RecordingState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onViewTrips: () -> Unit,
    onViewInsights: () -> Unit,
    onLoadDemoData: () -> Unit,
    onConnectCloud: () -> Unit,
    onDisconnectCloud: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    val live by RecordingState.state.collectAsStateWithLifecycle()
    AutoTripDetection(recording = live.recording, onStart = onStart)

    // In the car mount (landscape) show one giant Start/Stop button you can hit without looking.
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        LandscapeDriving(live = live, onStart = onStart, onStop = onStop, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Car Trip Analyzer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onOpenGuide) {
                Icon(Icons.Filled.Info, contentDescription = "How it works")
            }
            IconButton(onClick = onOpenDebug) {
                Icon(Icons.Filled.BugReport, contentDescription = "Diagnostics")
            }
        }

        if (live.recording) {
            Text(
                text = Format.clock(live.elapsedS),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${Format.speedKmh(live.speedKmh)}  |  ${Format.distance(live.distanceM)}",
                style = MaterialTheme.typography.titleMedium
            )

            StatGrid(
                stats = listOf(
                    Stat("Hard brakes", "${live.hardBrake}", Color(0xFFEF4444)),
                    Stat("Hard accel", "${live.hardAccel}", Color(0xFFF59E0B)),
                    Stat("Hard corners", "${live.hardCorner}", Color(0xFFF59E0B)),
                    Stat("GPS fixes", "${live.gpsFixes}")
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (live.gpsFixes == 0) {
                Text(
                    "Waiting for GPS fix... make sure location is on and you have sky view.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (live.gpsSignalLost) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                ) {
                    Text(
                        "GPS signal lost ${Format.tripMinutes(live.lastGpsAgeS.toDouble())} ago. Still recording motion locally; ending now will save this as a partial trip.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9A3412)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(76.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  End trip", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                text = "Mount your phone, start a trip, and drive. Sensor and GPS data are recorded and analyzed when you end the trip.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(88.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("  Start trip", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onViewTrips,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.List, contentDescription = null)
                Text("  Past trips", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onViewInsights,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.ShowChart, contentDescription = null)
                Text("  Insights", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onLoadDemoData,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Storage, contentDescription = null)
                Text("  Load sample data", style = MaterialTheme.typography.titleMedium)
            }

            CloudSection(
                onConnect = onConnectCloud,
                onDisconnect = onDisconnectCloud,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Full-screen Start/Stop for in-car landscape use — a huge, eyes-free tap target. */
@Composable
private fun LandscapeDriving(
    live: RecordingState.Live,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(10.dp)) {
        if (live.recording) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(Format.clock(live.elapsedS), fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Text("TAP TO END TRIP", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${Format.speedKmh(live.speedKmh)}  ·  ${Format.distance(live.distanceM)}",
                        fontSize = 22.sp
                    )
                }
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxSize()) {
                Text("START TRIP", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutoTripDetection(recording: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current
    var movingSince by remember { mutableStateOf<Long?>(null) }
    var mutedUntil by remember { mutableStateOf(0L) }

    DisposableEffect(context, recording) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (recording || !hasFine) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    val now = System.currentTimeMillis()
                    val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
                    if (speedKmh >= AUTO_PROMPT_SPEED_KMH) {
                        val start = movingSince ?: now.also { movingSince = it }
                        if (now >= mutedUntil &&
                            now - start >= AUTO_PROMPT_MIN_MS
                        ) {
                            mutedUntil = now + AUTO_PROMPT_SNOOZE_MS
                            onStart()
                        }
                    } else if (speedKmh < AUTO_PROMPT_RESET_KMH) {
                        movingSince = null
                    }
                }
            }
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {
            }
            onDispose { client.removeLocationUpdates(callback) }
        }
    }
}

@Composable
private fun CloudSection(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cloud by CloudState.state.collectAsStateWithLifecycle()
    var autoSync by remember { mutableStateOf(CloudPrefs.autoSync(context)) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Google Sheets sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            if (cloud.email == null) {
                Text(
                    "Connect a Google account to auto-append every trip to a Google Sheet (opens in Excel / 365).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect Google account")
                }
            } else {
                Text(
                    "Connected: ${cloud.email}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            autoSync = it
                            CloudPrefs.setAutoSync(context, it)
                        }
                    )
                    Text(
                        "  Auto-sync after each trip",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            cloud.lastMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val AUTO_PROMPT_SPEED_KMH = 10.0
private const val AUTO_PROMPT_RESET_KMH = 5.0
private const val AUTO_PROMPT_MIN_MS = 20_000L
private const val AUTO_PROMPT_SNOOZE_MS = 10L * 60L * 1000L
