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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartrip.analyzer.data.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class FuelEconomyUnit(
    val label: String,
    val economyLabel: String,
    val priceLabel: String,
    val fuelUnitLabel: String
) {
    L_PER_100_KM("L/100 km", "Fuel economy", "Fuel price / L", "L"),
    MPG_US("MPG", "Fuel economy", "Fuel price / gal", "gal")
}

data class VehicleFuelProfile(
    val vehicleName: String = "",
    val unit: FuelEconomyUnit = FuelEconomyUnit.L_PER_100_KM,
    val economy: Double = 8.5,
    val fuelPrice: Double = 1.65,
    val configured: Boolean = false
) {
    fun displayName(): String = vehicleName.ifBlank { "Vehicle" }
}

data class FuelCostEstimate(
    val fuelAmount: Double,
    val fuelUnitLabel: String,
    val cost: Double
)

object VehicleFuelState {
    private val _state = MutableStateFlow(VehicleFuelProfile())
    val state: StateFlow<VehicleFuelProfile> = _state

    fun set(profile: VehicleFuelProfile) {
        _state.value = profile
    }
}

object VehicleFuelPrefs {
    private const val PREF = "vehicle_fuel_profile"
    private const val KEY_NAME = "name"
    private const val KEY_UNIT = "unit"
    private const val KEY_ECONOMY = "economy"
    private const val KEY_PRICE = "price"
    private const val KEY_CONFIGURED = "configured"

    fun load(context: Context): VehicleFuelProfile {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val unit = runCatching {
            FuelEconomyUnit.valueOf(
                prefs.getString(KEY_UNIT, FuelEconomyUnit.L_PER_100_KM.name)
                    ?: FuelEconomyUnit.L_PER_100_KM.name
            )
        }.getOrDefault(FuelEconomyUnit.L_PER_100_KM)
        val profile = VehicleFuelProfile(
            vehicleName = prefs.getString(KEY_NAME, "") ?: "",
            unit = unit,
            economy = Double.fromBits(prefs.getLong(KEY_ECONOMY, 8.5.toBits())),
            fuelPrice = Double.fromBits(prefs.getLong(KEY_PRICE, 1.65.toBits())),
            configured = prefs.getBoolean(KEY_CONFIGURED, false)
        )
        VehicleFuelState.set(profile)
        return profile
    }

    fun save(context: Context, profile: VehicleFuelProfile) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, profile.vehicleName.trim())
            .putString(KEY_UNIT, profile.unit.name)
            .putLong(KEY_ECONOMY, profile.economy.toBits())
            .putLong(KEY_PRICE, profile.fuelPrice.toBits())
            .putBoolean(KEY_CONFIGURED, profile.configured)
            .apply()
        VehicleFuelState.set(profile)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        VehicleFuelState.set(VehicleFuelProfile())
    }
}

object FuelCost {
    fun estimate(distanceM: Double, profile: VehicleFuelProfile): FuelCostEstimate? {
        if (!profile.configured || distanceM <= 0.0 || profile.economy <= 0.0 || profile.fuelPrice <= 0.0) {
            return null
        }
        val km = distanceM / 1000.0
        val fuel = when (profile.unit) {
            FuelEconomyUnit.L_PER_100_KM -> km * profile.economy / 100.0
            FuelEconomyUnit.MPG_US -> {
                val miles = km * 0.621371
                miles / profile.economy
            }
        }
        return FuelCostEstimate(
            fuelAmount = fuel,
            fuelUnitLabel = profile.unit.fuelUnitLabel,
            cost = fuel * profile.fuelPrice
        )
    }

    fun estimateTrips(trips: List<TripEntity>, profile: VehicleFuelProfile): FuelCostEstimate? {
        val distanceM = trips.sumOf { it.distanceM }
        return estimate(distanceM, profile)
    }

    fun money(value: Double): String = String.format(Locale.US, "\$%.2f", value)

    fun fuel(value: Double, unitLabel: String): String =
        if (value < 10.0) String.format(Locale.US, "%.1f %s", value, unitLabel)
        else String.format(Locale.US, "%.0f %s", value, unitLabel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFuelScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { VehicleFuelPrefs.load(context) }
    val profile by VehicleFuelState.state.collectAsStateWithLifecycle()

    var name by remember(profile) { mutableStateOf(profile.vehicleName.ifBlank { "My car" }) }
    var unit by remember(profile) { mutableStateOf(profile.unit) }
    var economyText by remember(profile) { mutableStateOf(formatInputNumber(profile.economy)) }
    var priceText by remember(profile) { mutableStateOf(formatInputNumber(profile.fuelPrice)) }
    val economy = economyText.toDoubleOrNull()
    val price = priceText.toDoubleOrNull()
    val canSave = name.isNotBlank() && economy != null && economy > 0.0 && price != null && price > 0.0
    val previewProfile = VehicleFuelProfile(
        vehicleName = name,
        unit = unit,
        economy = economy ?: profile.economy,
        fuelPrice = price ?: profile.fuelPrice,
        configured = canSave
    )
    val sampleEstimate = FuelCost.estimate(25_000.0, previewProfile)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle & fuel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (profile.configured) {
                        TextButton(onClick = { VehicleFuelPrefs.clear(context) }) {
                            Text("Reset")
                        }
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
            VehicleFuelHero(profile = previewProfile, sampleEstimate = sampleEstimate)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Vehicle name") },
                        leadingIcon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FuelEconomyUnit.entries.forEach { option ->
                            FilterChip(
                                selected = unit == option,
                                onClick = { unit = option },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = economyText,
                        onValueChange = { economyText = cleanNumberInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(unit.economyLabel) },
                        suffix = { Text(unit.label) },
                        leadingIcon = { Icon(Icons.Filled.LocalGasStation, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = economyText.isNotBlank() && (economy == null || economy <= 0.0)
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = cleanNumberInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(unit.priceLabel) },
                        prefix = { Text("\$") },
                        leadingIcon = { Icon(Icons.Filled.Paid, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = priceText.isNotBlank() && (price == null || price <= 0.0)
                    )

                    Button(
                        onClick = {
                            VehicleFuelPrefs.save(
                                context,
                                VehicleFuelProfile(
                                    vehicleName = name.trim(),
                                    unit = unit,
                                    economy = economy ?: profile.economy,
                                    fuelPrice = price ?: profile.fuelPrice,
                                    configured = true
                                )
                            )
                            onBack()
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        Text("Save vehicle profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleFuelHero(
    profile: VehicleFuelProfile,
    sampleEstimate: FuelCostEstimate?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = VehicleFuelInk,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "TRIP COST SETUP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.68f)
            )
            Text(
                profile.displayName(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VehiclePreviewMetric(
                    label = profile.unit.label,
                    value = formatInputNumber(profile.economy),
                    modifier = Modifier.weight(1f)
                )
                VehiclePreviewMetric(
                    label = profile.unit.priceLabel,
                    value = FuelCost.money(profile.fuelPrice),
                    modifier = Modifier.weight(1f)
                )
            }
            sampleEstimate?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(VehicleFuelGreen)
                        )
                        Text(
                            "25 km estimate: ${FuelCost.money(it.cost)} / ${FuelCost.fuel(it.fuelAmount, it.fuelUnitLabel)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehiclePreviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun cleanNumberInput(value: String): String =
    value.filterIndexed { index, c ->
        c.isDigit() || (c == '.' && value.indexOf('.') == index)
    }.take(8)

private fun formatInputNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private val VehicleFuelInk = Color(0xFF17301F)
private val VehicleFuelGreen = Color(0xFF22C55E)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun VehicleFuelScreenPreview() {
    CarTripTheme {
        VehicleFuelHero(
            profile = VehicleFuelProfile(
                vehicleName = "Civic",
                unit = FuelEconomyUnit.L_PER_100_KM,
                economy = 7.1,
                fuelPrice = 1.62,
                configured = true
            ),
            sampleEstimate = FuelCostEstimate(
                fuelAmount = 1.8,
                fuelUnitLabel = "L",
                cost = 2.88
            )
        )
    }
}
