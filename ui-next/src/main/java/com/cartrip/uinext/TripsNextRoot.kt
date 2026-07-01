package com.cartrip.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
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
 * The bottom-nav home: a single shell [Scaffold] (top bar + a 5-tab bottom nav = the spec's Drive / Trips /
 * Insights / Map / More). Trip data is observed ONCE here and shared by the tabs that need it. Drive / Map /
 * More are roughed-in (read-only) surfaces that flesh out the product shape ahead of their engine-api work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell(onOpenTrip: (Long) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TripRepository.create(context) }
    val trips: List<TripSummary>? by repo.observeTrips().collectAsState(initial = null)
    // Tabs: 0 Drive, 1 Trips, 2 Insights, 3 Map, 4 More. Open on Trips (the populated surface).
    var tab by rememberSaveable { mutableStateOf(1) }
    val titles = listOf("Drive", "Trips", "Insights", "Map", "More")
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titles[tab]) },
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
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("Drive") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Trips") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text("Insights") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text("More") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DriveScreen()
                1 -> TripListContent(trips, onOpenTrip)
                2 -> InsightsContent(trips)
                3 -> MapHubScreen(trips, onOpenTrip)
                else -> MoreScreen()
            }
        }
    }
}
