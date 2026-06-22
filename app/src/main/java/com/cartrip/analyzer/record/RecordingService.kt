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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.cartrip.analyzer.MainActivity
import com.cartrip.analyzer.R
import com.cartrip.analyzer.analysis.GeoUtils
import com.cartrip.analyzer.analysis.DriveEvent
import com.cartrip.analyzer.analysis.TrackPoint
import com.cartrip.analyzer.analysis.TripAnalyzer
import com.cartrip.analyzer.cloud.CloudPrefs
import com.cartrip.analyzer.cloud.CloudState
import com.cartrip.analyzer.cloud.CloudSync
import com.cartrip.analyzer.cloud.GoogleAuth
import com.cartrip.analyzer.cloud.RoutesClient
import com.cartrip.analyzer.cloud.RoutesConfig
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.AppDatabase
import com.cartrip.analyzer.data.DriveEventEntity
import com.cartrip.analyzer.data.LocationSample
import com.cartrip.analyzer.data.MotionSample
import com.cartrip.analyzer.data.TripEntity
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
import kotlinx.coroutines.withContext
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
    @Volatile private var usingFused = false

    @Volatile private var recording = false
    @Volatile private var tripId: Long = 0
    private var tickerJob: Job? = null

    private val bufferLock = Any()
    private val locBuffer = ArrayList<LocationSample>()
    private val motionBuffer = ArrayList<MotionSample>()

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
    private var lastMotionWrite = 0L
    private var sawDriving = false
    private var lastDrivingLocT = 0L
    private var autoStopRequested = false

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
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recording) return
        resetSessionState()
        startInForeground()
        scope.launch {
            val startWall = System.currentTimeMillis()
            val id = db.tripDao().insertTrip(TripEntity(startTime = startWall, endTime = 0))
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
    }

    private fun stopRecording(trimCutoff: TrimCutoff? = null) {
        if (!recording) {
            stopForegroundCompat()
            stopSelf()
            return
        }
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
        val finishedId = tripId
        scope.launch {
            flushBuffers()
            val dao = db.tripDao()
            if (trimCutoff != null) {
                dao.deleteLocationsAfter(finishedId, trimCutoff.locationT)
                dao.deleteMotionsAfter(finishedId, trimCutoff.motionT)
            }
            val locs = dao.getLocations(finishedId)
            val motions = dao.getMotions(finishedId)
            val analysis = TripAnalyzer.analyze(locs, motions)
            val trip = dao.getTrip(finishedId)
            if (trip != null) {
                val m = analysis.metrics
                val eta = fetchLiveEta(analysis)
                dao.updateTrip(
                    trip.copy(
                        endTime = System.currentTimeMillis(),
                        distanceM = m.distanceM,
                        durationS = m.durationS,
                        movingS = m.movingS,
                        idleS = m.idleS,
                        maxSpeedMps = m.maxSpeedMps,
                        avgMovingSpeedMps = m.avgMovingSpeedMps,
                        maxAccelMps2 = m.maxAccelMps2,
                        maxBrakeMps2 = m.maxBrakeMps2,
                        maxLateralMps2 = m.maxLateralMps2,
                        peakGForce = m.peakGForce,
                        hardAccelCount = m.hardAccelCount,
                        hardBrakeCount = m.hardBrakeCount,
                        hardCornerCount = m.hardCornerCount,
                        smoothness = m.smoothness,
                        analyzed = true,
                        googleEtaTrafficS = eta?.trafficS ?: trip.googleEtaTrafficS,
                        googleEtaFreeFlowS = eta?.freeFlowS ?: trip.googleEtaFreeFlowS,
                        etaSource = if (eta != null) "live" else trip.etaSource,
                        etaFetchedAt = if (eta != null) System.currentTimeMillis() else trip.etaFetchedAt
                    )
                )
                persistAnalysis(finishedId, analysis.points, analysis.events)
                purgeExpiredRawSamples()

                // Local Excel export + optional Google Sheets append.
                val updated = dao.getTrip(finishedId)
                if (updated != null) {
                    val status = mutableListOf<String>()
                    runCatching {
                        TripExcel.write(applicationContext, updated, analysis)
                    }.onSuccess { file ->
                        status += "Excel saved: ${file.name}"
                    }.onFailure { e ->
                        status += "Excel export failed: ${e.userMessage()}"
                    }

                    if (CloudPrefs.autoSync(applicationContext) &&
                        GoogleAuth.lastAccount(applicationContext) != null
                    ) {
                        CloudState.set {
                            it.copy(
                                syncing = true,
                                lastMessage = (status + "Syncing Google Sheets...").joinToString(" ")
                            )
                        }
                        val sync = CloudSync.syncTrip(applicationContext, updated, analysis)
                        sync.onSuccess {
                            status += "Google Sheets sync complete."
                        }.onFailure { e ->
                            status += "Google Sheets sync failed: ${e.userMessage()}"
                        }
                        CloudState.set {
                            it.copy(syncing = false, lastMessage = status.joinToString(" "))
                        }
                    } else {
                        CloudState.set { it.copy(lastMessage = status.joinToString(" ")) }
                    }
                }
            }
            RecordingState.completeTrip(finishedId)
            withContext(Dispatchers.Main) {
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
        autoStopRequested = false
        lastMotionWrite = 0L
    }

    private suspend fun persistAnalysis(
        finishedId: Long,
        points: List<TrackPoint>,
        events: List<DriveEvent>
    ) {
        val dao = db.tripDao()
        val sampledPoints = samplePoints(points)
        dao.deleteAnalysisPoints(finishedId)
        dao.deleteDriveEvents(finishedId)
        if (sampledPoints.isNotEmpty()) {
            dao.insertAnalysisPoints(sampledPoints.map { it.toEntity(finishedId) })
        }
        if (events.isNotEmpty()) {
            dao.insertDriveEvents(events.map { it.toEntity(finishedId) })
        }
    }

    /**
     * Snapshot Google's live traffic estimate for this trip's start->end the moment it ends,
     * so it can later be compared against the actual time. Returns null (and is skipped) when
     * there's no clear start/end, no key, or the call fails — the trip still saves fine.
     */
    private fun fetchLiveEta(analysis: TripAnalysis): RoutesClient.RouteResult? {
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
    }

    private fun samplePoints(points: List<TrackPoint>, minGapMs: Long = 1000L): List<TrackPoint> {
        if (points.size <= 2) return points
        val out = ArrayList<TrackPoint>()
        var lastT = Long.MIN_VALUE
        points.forEach { point ->
            if (out.isEmpty() || point.tMs - lastT >= minGapMs) {
                out += point
                lastT = point.tMs
            }
        }
        if (out.lastOrNull()?.tMs != points.last().tMs) out += points.last()
        return out
    }

    private fun TrackPoint.toEntity(tripId: Long): AnalysisPointEntity =
        AnalysisPointEntity(
            tripId = tripId,
            t = tMs,
            lat = lat,
            lon = lon,
            speedKmh = speedKmh,
            longAccel = longAccel,
            latAccel = latAccel
        )

    private fun DriveEvent.toEntity(tripId: Long): DriveEventEntity =
        DriveEventEntity(
            tripId = tripId,
            t = tMs,
            type = type.name,
            magnitude = magnitude
        )

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private fun registerSensors() {
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (linear != null) {
            sensorManager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME)
        }
        if (gyro != null) {
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun requestLocation() {
        val playOk = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        if (playOk) startFused() else startGps()
    }

    private fun startFused() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        fusedClient = client
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handleLocation(it) }
            }
        }
        locationCallback = cb
        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            usingFused = true
        } catch (_: SecurityException) {
            startGps()
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
                val start = RecordingState.state.value.startTime
                val elapsed = if (start > 0) (System.currentTimeMillis() - start) / 1000 else 0
                RecordingState.update { it.copy(elapsedS = elapsed) }
                if (counter % 2 == 0) flushBuffers()
            }
        }
    }

    private suspend fun flushBuffers() {
        val locs: List<LocationSample>
        val motions: List<MotionSample>
        synchronized(bufferLock) {
            locs = ArrayList(locBuffer); locBuffer.clear()
            motions = ArrayList(motionBuffer); motionBuffer.clear()
        }
        if (locs.isNotEmpty()) db.tripDao().insertLocations(locs)
        if (motions.isNotEmpty()) db.tripDao().insertMotions(motions)
    }

    // ---- Location handling (shared by fused callback and GPS listener) ----

    private fun handleLocation(loc: Location) {
        if (!recording || tripId == 0L) return
        val tMono = if (loc.elapsedRealtimeNanos > 0) loc.elapsedRealtimeNanos / 1_000_000L
        else System.currentTimeMillis()
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
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0
        )
        synchronized(bufferLock) { locBuffer.add(sample) }
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
                gpsFixes = gpsFixes
            )
        }
    }

    private fun updateAutoStop(tLoc: Long, speedMps: Double) {
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
            val trimLocationT = lastDrivingLocT + AUTO_STOP_TRIM_GRACE_MS
            val trimMotionT = System.currentTimeMillis() - (tLoc - trimLocationT)
            stopRecording(TrimCutoff(trimLocationT, trimMotionT))
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
                // Monotonic clock, matching the location time base so motion can be time-aligned
                // with GPS for fusion and event-location mapping.
                val now = SystemClock.elapsedRealtime()
                if (now - lastMotionWrite >= 40) { // ~25 Hz
                    lastMotionWrite = now
                    val sample = MotionSample(
                        tripId = tripId, t = now,
                        ax = ax, ay = ay, az = az, gx = gx, gy = gy, gz = gz
                    )
                    synchronized(bufferLock) { motionBuffer.add(sample) }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gx = event.values[0].toDouble()
                gy = event.values[1].toDouble()
                gz = event.values[2].toDouble()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- Foreground / notification ----

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
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
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
            .setSmallIcon(R.drawable.ic_stat_record)
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
        recording = false
        tickerJob?.cancel()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.cartrip.analyzer.START"
        const val ACTION_STOP = "com.cartrip.analyzer.STOP"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIF_ID = 42
        private const val MAX_SPEED = 75.0 // m/s (~270 km/h) plausibility cap
        private const val RAW_SENSOR_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private const val AUTO_STOP_DRIVING_SPEED_MPS = 3.0 // ~11 km/h
        private const val AUTO_STOP_IDLE_SPEED_MPS = 1.0 // ~4 km/h
        private const val MIN_AUTO_STOP_TRIP_MS = 3L * 60L * 1000L
        private const val AUTO_STOP_IDLE_MS = 6L * 60L * 1000L
        private const val AUTO_STOP_TRIM_GRACE_MS = 60L * 1000L
    }
}
