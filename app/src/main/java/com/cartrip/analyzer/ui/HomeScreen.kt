package com.cartrip.analyzer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.cloud.CloudPrefs
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.record.RecordingState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onOpenVehicleSetup: () -> Unit,
    onOpenPremium: () -> Unit,
    modifier: Modifier = Modifier,
    trips: List<TripEntity> = emptyList(),
    onOpenTrip: (Long) -> Unit = {},
    enableAutoTripDetection: Boolean = true
) {
    val live by RecordingState.state.collectAsStateWithLifecycle()
    val fuelProfile by VehicleFuelState.state.collectAsStateWithLifecycle()
    val premiumStatus by PremiumState.state.collectAsStateWithLifecycle()
    if (enableAutoTripDetection) {
        AutoTripDetection(recording = live.recording, onStart = onStart)
    }

    val completedTrips = remember(trips) {
        trips.filter { it.analyzed && it.endTime > 0 && it.distanceM > 0.0 }
    }
    val latestTrip = completedTrips.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeHeader(
            onOpenGuide = onOpenGuide,
            onOpenVehicleSetup = onOpenVehicleSetup
        )

        if (live.recording) {
            ActiveDriveHero(
                elapsedS = live.elapsedS,
                speedKmh = live.speedKmh,
                distanceM = live.distanceM,
                hardBrake = live.hardBrake,
                hardAccel = live.hardAccel,
                hardCorner = live.hardCorner,
                gpsFixes = live.gpsFixes,
                gpsSignalLost = live.gpsSignalLost,
                lastGpsAgeS = live.lastGpsAgeS,
                maxSpeedKmh = live.maxSpeedKmh,
                onStop = onStop
            )
            ActiveTripFocusFooter()
        } else {
            ReadyDriveHero(
                latestTrip = latestTrip,
                onStart = onStart
            )

            QuickActions(
                onViewTrips = onViewTrips,
                onViewInsights = onViewInsights,
                onOpenVehicleSetup = onOpenVehicleSetup,
                onOpenPremium = onOpenPremium
            )

            InsightGrid(trips = completedTrips, fuelProfile = fuelProfile)

            if (latestTrip != null) {
                LatestTripCard(
                    trip = latestTrip,
                    fuelProfile = fuelProfile,
                    onOpenTrip = { onOpenTrip(latestTrip.id) }
                )
            } else {
                EmptyTripCard(onLoadDemoData = onLoadDemoData)
            }

            CloudSection(
                onConnect = onConnectCloud,
                onDisconnect = onDisconnectCloud,
                modifier = Modifier.fillMaxWidth()
            )
            PremiumPreviewCard(
                premiumStatus = premiumStatus,
                onOpenPremium = onOpenPremium
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenGuide: () -> Unit,
    onOpenVehicleSetup: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Car Trip Analyzer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Actual drives, explained clearly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenGuide) {
            Icon(Icons.Filled.Info, contentDescription = "Open guide")
        }
        IconButton(onClick = onOpenVehicleSetup) {
            Icon(Icons.Filled.Settings, contentDescription = "Open setup")
        }
    }
}

@Composable
private fun ReadyDriveHero(
    latestTrip: TripEntity?,
    onStart: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = HeroTeal,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ACTUAL VS EXPECTED",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                text = if (latestTrip == null) {
                    "Know what your next drive really cost you."
                } else {
                    "Record another drive and compare the result."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (latestTrip == null) {
                    "Time lost, distance, smoothness, and stop-and-go patterns appear as soon as a trip ends."
                } else {
                    "The more trips you record, the clearer your slow routes, smooth drives, and repeat patterns become."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.84f)
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = HeroTeal
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Start trip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActiveDriveHero(
    elapsedS: Long,
    speedKmh: Double,
    distanceM: Double,
    hardBrake: Int,
    hardAccel: Int,
    hardCorner: Int,
    gpsFixes: Int,
    gpsSignalLost: Boolean,
    lastGpsAgeS: Long,
    maxSpeedKmh: Double,
    onStop: () -> Unit
) {
    val eventCount = hardBrake + hardAccel + hardCorner
    val quality = activeDriveQuality(distanceM, eventCount)
    val signal = activeSignalState(gpsFixes, gpsSignalLost)
    val averageSpeedKmh = averageSpeedKmh(distanceM, elapsedS)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = ActiveInk,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE TRIP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Text(
                        text = "Recording GPS and motion",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActiveSignalPill(signal)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Format.clock(elapsedS),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Trip time",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActiveMetricTile(
                    icon = Icons.Filled.Speed,
                    label = "Now",
                    value = Format.speedKmh(speedKmh),
                    modifier = Modifier.weight(1f)
                )
                ActiveMetricTile(
                    icon = Icons.Filled.DirectionsCar,
                    label = "Distance",
                    value = Format.distance(distanceM),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActiveMetricTile(
                    icon = Icons.Filled.Timeline,
                    label = "Average",
                    value = Format.speedKmh(averageSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
                ActiveMetricTile(
                    icon = Icons.Filled.Timer,
                    label = "Top speed",
                    value = Format.speedKmh(maxSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
            }
            ActiveQualityPanel(
                quality = quality,
                hardBrake = hardBrake,
                hardAccel = hardAccel,
                hardCorner = hardCorner,
                eventCount = eventCount
            )
            if (gpsFixes == 0) {
                RecordingNotice(
                    icon = Icons.Filled.GpsFixed,
                    title = "Waiting for GPS",
                    text = "Keep location on and give the phone a clear signal before driving far."
                )
            }
            if (gpsSignalLost) {
                RecordingNotice(
                    icon = Icons.Filled.GpsOff,
                    title = "GPS signal lost",
                    text = "Last fix was ${Format.tripMinutes(lastGpsAgeS.toDouble())} ago. Motion is still being recorded locally."
                )
            }
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "End trip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActiveMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.76f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun ActiveSignalPill(signal: ActiveSignalState) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = signal.color.copy(alpha = 0.18f),
        contentColor = signal.color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = signal.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = signal.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveQualityPanel(
    quality: ActiveDriveQuality,
    hardBrake: Int,
    hardAccel: Int,
    hardCorner: Int,
    eventCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.1f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trip quality so far",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Text(
                        text = quality.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = quality.color
                    )
                }
                Text(
                    text = "$eventCount",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = quality.color
                )
            }
            LinearProgressIndicator(
                progress = { quality.score / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = quality.color,
                trackColor = Color.White.copy(alpha = 0.16f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventChip(label = "Brake", value = hardBrake, modifier = Modifier.weight(1f))
                EventChip(label = "Accel", value = hardAccel, modifier = Modifier.weight(1f))
                EventChip(label = "Corner", value = hardCorner, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EventChip(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.1f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecordingNotice(
    icon: ImageVector,
    title: String,
    text: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.84f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun ActiveTripFocusFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = GoodGreen,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Keep driving. The result appears automatically.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "When you end the trip, the app will analyze time, distance, smoothness, and route comparison signals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    onViewTrips: () -> Unit,
    onViewInsights: () -> Unit,
    onOpenVehicleSetup: () -> Unit,
    onOpenPremium: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeActionButton(
                label = "Trips",
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                onClick = onViewTrips,
                modifier = Modifier.weight(1f)
            )
            HomeActionButton(
                label = "Insights",
                icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
                onClick = onViewInsights,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeActionButton(
                label = "Vehicle",
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenVehicleSetup,
                modifier = Modifier.weight(1f)
            )
            HomeActionButton(
                label = "Premium",
                icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                onClick = onOpenPremium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun InsightGrid(
    trips: List<TripEntity>,
    fuelProfile: VehicleFuelProfile
) {
    val recent = trips.take(10)
    val distance = recent.sumOf { it.distanceM }
    val averageScore = recent.takeIf { it.isNotEmpty() }
        ?.map { TripScores.from(it).overall }
        ?.average()
        ?.roundToInt()
    val comparison = recent.mapNotNull { timeComparisonSeconds(it) }
    val comparisonTotal = comparison.takeIf { it.isNotEmpty() }?.sum()
    val fuelEstimate = FuelCost.estimateTrips(recent, fuelProfile)

    val tiles = listOf(
        InsightTileData(
            label = "Recent distance",
            value = if (distance > 0.0) Format.tripDistance(distance) else "--",
            accent = HeroTeal
        ),
        InsightTileData(
            label = "Est. fuel cost",
            value = fuelEstimate?.let { FuelCost.money(it.cost) } ?: "Add vehicle",
            accent = if (fuelEstimate != null) GoodGreen else WarmAmber
        ),
        InsightTileData(
            label = "Avg drive score",
            value = averageScore?.toString() ?: "--",
            accent = averageScore?.let { TripScores.color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
        ),
        InsightTileData(
            label = if (comparisonTotal != null) "Time vs expected" else "Idle time tracked",
            value = if (comparisonTotal != null) {
                signedMinutes(comparisonTotal)
            } else {
                recent.sumOf { it.idleS }.takeIf { it > 0.0 }?.let { Format.tripMinutes(it) } ?: "--"
            },
            accent = when {
                comparisonTotal == null -> WarmAmber
                comparisonTotal > 60.0 -> AlertRed
                comparisonTotal < -60.0 -> GoodGreen
                else -> GoodGreen
            }
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    InsightTile(tile = tile, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InsightTile(
    tile: InsightTileData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tile.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tile.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LatestTripCard(
    trip: TripEntity,
    fuelProfile: VehicleFuelProfile,
    onOpenTrip: () -> Unit
) {
    val scores = TripScores.from(trip)
    val comparison = tripComparisonCopy(trip)
    val eventCount = trip.hardBrakeCount + trip.hardAccelCount + trip.hardCornerCount
    val fuelEstimate = FuelCost.estimate(trip.distanceM, fuelProfile)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenTrip),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Latest trip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = trip.name.ifBlank { "Drive at ${Format.timeOfDay(trip.startTime)}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Format.dateOnly(trip.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ScoreRing(
                    score = scores.overall,
                    ringSize = 72.dp
                )
            }

            Text(
                text = comparison.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = comparison.color
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TripFact(
                    label = "Distance",
                    value = Format.tripDistance(trip.distanceM),
                    modifier = Modifier.weight(1f)
                )
                TripFact(
                    label = "Drive time",
                    value = Format.duration(trip.durationS),
                    modifier = Modifier.weight(1f)
                )
                TripFact(
                    label = fuelEstimate?.let { "Est. cost" } ?: "Events",
                    value = fuelEstimate?.let { FuelCost.money(it.cost) } ?: eventCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TripFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyTripCard(onLoadDemoData: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Start with a drive, or preview the app.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sample trips show result cards, route replay, history, insights, and cost estimates before you record your own drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onLoadDemoData) {
                Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preview sample trips")
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Setup and sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (cloud.email == null) {
                Text(
                    text = "Connect Google Sheets when you want a private driving log you can open later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect Google account")
                }
            } else {
                Text(
                    text = "Connected: ${cloud.email}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        text = "  Auto-sync completed trips",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            cloud.lastMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PremiumPreviewCard(
    premiumStatus: PremiumStatus,
    onOpenPremium: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = WarmAmber)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (premiumStatus.previewSeen) {
                            "Free trip recording stays available while future premium ideas stay separated."
                        } else {
                            "Preview future trend, benchmark, and export ideas without enabling payments."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenPremium,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("View advanced plan")
            }
        }
    }
}

private data class InsightTileData(
    val label: String,
    val value: String,
    val accent: Color
)

private data class TripComparisonCopy(
    val text: String,
    val color: Color
)

private fun tripComparisonCopy(trip: TripEntity): TripComparisonCopy {
    val delta = timeComparisonSeconds(trip)
    return when {
        delta == null -> TripComparisonCopy(
            text = "Expected-route comparison is ready when an estimate is available.",
            color = Color(0xFF64748B)
        )
        abs(delta) < 60.0 -> TripComparisonCopy(
            text = "Right on the expected pace.",
            color = GoodGreen
        )
        delta > 0.0 -> TripComparisonCopy(
            text = "${Format.tripMinutes(delta)} slower than expected.",
            color = AlertRed
        )
        else -> TripComparisonCopy(
            text = "${Format.tripMinutes(abs(delta))} faster than expected.",
            color = GoodGreen
        )
    }
}

private fun timeComparisonSeconds(trip: TripEntity): Double? =
    trip.googleEtaTrafficS.takeIf { it > 0.0 && trip.durationS > 0.0 }
        ?.let { trip.durationS - it }

private fun signedMinutes(seconds: Double): String {
    if (abs(seconds) < 60.0) return "On pace"
    val prefix = if (seconds > 0.0) "+" else "-"
    return "$prefix${Format.tripMinutes(abs(seconds))}"
}

private data class ActiveSignalState(
    val label: String,
    val color: Color,
    val icon: ImageVector
)

private data class ActiveDriveQuality(
    val label: String,
    val score: Int,
    val color: Color
)

private fun activeSignalState(gpsFixes: Int, gpsSignalLost: Boolean): ActiveSignalState =
    when {
        gpsSignalLost -> ActiveSignalState("GPS lost", AlertRed, Icons.Filled.Warning)
        gpsFixes == 0 -> ActiveSignalState("Finding GPS", WarmAmber, Icons.Filled.GpsFixed)
        gpsFixes < 8 -> ActiveSignalState("Locking on", WarmAmber, Icons.Filled.GpsFixed)
        else -> ActiveSignalState("GPS good", GoodGreen, Icons.Filled.GpsFixed)
    }

private fun activeDriveQuality(distanceM: Double, eventCount: Int): ActiveDriveQuality {
    val km = (distanceM / 1000.0).coerceAtLeast(0.3)
    val eventsPerKm = eventCount / km
    val score = (100.0 - eventsPerKm * 9.0).coerceIn(45.0, 100.0).roundToInt()
    return when {
        eventCount == 0 -> ActiveDriveQuality("Smooth so far", 100, GoodGreen)
        score >= 82 -> ActiveDriveQuality("Steady drive", score, GoodGreen)
        score >= 65 -> ActiveDriveQuality("A little busy", score, WarmAmber)
        else -> ActiveDriveQuality("Watch the inputs", score, AlertRed)
    }
}

private fun averageSpeedKmh(distanceM: Double, elapsedS: Long): Double =
    if (elapsedS <= 0L) 0.0 else distanceM / elapsedS * 3.6

private const val AUTO_PROMPT_SPEED_KMH = 10.0
private const val AUTO_PROMPT_RESET_KMH = 5.0
private const val AUTO_PROMPT_MIN_MS = 20_000L
private const val AUTO_PROMPT_SNOOZE_MS = 10L * 60L * 1000L

private val HeroTeal = Color(0xFF0F766E)
private val ActiveInk = Color(0xFF172554)
private val GoodGreen = Color(0xFF16A34A)
private val WarmAmber = Color(0xFFD97706)
private val AlertRed = Color(0xFFDC2626)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun HomeScreenPreview() {
    CarTripTheme {
        HomeScreen(
            onStart = {},
            onStop = {},
            onViewTrips = {},
            onViewInsights = {},
            onLoadDemoData = {},
            onConnectCloud = {},
            onDisconnectCloud = {},
            onOpenGuide = {},
            onOpenVehicleSetup = {},
            onOpenPremium = {},
            trips = listOf(previewTrip),
            enableAutoTripDetection = false
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ActiveDriveHeroPreview() {
    CarTripTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActiveDriveHero(
                elapsedS = 1_428,
                speedKmh = 52.0,
                distanceM = 12_400.0,
                hardBrake = 1,
                hardAccel = 0,
                hardCorner = 2,
                gpsFixes = 96,
                gpsSignalLost = false,
                lastGpsAgeS = 0,
                maxSpeedKmh = 84.0,
                onStop = {}
            )
            ActiveTripFocusFooter()
        }
    }
}

private val previewTrip = TripEntity(
    id = 42,
    name = "Morning commute",
    startTime = 1_719_320_400_000,
    endTime = 1_719_324_120_000,
    distanceM = 18_600.0,
    durationS = 2_220.0,
    movingS = 1_860.0,
    idleS = 360.0,
    maxSpeedMps = 26.0,
    avgMovingSpeedMps = 10.0,
    maxAccelMps2 = 2.1,
    maxBrakeMps2 = 2.4,
    maxLateralMps2 = 2.0,
    peakGForce = 0.32,
    hardAccelCount = 1,
    hardBrakeCount = 2,
    hardCornerCount = 1,
    smoothness = 88,
    analyzed = true,
    googleEtaTrafficS = 2_040.0,
    googleEtaFreeFlowS = 1_620.0,
    etaSource = "typical",
    etaFetchedAt = 1_719_324_120_000,
    locationSampleCount = 1800,
    motionSampleCount = 12000,
    hardBrakePct = 0.006,
    aggressiveTurnPct = 0.004,
    hardAccelPct = 0.003,
    limitCoverage = 0.7
)
