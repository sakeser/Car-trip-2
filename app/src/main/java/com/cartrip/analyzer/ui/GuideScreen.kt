package com.cartrip.analyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * In-app explainer for how the app works and what every number means.
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
                        "Safety - a Tesla-style risk score. Penalizes the share of moving time you spend hard-braking (>0.30g), turning aggressively (>0.40g) or accelerating hard (>0.30g), plus speeding over the posted limit and high peak g-force.",
                        "Comfort - how smooth the ride felt: hard events, jerk (how abruptly acceleration changed), stop-and-go idling, and harsh stops.",
                        "Speed - 'you vs traffic': your actual time against Google's traffic estimate for that route and time. Matching Google is about 75; beating it climbs toward 100.",
                        "Drive (overall) - a weighted blend of the three."
                    )
                )
            }
            item {
                GuideCard(
                    "Events on the map and list",
                    listOf(
                        "Hard brake, acceleration, and sharp turn events are detected from your GPS-smoothed motion. Tap one to zoom the map to where it happened.",
                        "Potholes and big bumps are sharp vertical jolts picked up by the accelerometer.",
                        "Red on the route means you were over the posted speed limit there, based on OpenStreetMap.",
                        "Safety factors show the percent of moving time in each behavior. Road and ride shows potholes, rough-road percent, and harsh stops."
                    )
                )
            }
            item {
                GuideCard(
                    "How it measures",
                    listOf(
                        "GPS (~1 Hz) gives position and speed. A Kalman filter and smoother turn it into clean speed and acceleration, with zero-velocity updates so a stop reads as a true 0.",
                        "The accelerometer (~25 Hz) plus the gravity sensor capture bumps, road roughness, and the exact feel of stops independent of how the phone is sitting.",
                        "Distance is integrated from smoothed speed, so GPS jitter does not inflate it.",
                        "Speed limits come from OpenStreetMap. Coverage varies by road, so a coverage percent tells you how much to trust the speeding number."
                    )
                )
            }
            item {
                GuideCard(
                    "Your data",
                    listOf(
                        "Everything records to this phone first and is checkpointed every ~2 seconds, so a crash, reboot, or forgetting to tap Stop will not lose the trip. It is recovered as a partial trip.",
                        "If you are signed into Google with sync on, each real trip is appended to a Google Sheet in your Drive.",
                        "Sample trips are clearly labeled as demos, and clearing them never deletes your real recordings."
                    )
                )
            }
            item {
                GuideCard(
                    "Tuning and setup",
                    listOf(
                        "Vehicle and fuel setup powers cost estimates today. Future tuning can add sensitivity controls for event thresholds, speed tolerance, and auto-stop timing while keeping the default behavior conservative."
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEach { line ->
                Text(
                    "- $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
