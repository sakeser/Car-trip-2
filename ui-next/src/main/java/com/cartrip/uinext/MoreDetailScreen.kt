package com.cartrip.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A More-tab drill-in section (nav route `more/{key}`). "about" renders real, read-only content; every other
 * section shows what it will hold plus a "coming soon" note until it lands behind an engine-api settings
 * gateway. Establishes the More -> detail -> back navigation shape. ASCII source (Cp1252 trap).
 */
@Composable
fun MoreDetailScreen(sectionKey: String, onBack: () -> Unit) {
    val item = MORE_ITEMS.firstOrNull { it.key == sectionKey }
    NextScaffold(title = item?.title ?: "More", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (sectionKey == "about") AboutContent()
            else ComingSoonContent(item)
        }
    }
}

@Composable
private fun AboutContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("CarTrip", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Premium preview $MIDDOT :ui-next",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "A local-first driving-intelligence app: it records your drives on-device, then turns the sensor " +
                    "and GPS data into plain-English insight -- how smoothly you drove, how demanding the drive was, " +
                    "your routes, trouble spots, and speeding -- without sending your trips anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    PlaceholderCard(
        title = "Licenses & credits",
        body = "Open-source licenses (Jetpack Compose, Google Maps, kotlinx) and map data attribution (OSM, " +
            "Google) will be listed here.",
    )
}

@Composable
private fun ComingSoonContent(item: MoreItem?) {
    PlaceholderCard(
        title = item?.title ?: "Settings",
        body = (item?.subtitle?.plus(". ") ?: "") +
            "This screen will land behind an engine-api settings gateway; for now the current app handles it.",
    )
}
