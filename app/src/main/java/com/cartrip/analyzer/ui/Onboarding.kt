package com.cartrip.analyzer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class OnboardingStatus(
    val completed: Boolean = false
)

object OnboardingState {
    private val _state = MutableStateFlow(OnboardingStatus())
    val state: StateFlow<OnboardingStatus> = _state

    fun set(status: OnboardingStatus) {
        _state.value = status
    }
}

object OnboardingPrefs {
    private const val PREF = "onboarding"
    private const val KEY_COMPLETED = "completed"

    fun load(context: Context): OnboardingStatus {
        val status = OnboardingStatus(
            completed = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)
        )
        OnboardingState.set(status)
        return status
    }

    fun markComplete(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
        OnboardingState.set(OnboardingStatus(completed = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    startAfterOnboarding: Boolean,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCompleteAndStart: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Before your first trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            OnboardingPrefs.markComplete(context)
                            onComplete()
                        }
                    ) {
                        Text("Skip")
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingHero()

            PermissionExplanationCard(
                icon = Icons.Filled.LocationOn,
                title = "Location while driving",
                body = "GPS is used to measure route, speed, distance, stops, and actual time. The Android permission prompt appears only after you continue."
            )
            PermissionExplanationCard(
                icon = Icons.Filled.Sensors,
                title = "Motion signals",
                body = "Phone sensors help spot hard braking, acceleration, cornering, bumps, and ride smoothness. They make the result useful after the trip ends."
            )
            PermissionExplanationCard(
                icon = Icons.Filled.Notifications,
                title = "Recording notification",
                body = "When Android requires it, a notification keeps the trip recorder alive while you drive and makes it clear when recording is active."
            )
            PermissionExplanationCard(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy expectations",
                body = "Trips are stored on this phone first. Google Sheets sync is optional and only runs if you connect it. No ads, accounts, or analytics are added here."
            )

            ResultPromiseCard()

            Button(
                onClick = {
                    OnboardingPrefs.markComplete(context)
                    if (startAfterOnboarding) onCompleteAndStart() else onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OnboardingGreen),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text(
                    if (startAfterOnboarding) "Continue to permissions" else "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    OnboardingPrefs.markComplete(context)
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("I will start a trip later")
            }
        }
    }
}

@Composable
private fun OnboardingHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OnboardingInk,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = Color.White)
                }
                Text(
                    "HOW RECORDING WORKS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
            Text(
                "Record a drive, then see what really happened.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "The app compares actual time, route, distance, smoothness, events, and cost estimates after each trip. Nothing useful happens until a trip is recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun PermissionExplanationCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OnboardingGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = OnboardingGreen)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultPromiseCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "After the trip",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingPromise("Time lost or saved", Modifier.weight(1f))
                OnboardingPromise("Trip score", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingPromise("Fuel cost estimate", Modifier.weight(1f))
                OnboardingPromise("Route replay", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OnboardingPromise(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val OnboardingInk = Color(0xFF10211F)
private val OnboardingGreen = Color(0xFF16A34A)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun OnboardingPreview() {
    CarTripTheme {
        OnboardingHero()
    }
}
