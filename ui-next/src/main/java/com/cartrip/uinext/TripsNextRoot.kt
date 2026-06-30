package com.cartrip.uinext

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * The :ui-next entry point and its own internal navigation (list -> detail), self-contained so the host
 * (legacy :app, via a debug entry) never knows about the inner screens. [onExit] backs out of the whole
 * :ui-next flow to the host. System back pops detail -> list automatically; on the list it exits via the host.
 */
@Composable
fun TripsNextRoot(onExit: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            TripListNextScreen(
                onOpenTrip = { id -> nav.navigate("detail/$id") },
                onBack = onExit
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            TripDetailNextScreen(tripId = id, onBack = { nav.popBackStack() })
        }
    }
}
