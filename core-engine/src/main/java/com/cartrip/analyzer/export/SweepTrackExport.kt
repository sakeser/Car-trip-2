package com.cartrip.analyzer.export

import android.content.Context
import com.cartrip.analyzer.data.AnalysisPointEntity
import com.cartrip.analyzer.data.TripDao
import java.io.File
import java.util.Locale

/**
 * Exports per-trip 1 Hz speed tracks (`AnalysisPointEntity`) as CSV for the OFF-DEVICE
 * speed-interruption parameter sweep — see the test-source harness `analysis/SpeedInterruptionSweep.kt`
 * and `ADVISORY_ASSESSMENT.md` §7. The analysis-point track persists after the raw-sample retention
 * purge, so old field-test trips (e.g. 1187/1189/845/847) can still be exported and replayed.
 *
 * Debug-only (surfaced from the Diagnostics screen); it touches no production analysis path and only
 * reads. Output: one combined `all_tracks.csv` (header `tripId,tMs,speedKmh,speedLimitKmh`) under the
 * app's external files dir (`.../files/sweep/`). The harness's `loadRealTrips()` reads this file.
 */
object SweepTrackExport {

    data class Result(val file: File, val tripCount: Int, val pointCount: Int)

    /** Trips with fewer than this many analysis points are skipped (nothing to replay). */
    private const val MIN_POINTS = 5

    /** Pure: build the combined CSV from already-loaded, time-ordered tracks. Locale-safe (US '.'). */
    fun combinedCsv(tracks: List<Pair<Long, List<AnalysisPointEntity>>>): String {
        val sb = StringBuilder()
        sb.append("tripId,tMs,speedKmh,speedLimitKmh\n")
        for ((tripId, points) in tracks) {
            for (p in points) {
                sb.append(tripId).append(',')
                    .append(p.t).append(',')
                    .append(String.format(Locale.US, "%.3f", p.speedKmh)).append(',')
                    .append(String.format(Locale.US, "%.1f", p.speedLimitKmh)).append('\n')
            }
        }
        return sb.toString()
    }

    /**
     * Fetch every non-sample trip's analysis track (or only [tripIds] when given), write the combined
     * CSV, and return where it landed + how much was written. DB reads use Room's suspend DAO (which
     * dispatches its own IO); the file write should be called off the main thread by the caller.
     */
    suspend fun exportCombined(context: Context, dao: TripDao, tripIds: List<Long>? = null): Result {
        val idFilter = tripIds?.toHashSet()
        val trips = dao.getAllTrips()
            .filter { !it.isSample && (idFilter == null || it.id in idFilter) }
            .sortedBy { it.id }
        val tracks = ArrayList<Pair<Long, List<AnalysisPointEntity>>>()
        var pointCount = 0
        for (trip in trips) {
            val pts = dao.getAnalysisPoints(trip.id)
            if (pts.size >= MIN_POINTS) {
                tracks += trip.id to pts
                pointCount += pts.size
            }
        }
        val dir = File(context.getExternalFilesDir(null), "sweep").apply { mkdirs() }
        val file = File(dir, "all_tracks.csv")
        file.writeText(combinedCsv(tracks))
        return Result(file, tracks.size, pointCount)
    }
}
