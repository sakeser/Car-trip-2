package com.cartrip.uinext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cartrip.uinext.theme.CarTripNextTheme

/**
 * The :ui-next entry point: applies the module's own [CarTripNextTheme] (so it no longer inherits the legacy
 * host theme) and hosts its internal navigation (list -> detail/{id}). Self-contained, so the host (legacy
 * :app, via a debug entry) only calls this. [onExit] backs out of the whole :ui-next flow to the host;
 * system back pops detail -> list automatically, and exits via the host on the list.
 */
@Composable
fun TripsNextRoot(onExit: () -> Unit) {
    CarTripNextTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "list") {
                composable("list") {
                    TripListNextScreen(
                        onOpenTrip = { id -> nav.navigate("detail/$id") },
                        onBack = onExit,
                    )
                }
                composable(
                    "detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    TripDetailNextScreen(tripId = id, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
