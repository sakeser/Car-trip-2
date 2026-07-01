package com.cartrip.analyzer.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cartrip.engine.R as EngineR
import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.analysis.TripAnalyzer
import com.cartrip.analyzer.cloud.CloudPrefs
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.cloud.ConnectedFeaturesPrefs
import com.cartrip.analyzer.cloud.GoogleAuth
import com.cartrip.analyzer.cloud.RoutesClient
import com.cartrip.analyzer.cloud.RoutesConfig
import com.cartrip.analyzer.cloud.SpeedLimits
import com.cartrip.analyzer.cloud.TripSync
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.GnssMeasurementSample
import com.cartrip.analyzer.data.GnssSample
import com.cartrip.analyzer.data.LocationSample
import com.cartrip.analyzer.data.MotionSample
import com.cartrip.analyzer.data.TripEndReason
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.data.TripFinalizer
import com.cartrip.analyzer.data.TripStatus
import com.cartrip.analyzer.export.TripExcel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

class RecordingService : Service(), SensorEventListener, LocationListener {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var db: AppDatabase
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var usingFused = false

    @Volatile private var recording = false
    @Volatile private var tripId: Long = 0
    private var tickerJob: Job? = null

    private val bufferLock = Any()
    private val locBuffer = ArrayList<LocationSample>()
    private val motionBuffer = ArrayList<MotionSample>()
    private val gnssBuffer = ArrayList<GnssSample>()
    private val gnssMeasBuffer = ArrayList<GnssMeasurementSample>()
    private var gnssMeasCallback: android.location.GnssMeasurementsEvent.Callback? = null

    // live metric state (touched on main thread only)
    private var lastLoc: LocationSample? = null
    private var liveDistance = 0.0
    private var liveSpeedKmh = 0.0
    private var maxSpeedMps = 0.0
    private var lastBrakeEvt = -1e9
    private var lastAccelEvt = -1e9
    private var lastCornerEvt = -1e9
    private var brakeCount = 0
    private var accelCount = 0
    private var cornerCount = 0
    private var gpsFixes = 0

    // latest sensor values
    private var ax = 0.0; private var ay = 0.0; private var az = 0.0
    private var gx = 0.0; private var gy = 0.0; private var gz = 0.0
    private var grx = 0.0; private var gry = 0.0; private var grz = 0.0
    // Accelerometer "is the car driving" signal for the auto-record motion-confirm — works WITHOUT GPS
    // (e.g. starting in an underground garage). The sample-to-sample change cancels the DC gravity
    // component, so road vibration shows up for either the linear-accel or raw-accel sensor.
    private var prevAx = 0.0; private var prevAy = 0.0; private var prevAz = 0.0
    private var haveJerkRef = false
    private var vibrationEma = 0.0
    private var maxVibrationEma = 0.0
    private var lastMotionWrite = 0L
    private var motionSensorRegistered = false
    private var sensorRestartAttempts = 0
    private var lastSensorRestartAtWall = 0L
    private var sawDriving = false
    private var lastDrivingLocT = 0L
    // Rolling window of recent (monotonic tLoc, speedMps) so an auto-stop can retrospectively find
    // the moment the car actually came to rest, instead of trimming to a fixed grace.
    private val recentSpeedTrack = ArrayDeque<Pair<Long, Double>>()
    private var autoStopRequested = false
    // Leading (monotonic tLoc, speedMps) window from the START of the trip across the warm-up and
    // pull-away, so an auto-record START can retrospectively trim the parked prefix (warm-up / backing
    // out). Held open for a few minutes (not just "until moving") because the real departure must be
    // confirmed as *sustained* motion, not a brief parking-lot creep. See [AutoStart].
    private val startSpeedTrack = ArrayList<Pair<Long, Double>>()
    private var startTrackOpen = true
    private var startTrackFirstT = 0L
    // Auto-record (Rev U): this trip was auto-armed; motion-confirm + stop-grace timers.
    @Volatile private var autoStarted = false
    private var motionConfirmJob: Job? = null
    private var graceStopJob: Job? = null
    private var locationSampleCount = 0
    private var motionSampleCount = 0
    private var lastLocationAtWall = 0L
    private var lastMotionAtWall = 0L
    private var gpsGapCount = 0
    private var gpsGapOpen = false
    private var stoppingNormally = false

    // GNSS quality accumulators (GnssStatus callback, main thread). Aggregated into a per-trip summary.
    private var gnssCallback: GnssStatus.Callback? = null
    private var gnssUpdates = 0
    private var gnssSatsUsedSum = 0L
    private var gnssCn0Sum = 0.0
    private var gnssCn0Count = 0L
    private var gnssTopCn0 = 0.0
    private var gnssL5Seen = false
    // Latest per-callback snapshot, sampled into gnss_samples on a fixed cadence.
    private var gnssLastSatsUsed = 0
    private var gnssLastSatsVisible = 0
    private var gnssLastMeanCn0 = 0.0
    private var gnssLastTopCn0 = 0.0
    private var gnssLastL5 = false
    private var gnssHasSnapshot = false

    private data class TrimCutoff(val locationT: Long, val motionT: Long)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_AUTO_ARM -> autoArm()
            ACTION_AUTO_STOP_GRACE -> autoStopGrace()
            ACTION_CHARGING_RESUMED -> { graceStopJob?.cancel(); graceStopJob = null }
            else -> { autoStarted = false; startRecording() }
        }
        return START_NOT_STICKY
    }

    /** @return true if recording actually started; false if the foreground start was blocked. */
    private fun startRecording(): Boolean {
        if (recording) return true
        resetSessionState()
        if (!startInForeground()) {
            // Couldn't enter the location foreground service. On Android 14 a *background*-initiated
            // auto-arm without "Allow all the time" location throws here — bail without crashing (a
            // crash would crash-loop, since the watcher re-arms on each process restart).
            onForegroundStartBlocked()
            return false
        }
        // Tactile cue: a firm buzz when manually recording; a lighter "armed" tick for an auto provisional
        // (it firms up to recordingStarted once motion is confirmed).
        if (autoStarted) Haptics.armed(this) else Haptics.recordingStarted(this)
        acquireWakeLock()
        scope.launch {
            val startWall = System.currentTimeMillis()
            TripFinalizer.recoverInterruptedTrips(db.tripDao(), startWall)
            val id = db.tripDao().insertTrip(
                TripEntity(
                    startTime = startWall,
                    endTime = 0,
                    status = TripStatus.RECORDING,
                    endReason = "",
                    lastCheckpointAt = startWall
                )
            )
            tripId = id
            recording = true
            withContext(Dispatchers.Main) {
                registerSensors()
                requestLocation()
            }
            RecordingState.update {
                it.copy(recording = true, tripId = id, startTime = startWall)
            }
            startTicker()
        }
        return true
    }

    private fun stopRecording(trimCutoff: TrimCutoff? = null) {
        if (!recording) {
            scope.launch {
                val recovered = db.tripDao().getLatestTripWithStatus(TripStatus.RECORDING)
                val recoveredId = recovered?.id
                if (recoveredId != null) {
                    val endWall = System.currentTimeMillis()
                    TripFinalizer.finalizeTrip(
                        dao = db.tripDao(),
                        tripId = recoveredId,
                        endWall = endWall,
                        requestedStatus = TripStatus.PARTIAL,
                        requestedReason = TripEndReason.APP_RECOVERY
                    )
                    RecordingState.completeTrip(recoveredId)
                }
                withContext(Dispatchers.Main) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            return
        }
        stoppingNormally = true
        recording = false
        // Two firm buzzes the instant the stop is registered (immediate tactile confirmation is the point).
        // Finalize/save happens just after; on the rare finalize failure the trip is recovered as PARTIAL.
        Haptics.stopped(this)
        tickerJob?.cancel()
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }
        try {
            if (usingFused) locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        } catch (_: Exception) {
        }
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
        unregisterGnss()
        val finishedId = tripId
        scope.launch {
            flushBuffers(writeCheckpoint = false)
            val dao = db.tripDao()
            writeCheckpoint()
            if (trimCutoff != null) {
                dao.deleteLocationsAfter(finishedId, trimCutoff.locationT)
                dao.deleteMotionsAfter(finishedId, trimCutoff.motionT)
                dao.deleteGnssSamplesAfter(finishedId, trimCutoff.motionT)
                dao.deleteGnssMeasurementsAfter(finishedId, trimCutoff.motionT)
            }
            // Start-side trim (auto trips only): an auto-armed trip starts recording while still parked
            // (warm-up / backing out), so drop the leading stationary prefix the same retrospective way
            // the stop side drops the idle tail. Done BEFORE finalize so the analyzer recomputes
            // distance/duration over the trimmed range — and the too-short discard below then catches a
            // trip trimmed down to nothing (e.g. a brief crawl that never became a real drive).
            if (autoStarted) {
                val startCut = AutoStart.retrospectiveStartTime(startSpeedTrack.toList())
                if (startCut != null) {
                    dao.deleteLocationsBefore(finishedId, startCut)
                    dao.deleteMotionsBefore(finishedId, startCut)
                    dao.deleteGnssSamplesBefore(finishedId, startCut)
                    dao.deleteGnssMeasurementsBefore(finishedId, startCut)
                    AutoRecordLog.add(this@RecordingService, "start-trimmed parked prefix to first motion")
                }
            }
            val finalized = TripFinalizer.finalizeTrip(
                dao = dao,
                tripId = finishedId,
                endWall = System.currentTimeMillis(),
                requestedStatus = TripStatus.COMPLETED,
                requestedReason = if (trimCutoff == null) TripEndReason.MANUAL_STOP else TripEndReason.AUTO_STOP
            )
            if (finalized != null) {
                val updated = finalized.trip
                val analysis = finalized.analysis

                // Discard a trivially short trip (e.g. an accidental Start→Stop, or a brief move that
                // never became a real drive): don't save it — delete it and alert the user instead.
                if (updated.distanceM < MIN_TRIP_DISTANCE_M || updated.durationS < MIN_TRIP_DURATION_S) {
                    dao.deleteTripWithData(finishedId)
                    purgeExpiredRawSamples()
                    postNotRecordedNotice()
                    CloudState.set { it.copy(lastMessage = "Trip not recorded - it was too short (under 5 m / 10 s).") }
                    withContext(Dispatchers.Main) {
                        RecordingState.reset()   // back to idle without opening a (deleted) summary
                        stopForegroundCompat()
                        stopSelf()
                    }
                    return@launch
                }

                purgeExpiredRawSamples()
                // The trip is analyzed and persisted — open the summary NOW. Network enrichment
                // (traffic ETA, OSM speed limits, Sheets sync) must never block the UI.
                RecordingState.completeTrip(finishedId)
                // Best-effort, time-bounded so a slow/hung network can't keep the service alive.
                withTimeoutOrNull(120_000L) {
                    if (updated.status == TripStatus.COMPLETED) {
                        val eta = fetchLiveEta(analysis)
                        if (eta != null) {
                            dao.updateTrip(
                                updated.copy(
                                    googleEtaTrafficS = eta.trafficS,
                                    googleEtaFreeFlowS = eta.freeFlowS,
                                    etaSource = "live",
                                    etaFetchedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (ConnectedFeaturesPrefs.enabled(applicationContext)) {
                            runCatching { SpeedLimits.refreshForTrip(dao, finishedId) }
                        }
                    }
                    val exportTrip = dao.getTrip(finishedId) ?: updated
                    runCatching { TripExcel.write(applicationContext, exportTrip, analysis) }
                    if (CloudPrefs.autoSync(applicationContext) &&
                        GoogleAuth.lastAccount(applicationContext) != null
                    ) {
                        CloudState.set { it.copy(syncing = true) }
                        val sync = TripSync.syncOne(applicationContext, dao, finishedId, force = false)
                        CloudState.set {
                            it.copy(
                                syncing = false,
                                lastMessage = if (sync.isSuccess) "Trip synced to Google Sheets."
                                else "Sheets sync will retry next time the app opens."
                            )
                        }
                    }
                }
            } else {
                RecordingState.completeTrip(finishedId)
            }
            withContext(Dispatchers.Main) {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    /** Auto-record: start a provisional trip and confirm real motion before keeping it. */
    private fun autoArm() {
        if (recording) {
            AutoRecordLog.add(this, "AUTO_ARM ignored (already recording); cancel grace")
            graceStopJob?.cancel(); graceStopJob = null; return
        }
        autoStarted = true
        if (!startRecording()) {
            // Foreground start was blocked (e.g. background location not granted) — onForegroundStartBlocked
            // already logged it + posted the notice + stopped us. Don't log a phantom "provisional started"
            // or schedule a motion-confirm against a recording that never began.
            return
        }
        AutoRecordLog.add(this, "AUTO_ARM: provisional trip started, confirming motion (${AutoRecordPrefs.MOTION_CONFIRM_MS / 1000}s)")
        motionConfirmJob?.cancel()
        motionConfirmJob = scope.launch {
            delay(AutoRecordPrefs.MOTION_CONFIRM_MS)
            withContext(Dispatchers.Main) {
                if (recording && autoStarted) {
                    // Confirm a real drive by GPS speed OR accelerometer vibration — the latter works
                    // when the drive starts with no GPS (underground garage).
                    val gpsOk = maxSpeedMps * 3.6 >= AutoRecordPrefs.MIN_SPEED_KMH
                    val sensorOk = maxVibrationEma >= SENSOR_MOTION_VIB
                    val stats = "gps ${"%.0f".format(maxSpeedMps * 3.6)} km/h, vib ${"%.2f".format(maxVibrationEma)}"
                    if (!gpsOk && !sensorOk) {
                        AutoRecordLog.add(this@RecordingService, "motion-confirm FAILED ($stats) -> discard")
                        discardRecording()
                    } else {
                        AutoRecordLog.add(this@RecordingService, "motion-confirm OK via ${if (gpsOk) "gps" else "sensor"} ($stats) -> keeping trip")
                        Haptics.recordingStarted(this@RecordingService) // armed → confirmed real trip
                    }
                }
            }
        }
    }

    /** In-car trigger dropped: stop after a grace, unless charging resumes first (CHARGING_RESUMED). */
    private fun autoStopGrace() {
        if (!recording || !autoStarted) return
        AutoRecordLog.add(this, "AUTO_STOP grace (${AutoRecordPrefs.STOP_GRACE_MS / 1000}s)")
        graceStopJob?.cancel()
        graceStopJob = scope.launch {
            delay(AutoRecordPrefs.STOP_GRACE_MS)
            withContext(Dispatchers.Main) {
                if (recording) {
                    // Trim the trip back to the moment the car actually came to rest (not the unplug +
                    // grace tail), the same retrospective rule the 6-min idle auto-stop uses. A non-null
                    // cutoff also marks the trip AUTO_STOP rather than MANUAL_STOP.
                    val restT = AutoStop.retrospectiveEndTime(recentSpeedTrack.toList())
                        ?: lastDrivingLocT.takeIf { it > 0L }
                    val trim = restT?.let { TrimCutoff(it + AUTO_STOP_END_GRACE_MS, it + AUTO_STOP_END_GRACE_MS) }
                    AutoRecordLog.add(this@RecordingService, if (trim != null) "auto-stopped (trimmed to rest)" else "auto-stopped")
                    stopRecording(trim)
                }
            }
        }
    }

    /** Tear down a provisional recording that never moved — delete it silently (no saved trip). */
    private fun discardRecording() {
        if (!recording) return
        Haptics.disarmed(this) // soft tick: armed but gave up (not a drive)
        recording = false
        autoStarted = false
        tickerJob?.cancel()
        motionConfirmJob?.cancel()
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { if (usingFused) locationCallback?.let { fusedClient?.removeLocationUpdates(it) } } catch (_: Exception) {}
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        unregisterGnss()
        val discardId = tripId
        scope.launch {
            val dao = db.tripDao()
            runCatching { dao.deleteTripWithData(discardId) }
            withContext(Dispatchers.Main) {
                RecordingState.reset()
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun resetSessionState() {
        lastLoc = null
        liveDistance = 0.0
        liveSpeedKmh = 0.0
        maxSpeedMps = 0.0
        lastBrakeEvt = -1e9
        lastAccelEvt = -1e9
        lastCornerEvt = -1e9
        brakeCount = 0
        accelCount = 0
        cornerCount = 0
        gpsFixes = 0
        sawDriving = false
        lastDrivingLocT = 0L
        recentSpeedTrack.clear()
        startSpeedTrack.clear()
        startTrackOpen = true
        startTrackFirstT = 0L
        autoStopRequested = false
        lastMotionWrite = 0L
        motionSensorRegistered = false
        sensorRestartAttempts = 0
        lastSensorRestartAtWall = 0L
        locationSampleCount = 0
        motionSampleCount = 0
        prevAx = 0.0; prevAy = 0.0; prevAz = 0.0
        haveJerkRef = false
        vibrationEma = 0.0
        maxVibrationEma = 0.0
        lastLocationAtWall = 0L
        lastMotionAtWall = 0L
        gpsGapCount = 0
        gpsGapOpen = false
        stoppingNormally = false
        gnssUpdates = 0
        gnssSatsUsedSum = 0L
        gnssCn0Sum = 0.0
        gnssCn0Count = 0L
        gnssTopCn0 = 0.0
        gnssL5Seen = false
        gnssLastSatsUsed = 0
        gnssLastSatsVisible = 0
        gnssLastMeanCn0 = 0.0
        gnssLastTopCn0 = 0.0
        gnssLastL5 = false
        gnssHasSnapshot = false
        synchronized(bufferLock) { gnssBuffer.clear(); gnssMeasBuffer.clear() }
    }

    /**
     * Snapshot Google's live traffic estimate for this trip's start->end the moment it ends,
     * so it can later be compared against the actual time. Returns null (and is skipped) when
     * there's no clear start/end, no key, or the call fails — the trip still saves fine.
     */
    private fun fetchLiveEta(analysis: TripAnalysis): RoutesClient.RouteResult? {
        if (!ConnectedFeaturesPrefs.enabled(applicationContext)) return null
        val start = analysis.points.firstOrNull() ?: return null
        val end = analysis.points.lastOrNull() ?: return null
        if (GeoUtils.haversine(start.lat, start.lon, end.lat, end.lon) < 300.0) return null
        val key = RoutesConfig.apiKey(applicationContext) ?: return null
        return runCatching {
            RoutesClient.computeRoute(
                apiKey = key,
                androidPackage = RoutesConfig.androidPackage(applicationContext),
                androidCertSha1 = RoutesConfig.signingSha1(applicationContext),
                originLat = start.lat, originLon = start.lon,
                destLat = end.lat, destLon = end.lon,
                departureRfc3339 = RoutesConfig.nowDeparture()
            )
        }.getOrNull()
    }

    private suspend fun purgeExpiredRawSamples() {
        val cutoff = System.currentTimeMillis() - RAW_SENSOR_RETENTION_MS
        val dao = db.tripDao()
        dao.deleteRawLocationsForCompletedTripsBefore(cutoff)
        dao.deleteRawMotionsForCompletedTripsBefore(cutoff)
        dao.deleteRawGnssForCompletedTripsBefore(cutoff)
        dao.deleteRawGnssMeasurementsForCompletedTripsBefore(cutoff)
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    /**
     * Hold a partial wake lock while recording so the CPU stays awake with the screen off —
     * otherwise non-wakeup sensors (accelerometer/gyro/gravity) stop delivering and the motion
     * track gets gaps. Capped at 6h as a safety net against leaks.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cartrip:recording").apply {
            setReferenceCounted(false)
            runCatching { acquire(6L * 60L * 60L * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun registerSensors() {
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        motionSensorRegistered = linear?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        if (gyro != null) {
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
        if (gravity != null) {
            sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun restartSensors() {
        runCatching { sensorManager.unregisterListener(this) }
        registerSensors()
    }

    /**
     * Detect motion-sensor starvation while GPS is alive and ask for a re-register. Covers both the
     * trip-786 case (callbacks never start) and a mid-trip stall (callbacks die after some samples):
     * a healthy ~50 Hz stream keeps [lastMotionAtWall] fresh, so this only fires when motion has
     * actually gone silent. Bounded by [MAX_SENSOR_RESTARTS] with a cooldown to avoid thrashing.
     */
    private fun shouldRestartMotionSensors(now: Long, start: Long): Boolean {
        if (!recording || !motionSensorRegistered || start <= 0L) return false
        if (sensorRestartAttempts >= MAX_SENSOR_RESTARTS) return false
        if (now - start < MOTION_STALL_RESTART_MS) return false       // warm-up grace
        if (locationSampleCount < 5) return false                     // require GPS to be alive
        if (lastSensorRestartAtWall > 0L && now - lastSensorRestartAtWall < SENSOR_RESTART_COOLDOWN_MS) return false
        // Stalled if motion never arrived, or the stream has been silent past the stall threshold.
        return if (lastMotionAtWall <= 0L) {
            now - start >= MOTION_STALL_RESTART_MS
        } else {
            now - lastMotionAtWall >= MOTION_STALL_RESTART_MS
        }
    }

    private fun requestLocation() {
        val playOk = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        if (playOk) startFused() else startGps()
        registerGnss()
    }

    /**
     * Listen to raw GNSS satellite status for a per-trip quality summary (sat count, C/N0, L5). This
     * does not change positioning (fused location still drives the track) — it explains data quality
     * and lets analysis downweight confidence when the sky view was poor.
     */
    private fun registerGnss() {
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) = accumulateGnss(status)
        }
        try {
            locationManager.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper()))
            gnssCallback = cb
        } catch (_: SecurityException) {
        }
        // Raw per-satellite measurements (carrier phase + Doppler) — only when high-precision logging is
        // on (it's voluminous), for the lane-detection R&D. Failure here never affects normal recording.
        if (GnssLoggingPrefs.rawEnabled(this)) registerGnssMeasurements()
    }

    private fun registerGnssMeasurements() {
        val cb = object : android.location.GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: android.location.GnssMeasurementsEvent) {
                if (!recording) return
                val t = SystemClock.elapsedRealtime()
                val out = ArrayList<GnssMeasurementSample>(event.measurements.size)
                for (m in event.measurements) {
                    out.add(
                        GnssMeasurementSample(
                            tripId = tripId, t = t,
                            svid = m.svid,
                            constellation = m.constellationType,
                            cn0 = m.cn0DbHz,
                            carrierFreqHz = if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toDouble() else 0.0,
                            pseudorangeRateMps = m.pseudorangeRateMetersPerSecond,
                            pseudorangeRateUncMps = m.pseudorangeRateUncertaintyMetersPerSecond,
                            adrMeters = m.accumulatedDeltaRangeMeters,
                            adrState = m.accumulatedDeltaRangeState,
                            adrUncMeters = m.accumulatedDeltaRangeUncertaintyMeters
                        )
                    )
                }
                synchronized(bufferLock) { gnssMeasBuffer.addAll(out) }
            }
        }
        try {
            locationManager.registerGnssMeasurementsCallback(cb, Handler(Looper.getMainLooper()))
            gnssMeasCallback = cb
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterGnss() {
        gnssCallback?.let { runCatching { locationManager.unregisterGnssStatusCallback(it) } }
        gnssCallback = null
        gnssMeasCallback?.let { runCatching { locationManager.unregisterGnssMeasurementsCallback(it) } }
        gnssMeasCallback = null
    }

    private fun accumulateGnss(status: GnssStatus) {
        if (!recording) return
        val visible = status.satelliteCount
        var used = 0
        var cn0Sum = 0.0
        var top = 0.0
        var l5 = false
        for (i in 0 until visible) {
            if (!status.usedInFix(i)) continue
            used++
            val cn0 = status.getCn0DbHz(i).toDouble()
            cn0Sum += cn0
            if (cn0 > top) top = cn0
            // L5/E5a/B2a band (~1176 MHz) — dual-frequency improves multipath rejection in cities.
            if (status.hasCarrierFrequencyHz(i)) {
                val f = status.getCarrierFrequencyHz(i)
                if (f in 1.164e9f..1.189e9f) l5 = true
            }
        }
        val meanCn0 = if (used > 0) cn0Sum / used else 0.0

        // Per-trip aggregate.
        gnssCn0Sum += cn0Sum
        gnssCn0Count += used
        if (top > gnssTopCn0) gnssTopCn0 = top
        if (l5) gnssL5Seen = true
        gnssSatsUsedSum += used
        gnssUpdates++

        // Latest snapshot (for per-window sampling) + live state (for the debug screen).
        gnssLastSatsUsed = used
        gnssLastSatsVisible = visible
        gnssLastMeanCn0 = meanCn0
        gnssLastTopCn0 = top
        gnssLastL5 = l5
        gnssHasSnapshot = true
        RecordingState.update {
            it.copy(gnssSatsUsed = used, gnssSatsVisible = visible, gnssCn0 = meanCn0, gnssL5 = l5)
        }
    }

    /** Buffer a per-window GNSS quality sample from the latest callback snapshot. */
    private fun sampleGnssWindow() {
        if (!gnssHasSnapshot) return
        val sample = GnssSample(
            tripId = tripId,
            t = SystemClock.elapsedRealtime(),
            satsUsed = gnssLastSatsUsed,
            satsVisible = gnssLastSatsVisible,
            meanCn0 = gnssLastMeanCn0,
            topCn0 = gnssLastTopCn0,
            l5 = gnssLastL5
        )
        synchronized(bufferLock) { gnssBuffer.add(sample) }
    }

    private fun startFused() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        fusedClient = client
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) handleLocation(location)
            }
        }
        locationCallback = cb
        if (!requestFused(LOCATION_INTERVAL_MS, LOCATION_FASTEST_INTERVAL_MS)) startGps()
    }

    /** (Re)request fused updates at the given cadence. Used for the normal fast rate and the battery-saving
     *  throttled rate. Returns false if location access was revoked (caller falls back / stops). */
    private fun requestFused(intervalMs: Long, fastestMs: Long): Boolean {
        val client = fusedClient ?: return false
        val cb = locationCallback ?: return false
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(fastestMs)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        return try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            usingFused = true
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun startGps() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
            }
        } catch (_: SecurityException) {
        }
    }

    private fun startTicker() {
        tickerJob = scope.launch {
            var counter = 0
            while (recording) {
                delay(1000)
                counter++
                val now = System.currentTimeMillis()
                val start = RecordingState.state.value.startTime
                val elapsed = if (start > 0) (now - start) / 1000 else 0
                val gpsSignalLost = lastLocationAtWall > 0L &&
                    now - lastLocationAtWall >= GPS_SIGNAL_LOST_MS
                if (gpsSignalLost && !gpsGapOpen) {
                    gpsGapOpen = true
                    gpsGapCount++
                }
                if (shouldRestartMotionSensors(now, start)) {
                    sensorRestartAttempts++
                    lastSensorRestartAtWall = now
                    withContext(Dispatchers.Main) { restartSensors() }
                }
                if (counter % 3 == 0) sampleGnssWindow()
                RecordingState.update {
                    it.copy(
                        elapsedS = elapsed,
                        gpsSignalLost = gpsSignalLost,
                        lastGpsAgeS = if (lastLocationAtWall > 0L) (now - lastLocationAtWall) / 1000 else 0L,
                        motionSamples = motionSampleCount,
                        sensorRestarts = sensorRestartAttempts
                    )
                }
                if (counter % 2 == 0) flushBuffers()
            }
        }
    }

    private suspend fun flushBuffers(writeCheckpoint: Boolean = true) {
        val locs: List<LocationSample>
        val motions: List<MotionSample>
        val gnss: List<GnssSample>
        val gnssMeas: List<GnssMeasurementSample>
        synchronized(bufferLock) {
            locs = ArrayList(locBuffer); locBuffer.clear()
            motions = ArrayList(motionBuffer); motionBuffer.clear()
            gnss = ArrayList(gnssBuffer); gnssBuffer.clear()
            gnssMeas = ArrayList(gnssMeasBuffer); gnssMeasBuffer.clear()
        }
        if (locs.isNotEmpty()) db.tripDao().insertLocations(locs)
        if (motions.isNotEmpty()) db.tripDao().insertMotions(motions)
        // GNSS is diagnostic — never let a bad batch abort the core location/motion flush.
        if (gnss.isNotEmpty()) runCatching { db.tripDao().insertGnssSamples(gnss) }
        if (gnssMeas.isNotEmpty()) runCatching { db.tripDao().insertGnssMeasurements(gnssMeas) }
        if (writeCheckpoint && tripId > 0L) {
            writeCheckpoint()
        }
    }

    private suspend fun writeCheckpoint() {
        val id = tripId
        if (id <= 0L) return
        val start = RecordingState.state.value.startTime
        val now = System.currentTimeMillis()
        db.tripDao().updateRecordingCheckpoint(
            id = id,
            durationS = if (start > 0L) (now - start) / 1000.0 else 0.0,
            distanceM = liveDistance,
            maxSpeedMps = maxSpeedMps,
            hardAccelCount = accelCount,
            hardBrakeCount = brakeCount,
            hardCornerCount = cornerCount,
            checkpointAt = now,
            lastLocationAt = lastLocationAtWall,
            lastMotionAt = lastMotionAtWall,
            locationSampleCount = locationSampleCount,
            motionSampleCount = motionSampleCount,
            gpsGapCount = gpsGapCount
        )
        if (gnssUpdates > 0) {
            db.tripDao().updateTripGnss(
                id = id,
                avgSats = gnssSatsUsedSum.toDouble() / gnssUpdates,
                avgCn0 = if (gnssCn0Count > 0) gnssCn0Sum / gnssCn0Count else 0.0,
                topCn0 = gnssTopCn0,
                l5Seen = gnssL5Seen,
                count = gnssUpdates
            )
        }
    }

    // ---- Location handling (shared by fused callback and GPS listener) ----

    private fun handleLocation(loc: Location) {
        if (!recording || tripId == 0L) return
        val nowWall = System.currentTimeMillis()
        val tMono = if (loc.elapsedRealtimeNanos > 0) loc.elapsedRealtimeNanos / 1_000_000L
        else SystemClock.elapsedRealtime()
        val gpsSpeed =
            if (loc.hasSpeed() && loc.speed >= 0f && loc.speed <= MAX_SPEED) loc.speed.toDouble()
            else -1.0

        val sample = LocationSample(
            tripId = tripId,
            t = tMono,
            lat = loc.latitude,
            lon = loc.longitude,
            speed = gpsSpeed,
            bearing = if (loc.hasBearing()) loc.bearing.toDouble() else -1.0,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0,
            bearingAccuracy = if (loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees.toDouble() else 0.0,
            speedAccuracy = if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond.toDouble() else 0.0,
            verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters.toDouble() else 0.0
        )
        synchronized(bufferLock) { locBuffer.add(sample) }
        locationSampleCount++
        lastLocationAtWall = nowWall
        gpsGapOpen = false
        gpsFixes++

        val prev = lastLoc
        if (prev != null) {
            val dt = (tMono - prev.t) / 1000.0
            if (dt in 0.2..10.0) {
                val d = GeoUtils.haversine(prev.lat, prev.lon, loc.latitude, loc.longitude)
                val implied = d / dt
                val outlier = implied > MAX_SPEED && gpsSpeed < 0
                if (!outlier) {
                    val vB = if (gpsSpeed >= 0) gpsSpeed else min(implied, MAX_SPEED)
                    val vA = if (prev.speed in 0.0..MAX_SPEED) prev.speed else 0.0
                    liveDistance += d
                    if (vB > maxSpeedMps) maxSpeedMps = vB
                    liveSpeedKmh = vB * 3.6
                    updateAutoStop(tMono, vB)
                    val tSec = tMono / 1000.0
                    val longAccel = (vB - vA) / dt
                    if (abs(longAccel) <= 8.0) {
                        if (longAccel >= TripAnalyzer.HARD_ACCEL && tSec - lastAccelEvt >= 2.0) {
                            accelCount++; lastAccelEvt = tSec
                        }
                        if (-longAccel >= TripAnalyzer.HARD_BRAKE && tSec - lastBrakeEvt >= 2.0) {
                            brakeCount++; lastBrakeEvt = tSec
                        }
                    }
                    if (vB > 5.0 && sample.bearing >= 0 && prev.bearing >= 0) {
                        val yawRate =
                            Math.toRadians(GeoUtils.angleDiffDeg(sample.bearing, prev.bearing)) / dt
                        val lat = vB * yawRate
                        if (abs(lat) >= TripAnalyzer.HARD_CORNER && abs(lat) <= 12.0 &&
                            tSec - lastCornerEvt >= 2.0
                        ) {
                            cornerCount++; lastCornerEvt = tSec
                        }
                    }
                    lastLoc = sample
                }
                // outlier: keep previous reference, drop this point from live calc
            } else if (dt > 10.0) {
                lastLoc = sample // signal gap, resync reference
                updateAutoStop(tMono, if (gpsSpeed >= 0) gpsSpeed else 0.0)
            }
            // dt < 0.2: near-duplicate, ignore for live calc
        } else {
            lastLoc = sample
            updateAutoStop(tMono, if (gpsSpeed >= 0) gpsSpeed else 0.0)
        }

        RecordingState.update {
            it.copy(
                distanceM = liveDistance,
                speedKmh = liveSpeedKmh,
                maxSpeedKmh = maxSpeedMps * 3.6,
                hardBrake = brakeCount,
                hardAccel = accelCount,
                hardCorner = cornerCount,
                gpsFixes = gpsFixes,
                gpsSignalLost = false,
                lastGpsAgeS = 0L
            )
        }
    }

    private fun updateAutoStop(tLoc: Long, speedMps: Double) {
        // Capture the leading samples (warm-up → pull-away) for the auto-record START trim. Held open for
        // START_TRACK_MAX_MS so the departure can be confirmed as sustained motion (a brief creep then
        // pause must not close it early); the sample cap is a defensive backstop.
        if (startTrackOpen) {
            if (startTrackFirstT == 0L) startTrackFirstT = tLoc
            startSpeedTrack.add(tLoc to speedMps)
            if (tLoc - startTrackFirstT >= START_TRACK_MAX_MS || startSpeedTrack.size >= START_TRACK_MAX_SAMPLES) {
                startTrackOpen = false
            }
        }
        // Keep a short rolling (time, speed) window so the stop can be placed at the real rest moment.
        recentSpeedTrack.addLast(tLoc to speedMps)
        val windowStart = tLoc - STOP_TRACK_WINDOW_MS
        while (recentSpeedTrack.isNotEmpty() && recentSpeedTrack.first().first < windowStart) {
            recentSpeedTrack.removeFirst()
        }

        if (speedMps >= AUTO_STOP_DRIVING_SPEED_MPS) {
            sawDriving = true
            lastDrivingLocT = tLoc
            return
        }
        if (!sawDriving || autoStopRequested || lastDrivingLocT <= 0L) return
        val tripStart = RecordingState.state.value.startTime
        if (tripStart > 0 && System.currentTimeMillis() - tripStart < MIN_AUTO_STOP_TRIP_MS) return
        if (speedMps <= AUTO_STOP_IDLE_SPEED_MPS &&
            tLoc - lastDrivingLocT >= AUTO_STOP_IDLE_MS
        ) {
            autoStopRequested = true
            // Retrospectively place the trip end at the moment the car came to rest (last sample
            // above 4 km/h, then the first stationary sample after it) rather than ~6 min later when
            // the idle timer fired. Location fixes (loc.elapsedRealtimeNanos) and motion samples
            // (SystemClock.elapsedRealtime) share the monotonic clock, so one cutoff trims both.
            val restT = AutoStop.retrospectiveEndTime(recentSpeedTrack.toList()) ?: lastDrivingLocT
            val trimT = restT + AUTO_STOP_END_GRACE_MS
            stopRecording(TrimCutoff(trimT, trimT))
        }
    }

    override fun onLocationChanged(loc: Location) = handleLocation(loc)

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ---- Sensor callbacks ----

    override fun onSensorChanged(event: SensorEvent) {
        if (!recording) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                ax = event.values[0].toDouble()
                ay = event.values[1].toDouble()
                az = event.values[2].toDouble()
                // Vibration signal for the GPS-free motion-confirm: the sample-to-sample delta cancels
                // gravity, so a smoothed magnitude separates a driving car (road buzz) from a parked one.
                if (haveJerkRef) {
                    val dx = ax - prevAx; val dy = ay - prevAy; val dz = az - prevAz
                    val jerk = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                    vibrationEma = 0.94 * vibrationEma + 0.06 * jerk
                    if (vibrationEma > maxVibrationEma) maxVibrationEma = vibrationEma
                }
                prevAx = ax; prevAy = ay; prevAz = az; haveJerkRef = true
                // Monotonic clock, matching the location time base so motion can be time-aligned
                // with GPS for fusion and event-location mapping.
                val now = SystemClock.elapsedRealtime()
                if (now - lastMotionWrite >= 20) { // ~50 Hz — verbose for sensor calibration
                    lastMotionWrite = now
                    val sample = MotionSample(
                        tripId = tripId, t = now,
                        ax = ax, ay = ay, az = az, gx = gx, gy = gy, gz = gz,
                        grx = grx, gry = gry, grz = grz
                    )
                    synchronized(bufferLock) { motionBuffer.add(sample) }
                    motionSampleCount++
                    lastMotionAtWall = System.currentTimeMillis()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gx = event.values[0].toDouble()
                gy = event.values[1].toDouble()
                gz = event.values[2].toDouble()
            }
            Sensor.TYPE_GRAVITY -> {
                grx = event.values[0].toDouble()
                gry = event.values[1].toDouble()
                grz = event.values[2].toDouble()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- Foreground / notification ----

    /** @return true if the location foreground service started; false if the platform blocked it. */
    private fun startInForeground(): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (e: Exception) {
            // Android 14: starting a `location`-typed FGS from the background requires the app to
            // currently have location access — i.e. ACCESS_BACKGROUND_LOCATION ("Allow all the time").
            // With only while-in-use this throws SecurityException. Catch it so a hands-free arm can't
            // crash (and crash-loop) the app.
            AutoRecordLog.add(this, "startForeground(location) FAILED: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * The location foreground service couldn't start (almost always a background auto-arm on Android 14
     * without "Allow all the time" location). No trip row exists yet, so there's nothing to clean up —
     * notify the user how to enable hands-free, then stop. Never crashes.
     */
    private fun onForegroundStartBlocked() {
        if (autoStarted) {
            AutoRecordLog.add(this, "auto-arm aborted: location FGS blocked (grant 'Allow all the time' for hands-free)")
            postHandsFreeBlockedNotice()
        }
        autoStarted = false
        runCatching { stopForegroundCompat() }
        stopSelf()
    }

    private fun postHandsFreeBlockedNotice() {
        val open = PendingIntent.getActivity(
            this, 2,
            (packageManager.getLaunchIntentForPackage(packageName) ?: Intent())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Couldn't auto-start your trip")
            .setContentText("Allow location \"All the time\" for hands-free recording. Tap to open.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Hands-free recording needs location set to \"Allow all the time\". Tap to open the app, " +
                    "go to Auto-record, and grant it. Recording from inside the app still works."
            ))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTICE_ID, n) }
    }

    /** Alert (works backgrounded too) that a just-stopped trip was too short to keep. */
    private fun postNotRecordedNotice() {
        val open = PendingIntent.getActivity(
            this, 3,
            (packageManager.getLaunchIntentForPackage(packageName) ?: Intent())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Trip not recorded")
            .setContentText("It was too short to save (under 5 m or 10 s).")
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOT_RECORDED_NOTICE_ID, n) }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            (packageManager.getLaunchIntentForPackage(packageName) ?: Intent())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording trip")
            .setContentText("Collecting GPS and motion data")
            .setSmallIcon(EngineR.drawable.engine_ic_stat_record)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Trip Recording", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        val interruptedTripId = if (recording && !stoppingNormally) tripId else 0L
        recording = false
        tickerJob?.cancel()
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }
        try {
            if (usingFused) locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        } catch (_: Exception) {
        }
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
        unregisterGnss()
        if (interruptedTripId > 0L) {
            runBlocking(Dispatchers.IO) {
                flushBuffers(writeCheckpoint = false)
                writeCheckpoint()
                TripFinalizer.finalizeTrip(
                    dao = db.tripDao(),
                    tripId = interruptedTripId,
                    endWall = System.currentTimeMillis(),
                    requestedStatus = TripStatus.PARTIAL,
                    requestedReason = TripEndReason.SERVICE_DESTROYED
                )
            }
        }
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.cartrip.analyzer.START"
        const val ACTION_STOP = "com.cartrip.analyzer.STOP"
        // Auto-recording (Rev U): arm a provisional trip on an in-car trigger, confirm motion, and
        // stop on a grace timer when the trigger drops (cancelled by CHARGING_RESUMED).
        const val ACTION_AUTO_ARM = "com.cartrip.analyzer.AUTO_ARM"
        const val ACTION_AUTO_STOP_GRACE = "com.cartrip.analyzer.AUTO_STOP_GRACE"
        const val ACTION_CHARGING_RESUMED = "com.cartrip.analyzer.CHARGING_RESUMED"
        const val CHANNEL_ID = "trip_recording"
        private const val NOTIF_ID = 42
        private const val NOTICE_ID = 44 // hands-free-blocked notice (distinct from the recording notif)
        private const val NOT_RECORDED_NOTICE_ID = 45 // "trip too short, not recorded" alert
        // A trip this short is an accidental/empty recording, not a drive — discard it and tell the user.
        private const val MIN_TRIP_DISTANCE_M = 5.0
        private const val MIN_TRIP_DURATION_S = 10.0
        private const val MAX_SPEED = 75.0 // m/s (~270 km/h) plausibility cap
        private const val LOCATION_INTERVAL_MS = 500L
        private const val LOCATION_FASTEST_INTERVAL_MS = 250L
        private const val RAW_SENSOR_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private const val GPS_SIGNAL_LOST_MS = 2L * 60L * 1000L
        private const val MOTION_STALL_RESTART_MS = 15L * 1000L
        private const val SENSOR_RESTART_COOLDOWN_MS = 30L * 1000L
        private const val MAX_SENSOR_RESTARTS = 6
        private const val AUTO_STOP_DRIVING_SPEED_MPS = 3.0 // ~11 km/h
        private const val AUTO_STOP_IDLE_SPEED_MPS = 1.0 // ~4 km/h
        private const val MIN_AUTO_STOP_TRIP_MS = 3L * 60L * 1000L
        private const val AUTO_STOP_IDLE_MS = 6L * 60L * 1000L
        // Cover the full idle window (6 min) plus margin so the last moving sample is still buffered
        // when the idle timer fires and we look back for the rest moment.
        private const val STOP_TRACK_WINDOW_MS = 8L * 60L * 1000L
        private const val AUTO_STOP_END_GRACE_MS = 3L * 1000L
        // The start-trim window: capture the first few minutes so the pull-away can be confirmed as
        // sustained motion. 5 min covers any realistic warm-up; a longer idle just isn't trimmed (safe).
        // The sample cap is a defensive backstop (~33 min at 1 Hz).
        private const val START_TRACK_MAX_MS = 5L * 60L * 1000L
        private const val START_TRACK_MAX_SAMPLES = 2000
        // Smoothed sample-to-sample accel delta (m/s^2) above this during the confirm window means the
        // car is driving even without GPS. Conservative start; tune from the logged "vib" values.
        private const val SENSOR_MOTION_VIB = 0.30
    }
}
