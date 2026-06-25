package com.cartrip.analyzer.ui

import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.analysis.GnssQuality
import com.cartrip.analyzer.cloud.RoutesClient
import com.cartrip.analyzer.cloud.RoutesConfig
import com.cartrip.analyzer.cloud.SpeedLimits
import com.cartrip.analyzer.record.AutoRecordLog
import com.cartrip.analyzer.record.RecordingState

/**
 * Developer diagnostics: live capture health (motion/GPS rates, GNSS satellites/signal) so the field
 * tester can confirm data is actually flowing mid-drive, plus build + last-service-call status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val live by RecordingState.state.collectAsStateWithLifecycle()

    val version = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        }.getOrDefault("?")
    }
    val mapsKey = remember { RoutesConfig.apiKey(ctx) != null }

    val elapsed = live.elapsedS.coerceAtLeast(1)
    val motionHz = if (live.recording) live.motionSamples.toDouble() / elapsed else 0.0
    val gpsHz = if (live.recording) live.gpsFixes.toDouble() / elapsed else 0.0
    val gnss = GnssQuality.level(live.gnssSatsUsed.toDouble(), live.gnssCn0, if (live.recording) 5 else 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DebugCard("Capture (live)") {
                if (!live.recording) {
                    KvRow("Status", "Not recording")
                    Text(
                        "Start a trip to see live motion / GPS / GNSS capture.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    KvRow("Status", "Recording", Color(0xFF22C55E))
                    KvRow("Elapsed", Format.clock(live.elapsedS))
                    KvRow(
                        "Motion",
                        if (live.motionSamples == 0) "NONE — sensor stall?" else "${live.motionSamples} · ${"%.0f".format(motionHz)} Hz",
                        if (live.motionSamples == 0) Color(0xFFEF4444) else null
                    )
                    KvRow(
                        "GPS fixes",
                        "${live.gpsFixes} · ${"%.1f".format(gpsHz)}/s",
                        if (live.gpsFixes == 0) Color(0xFFEF4444) else null
                    )
                    KvRow(
                        "GPS signal",
                        if (live.gpsSignalLost) "LOST (${live.lastGpsAgeS}s)" else "OK",
                        if (live.gpsSignalLost) Color(0xFFEF4444) else null
                    )
                    KvRow(
                        "GNSS sats",
                        "${live.gnssSatsUsed} used / ${live.gnssSatsVisible} seen",
                        gnssColor(gnss)
                    )
                    KvRow("GNSS C/N0", "${"%.0f".format(live.gnssCn0)} dB-Hz · ${gnss.label}", gnssColor(gnss))
                    KvRow("Dual-freq L5", if (live.gnssL5) "yes" else "no")
                    KvRow(
                        "Sensor restarts",
                        "${live.sensorRestarts}",
                        if (live.sensorRestarts > 0) Color(0xFFF59E0B) else null
                    )
                }
            }

            DebugCard("Build") {
                KvRow("Version", version)
                KvRow("Maps API key", if (mapsKey) "present" else "MISSING", if (mapsKey) null else Color(0xFFEF4444))
            }

            DebugCard("Services — last result") {
                val cache = SpeedLimits.lastCacheStat().ifBlank { "—" }
                val sl = SpeedLimits.lastDiagnostic().ifBlank { "ok" }
                val routes = RoutesClient.lastDiagnostic().ifBlank { "ok" }
                KvRow("Speed-limit cache", cache)
                KvRow("Speed-limit lookup", sl)
                KvRow("Routes/traffic", routes)
                Text(
                    "Cache/diagnostic values update after a speed-limit refresh or trip-end ETA fetch.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DebugCard("Auto-record log") {
                val ctx = LocalContext.current
                val log = remember { AutoRecordLog.entries(ctx) }
                if (log.isEmpty()) {
                    Text(
                        "No auto-record activity yet. Enable auto-record, then plug into the car and drive — " +
                            "the charger/Bluetooth trigger, the foreground-service start result (this reveals the " +
                            "Android 12+ background block), and motion-confirm all land here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    log.take(40).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun gnssColor(level: GnssQuality.Level): Color? = when (level) {
    GnssQuality.Level.STRONG -> Color(0xFF22C55E)
    GnssQuality.Level.MODERATE -> Color(0xFFF59E0B)
    GnssQuality.Level.WEAK -> Color(0xFFEF4444)
    GnssQuality.Level.UNKNOWN -> null
}

@Composable
private fun DebugCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun KvRow(key: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
