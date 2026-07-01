package com.cartrip.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cartrip.engine.api.TripRepository
import com.cartrip.engine.api.TripSummary
import com.cartrip.uinext.theme.CarTripNextTheme

/**
 * The :ui-next entry point: applies the module's own [CarTripNextTheme] and hosts a small premium **app shell**
 * — a bottom-nav home (Trips + Health) plus a full-screen trip detail. Self-contained, so the host (legacy
 * :app, via a debug entry) only calls this; [onExit] backs out of the whole :ui-next flow to the host. System
 * back pops detail -> home automatically.
 */
@Composable
fun TripsNextRoot(onExit: () -> Unit) {
    CarTripNextTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeShell(onOpenTrip = { id -> nav.navigate("detail/$id") }, onExit = onExit)
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

/**
 * The bottom-nav home: a single shell [Scaffold] (top bar + bottom nav) that switches between the Trips list
 * and the Health (Driving Intelligence) overview. Trip data is observed ONCE here and shared by both tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell(onOpenTrip: (Long) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trips: List<TripSummary>? by repo.observeTrips().collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (tab == 0) "Trips" else "Health") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Trips") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text("Health") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> TripListContent(trips, onOpenTrip)
                else -> InsightsContent(trips)
            }
        }
    }
}
