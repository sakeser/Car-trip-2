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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PremiumEntitlement {
    FREE,
    PREMIUM
}

data class PremiumStatus(
    val entitlement: PremiumEntitlement = PremiumEntitlement.FREE,
    val previewSeen: Boolean = false
) {
    val isPremium: Boolean = entitlement == PremiumEntitlement.PREMIUM
}

object PremiumState {
    private val _state = MutableStateFlow(PremiumStatus())
    val state: StateFlow<PremiumStatus> = _state

    fun set(status: PremiumStatus) {
        _state.value = status
    }
}

object PremiumPrefs {
    private const val PREF = "premium_placeholder"
    private const val KEY_PREVIEW_SEEN = "preview_seen"

    fun load(context: Context): PremiumStatus {
        val status = PremiumStatus(
            entitlement = PremiumEntitlement.FREE,
            previewSeen = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_PREVIEW_SEEN, false)
        )
        PremiumState.set(status)
        return status
    }

    fun markPreviewSeen(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREVIEW_SEEN, true)
            .apply()
        PremiumState.set(PremiumStatus(previewSeen = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by PremiumState.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced insights") },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PremiumHero(status = status)

            PlanCard(
                title = "Free in this build",
                badge = "Included",
                accent = PremiumGreen,
                icon = Icons.Filled.CheckCircle,
                lines = listOf(
                    "Record unlimited trips on this phone",
                    "Actual vs expected trip result",
                    "Route replay, event map, and trip history",
                    "Vehicle/fuel profile and per-trip cost estimates",
                    "Optional Google Sheets sync"
                )
            )

            PlanCard(
                title = "Advanced later",
                badge = "Preview",
                accent = PremiumGold,
                icon = Icons.Filled.Lock,
                lines = listOf(
                    "Monthly driving cost trends and route benchmarks",
                    "Advanced delay explanations across repeated routes",
                    "Multi-vehicle fuel and efficiency profiles",
                    "Deeper score coaching and custom insights",
                    "Export bundles for tax, work, or family reporting"
                )
            )

            PremiumBoundaryCard()

            Button(
                onClick = { PremiumPrefs.markPreviewSeen(context) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text(
                    if (status.previewSeen) "Preview reviewed" else "Got it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF241A04)
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Back to app")
            }
        }
    }
}

@Composable
private fun PremiumHero(status: PremiumStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PremiumInk,
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
                    Icon(Icons.Filled.Star, contentDescription = null, tint = PremiumGold)
                }
                Text(
                    "ADVANCED INSIGHTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
            Text(
                "Free trip tools today. Deeper intelligence later.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (status.isPremium) {
                    "Advanced status can be represented in app state, but billing is not connected in this build."
                } else {
                    "Payments are not active in this build. This screen only defines future premium boundaries so today's core trip recording stays free."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    badge: String,
    accent: Color,
    icon: ImageVector,
    lines: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            lines.forEach { line ->
                PremiumFeatureRow(line = line, accent = accent)
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(line: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
        )
        Text(
            line,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PremiumBoundaryCard() {
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
                "Current build boundary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            BoundaryRow(Icons.Filled.Paid, "No billing SDK, payment flow, ads, or account wall is included.")
            BoundaryRow(Icons.AutoMirrored.Filled.ShowChart, "No analytics or tracking were added for this preview.")
            BoundaryRow(Icons.Filled.Route, "No existing trip recording, maps, sync, export, or analysis behavior is gated.")
        }
    }
}

@Composable
private fun BoundaryRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val PremiumInk = Color(0xFF1F1A10)
private val PremiumGold = Color(0xFFF59E0B)
private val PremiumGreen = Color(0xFF16A34A)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PremiumPreview() {
    CarTripTheme {
        PremiumHero(status = PremiumStatus())
    }
}
