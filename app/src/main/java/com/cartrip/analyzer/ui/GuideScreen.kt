package com.cartrip.analyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * In-app explainer for how the app works and what every number means, plus a roughed-in
 * settings placeholder for sensitivities (to be wired up later).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How it works") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GuideCard(
                    "Your three scores",
                    listOf(
                        "Safety - risk-style score, à la Tesla. Penalizes the share of moving time you spend hard-braking (>0.30g), turning aggressively (>0.40g) or accelerating hard (>0.30g), plus speeding over the posted limit and high peak g-force.",
                        "Comfort - how smooth the ride felt: hard events, jerk (how abruptly acceleration changed), stop-and-go idling, and harsh stops.",
                        "Speed - 'you vs traffic': your actual time against Google's traffic estimate for that route and time. Matching Google ≈ 75; beating it climbs toward 100.",
                        "Drive (overall) - a weighted blend of the three."
                    )
                )
            }
            item {
                GuideCard(
                    "Events on the map & list",
                    listOf(
                        "Hard brake / acceleration / sharp turn - detected from your GPS-smoothed motion. Tap one to zoom the map to where it happened.",
                        "Pothole / big bump - a sharp vertical jolt picked up by the accelerometer.",
                        "Red on the route - you were over the posted speed limit there (from OpenStreetMap).",
                        "'Safety factors' shows the % of moving time in each behaviour; 'Road & ride' shows potholes, rough-road % and harsh stops."
                    )
                )
            }
            item {
                GuideCard(
                    "How it measures",
                    listOf(
                        "GPS (~1 Hz) gives position and speed; a Kalman filter + smoother turn it into clean speed and acceleration, with zero-velocity updates so a stop reads as a true 0.",
                        "The accelerometer (~25 Hz) plus the gravity sensor capture bumps, road roughness, and the exact feel of stops - independent of how the phone is sitting.",
                        "Distance is integrated from smoothed speed, so GPS jitter doesn't inflate it.",
                        "Speed limits come from OpenStreetMap; coverage varies by road, so a 'coverage %' tells you how much to trust the speeding number."
                    )
                )
            }
            item {
                GuideCard(
                    "Your data",
                    listOf(
                        "Everything records to this phone first and is checkpointed every ~2 seconds, so a crash, reboot, or forgetting to tap Stop won't lose the trip - it's recovered as a 'partial'.",
                        "If you're signed into Google with sync on, each real trip is appended to a Google Sheet in your Drive.",
                        "'Load demo data' only ever touches sample trips (marked SAMPLE) - your real recordings are never deleted by it."
                    )
                )
            }
            item {
                GuideCard(
                    "Settings (coming soon)",
                    listOf(
                        "Sensitivity controls for event thresholds (how hard a brake/turn/pothole has to be to count), speeding tolerance, and auto-stop timing will live here. Roughed in for now - tell me which knobs you want and I'll wire them up."
                    )
                )
            }
        }
    }
}

@Composable
private fun GuideCard(title: String, lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEach { line ->
                Text(
                    "•  $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
