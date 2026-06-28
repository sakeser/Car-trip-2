package com.cartrip.analyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cartrip.analyzer.analysis.FuelEstimator
import com.cartrip.analyzer.cloud.GasPrice
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

/**
 * Vehicle & fuel settings: the profile that drives per-trip fuel/cost estimates. Seeded with the
 * owner's 2023 Hyundai Tucson + a GTA fuel price. The "calibrate from your car" helper turns a
 * real car-reported combined L/100km into a [FuelEstimator.Vehicle.calibration] factor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { VehiclePrefs.load(context) }
    var year by remember { mutableStateOf(initial.year.toString()) }
    var make by remember { mutableStateOf(initial.make) }
    var model by remember { mutableStateOf(initial.model) }
    var city by remember { mutableStateOf(fmt(initial.cityL100)) }
    var hwy by remember { mutableStateOf(fmt(initial.hwyL100)) }
    var price by remember { mutableStateOf(fmt(initial.pricePerL)) }
    var cal by remember { mutableStateOf(fmt(initial.calibration)) }
    var actual by remember { mutableStateOf("") }
    var autoPrice by remember { mutableStateOf(VehiclePrefs.autoUpdatePrice(context)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun current(): FuelEstimator.Vehicle = FuelEstimator.Vehicle(
        year = year.toIntOrNull() ?: initial.year,
        make = make.ifBlank { initial.make },
        model = model.ifBlank { initial.model },
        cityL100 = city.toDoubleOrNull() ?: initial.cityL100,
        hwyL100 = hwy.toDoubleOrNull() ?: initial.hwyL100,
        pricePerL = price.toDoubleOrNull() ?: initial.pricePerL,
        calibration = cal.toDoubleOrNull() ?: 1.0,
        fuelType = initial.fuelType,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle & fuel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your vehicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(year, { year = it }, label = { Text("Year") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(make, { make = it }, label = { Text("Make") }, modifier = Modifier.weight(1.4f), singleLine = true)
            }
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Fuel economy (L/100km)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(hwy, { hwy = it }, label = { Text("Highway") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
            OutlinedTextField(price, { price = it }, label = { Text("Fuel price ($/L)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)

            // Auto-update the price from the free Ontario weekly survey (Toronto regular average).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-update price", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${GasPrice.SOURCE_LABEL}, refreshed daily. You can still edit it manually.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPrice,
                    onCheckedChange = { on ->
                        autoPrice = on
                        VehiclePrefs.setAutoUpdatePrice(context, on)
                        if (on) scope.launch {
                            val p = GasPrice.refreshNow(context)
                            if (p != null) {
                                price = fmt(p)
                                snackbar.showSnackbar(String.format(Locale.US, "Price updated to $%.2f/L", p))
                            } else {
                                snackbar.showSnackbar("Couldn't fetch the latest price")
                            }
                        }
                    }
                )
            }

            val v = current()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        String.format(Locale.US, "Combined (rated): %.1f L/100km", FuelEstimator.combinedL100(v)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        String.format(Locale.US, "Effective (calibrated %.2f×): %.1f L/100km",
                            v.calibration, FuelEstimator.combinedL100(v) * v.calibration),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                    )
                }
            }

            Text("Calibration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Adjusts every estimate to match your car. Enter the combined L/100km your car reports and tap Calibrate, or edit the factor directly.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(actual, { actual = it }, label = { Text("Car L/100km") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(cal, { cal = it }, label = { Text("Factor") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
            OutlinedButton(
                onClick = {
                    val a = actual.toDoubleOrNull()
                    val rated = FuelEstimator.combinedL100(current())
                    if (a != null && a > 0 && rated > 0) {
                        cal = fmt(a / rated)
                        scope.launch { snackbar.showMessage("Calibrated to ${fmt(a / rated)}×") }
                    } else {
                        scope.launch { snackbar.showMessage("Enter your car's combined L/100km first.") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Calibrate from car reading") }

            Button(
                onClick = {
                    VehiclePrefs.save(context, current())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}

private fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString()
    else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

private suspend fun SnackbarHostState.showMessage(msg: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(msg)
}
