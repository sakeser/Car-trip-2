package com.cartrip.analyzer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cartrip.analyzer.cloud.CloudPrefs
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.cloud.CloudSync
import com.cartrip.analyzer.cloud.GoogleAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cartrip.analyzer.record.RecordingService
import com.cartrip.analyzer.record.RecordingState
import com.cartrip.analyzer.ui.CarTripTheme
import com.cartrip.analyzer.ui.DebugScreen
import com.cartrip.analyzer.ui.GuideScreen
import com.cartrip.analyzer.ui.HomeScreen
import com.cartrip.analyzer.ui.InsightsScreen
import com.cartrip.analyzer.ui.TripDetailScreen
import com.cartrip.analyzer.ui.TripListScreen
import com.cartrip.analyzer.ui.TripViewModel
import com.cartrip.analyzer.ui.AutoRecordScreen
import com.cartrip.analyzer.ui.VehicleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarTripTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val vm: TripViewModel = viewModel()
    val nav = rememberNavController()
    val live by RecordingState.state.collectAsStateWithLifecycle()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startRecordingService(context)
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val repaired = vm.recoverInterruptedTrips()
        // One-time: fill in the Rev BF speeding severity for older trips (from stored limits, no network).
        runCatching { vm.backfillSpeedingSeverity() }
        CloudState.set { it.copy(email = CloudPrefs.email(context)) }
        if (repaired > 0) {
            CloudState.set {
                it.copy(lastMessage = "Recovered $repaired interrupted trip${if (repaired == 1) "" else "s"} as partial.")
            }
        }
        // Sync anything that ended without a normal stop (recovered partials, past failures).
        val synced = vm.syncPendingTrips()
        if (synced > 0) {
            CloudState.set {
                it.copy(lastMessage = "Synced $synced pending trip${if (synced == 1) "" else "s"} to Google Sheets.")
            }
        }
    }

    LaunchedEffect(live.completedTripId) {
        val id = live.completedTripId ?: return@LaunchedEffect
        nav.navigate("detail/$id") {
            popUpTo("home")
        }
        RecordingState.consumeCompletedTrip()
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        CloudState.set { it.copy(lastMessage = "Google access approved. Trips will sync.") }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            CloudPrefs.setEmail(context, account.email)
            CloudState.set { it.copy(email = account.email, lastMessage = "Connected.") }
            scope.launch {
                val intent = CloudSync.consentIntentIfNeeded(context)
                if (intent != null) consentLauncher.launch(intent)
            }
        } catch (e: ApiException) {
            CloudState.set { it.copy(lastMessage = "Sign-in failed (code ${e.statusCode}).") }
        }
    }

    fun connectCloud() {
        signInLauncher.launch(GoogleAuth.client(context).signInIntent)
    }

    fun disconnectCloud() {
        GoogleAuth.client(context).signOut()
        CloudPrefs.setEmail(context, null)
        CloudState.set { it.copy(email = null, lastMessage = "Disconnected.") }
    }

    fun start() {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (fine && notif) startRecordingService(context) else launcher.launch(permissions)
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStart = { start() },
                onStop = { stopRecordingService(context) },
                onViewTrips = { nav.navigate("trips") },
                onViewInsights = { nav.navigate("insights") },
                onLoadDemoData = {
                    vm.loadDemoData()
                    CloudState.set { it.copy(lastMessage = "Loading 30 days of sample trips...") }
                },
                onConnectCloud = { connectCloud() },
                onDisconnectCloud = { disconnectCloud() },
                onOpenGuide = { nav.navigate("guide") },
                onOpenDebug = { nav.navigate("debug") },
                onOpenVehicle = { nav.navigate("vehicle") },
                onOpenAutoRecord = { nav.navigate("autorecord") }
            )
        }
        composable("guide") {
            GuideScreen(onBack = { nav.popBackStack() })
        }
        composable("vehicle") {
            VehicleScreen(onBack = { nav.popBackStack() })
        }
        composable("autorecord") {
            AutoRecordScreen(onBack = { nav.popBackStack() })
        }
        composable("debug") {
            DebugScreen(onBack = { nav.popBackStack() })
        }
        composable("insights") {
            InsightsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onOpenTrip = { id -> nav.navigate("detail/$id") }
            )
        }
        composable("trips") {
            TripListScreen(
                viewModel = vm,
                onOpen = { id -> nav.navigate("detail/$id") },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            TripDetailScreen(
                tripId = id,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack() }
            )
        }
    }
}

private fun startRecordingService(context: Context) {
    val intent = Intent(context, RecordingService::class.java)
        .setAction(RecordingService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopRecordingService(context: Context) {
    val intent = Intent(context, RecordingService::class.java)
        .setAction(RecordingService.ACTION_STOP)
    context.startService(intent)
}
