package com.cartrip.analyzer.analysis

import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * EVIDENCE TOOL — NOT PRODUCTION, NOT WIRED IN. (Advisory: forced-slowdown / speed-interruption sweep.)
 *
 * Compares the CURRENT production drawdown detector against several candidate "Speed Interruption"
 * configurations over a set of trips, and writes a markdown + CSV report. The goal is calibration
 * EVIDENCE before any threshold change — it does NOT modify `Drawdowns`, `StopAndGo`, `StressScore`,
 * or `DriverLoad`, and nothing in the app calls this.
 *
 * Why it's here (test sources) and self-contained: it depends only on the Kotlin stdlib (its own
 * [SweepPoint] type, faithful local ports of the production detectors) so it can run off-device via
 * `kotlinc` without the Android toolchain, and can't ship or be wired into production by accident.
 * Productionizing later = move [CandidateDetector] to `main/` and adapt [SweepPoint] -> the engine's
 * `analysis.TrackPoint` (`tMs`, `speedKmh`, `speedLimitKmh`).
 *
 * Run standalone:
 *   kotlinc SpeedInterruptionSweep.kt -include-runtime -d sweep.jar
 *   java -jar sweep.jar <outDir> [realTripCsvDir]
 *
 * Real trips: drop CSVs named `trip_<id>.csv` (header `tMs,speedKmh,speedLimitKmh`; limit 0 = unknown)
 * into [realTripCsvDir]. Export them once from the on-device DB's `AnalysisPointEntity` (the persisted
 * 1 Hz track — survives the raw-sample retention purge). Until then the report runs on the synthetic
 * representative trips below, which mirror the advisory's example shapes (highway compression, moderate
 * waves, urban arterial, sustained crawl, speed-limit wave, stop-and-go). The known field-test trips
 * (1187 calm, 1189 crawl, 845/847) should be the first real CSVs added.
 */

/** Minimal point: the only fields the speed-interruption logic needs. Mirrors `TrackPoint`'s subset. */
data class SweepPoint(val tMs: Long, val speedKmh: Double, val speedLimitKmh: Double = 0.0)

enum class SpeedInterruptionType { MAJOR_FORCED_SLOWDOWN, TRAFFIC_WAVE, URBAN_INTERRUPTION }

/** One detected interruption. Base [severity] = (km/h lost)^2 (comparable to production drawdown
 *  severity); the extra factors are reported raw for inspection, NOT folded into severity pre-calibration. */
data class SpeedInterruption(
    val tCruiseEndMs: Long,
    val cruiseKmh: Double,
    val referenceKmh: Double,
    val troughKmh: Double,
    val recoveryKmh: Double,
    val lossKmh: Double,
    val lossFraction: Double,
    val timeToTroughS: Double,
    val interruptionDurationS: Double,
    val recoveredFraction: Double,
    val belowExpectedKmh: Double,
    val speedLimitKmh: Double,
    val type: SpeedInterruptionType,
    val severity: Double
)

/** A candidate parameter set. The 5 below are the advisory's suggested comparison grid. */
data class SweepConfig(
    val name: String,
    val cruiseMinKmh: Double,
    val minLossFraction: Double,   // plain-English "speed lost"; dropFloor = reference*(1-this)
    val recoveryFraction: Double,
    val recoveryWindowS: Double
)

val SWEEP_CONFIGS = listOf(
    SweepConfig("CurrentStrict", cruiseMinKmh = 60.0, minLossFraction = 0.50, recoveryFraction = 0.75, recoveryWindowS = 150.0),
    SweepConfig("Balanced",      cruiseMinKmh = 55.0, minLossFraction = 0.38, recoveryFraction = 0.72, recoveryWindowS = 180.0),
    SweepConfig("HighwayWave",   cruiseMinKmh = 70.0, minLossFraction = 0.30, recoveryFraction = 0.80, recoveryWindowS = 180.0),
    SweepConfig("UrbanWave",     cruiseMinKmh = 45.0, minLossFraction = 0.35, recoveryFraction = 0.70, recoveryWindowS = 180.0),
    SweepConfig("Sensitive",     cruiseMinKmh = 45.0, minLossFraction = 0.30, recoveryFraction = 0.70, recoveryWindowS = 240.0)
)

/**
 * The candidate detector: like `Drawdowns` but configurable, speed-limit-aware, typed, with a cooldown
 * so 1 Hz GPS oscillation isn't counted as multiple waves. Pure.
 */
object CandidateDetector {
    private const val MIN_CRUISE_S = 4.0
    private const val EDGE_TRIM_S = 5.0
    private const val CRUISE_HOLD_FRAC = 0.90     // cruise ends when speed dips >10% below its running peak
    private const val COOLDOWN_S = 8.0            // retrigger guard after a recovery

    fun detect(points: List<SweepPoint>, cfg: SweepConfig): List<SpeedInterruption> {
        val n = points.size
        if (n < 5) return emptyList()
        val t0 = points.first().tMs
        val tN = points.last().tMs
        fun inEdge(tMs: Long) = (tMs - t0) < EDGE_TRIM_S * 1000 || (tN - tMs) < EDGE_TRIM_S * 1000

        val out = ArrayList<SpeedInterruption>()
        var i = 0
        var cooldownUntil = Long.MIN_VALUE
        while (i < n) {
            val p = points[i]
            if (p.speedKmh < cfg.cruiseMinKmh || inEdge(p.tMs) || p.tMs < cooldownUntil) { i++; continue }
            // Establish the cruise: extend while speed holds near its running peak. Relative (not an
            // absolute floor) so a shallow dip from a fast cruise (110->70) still EXITS the cruise and
            // gets evaluated — prod Drawdowns' absolute floor (cruiseMin*0.85) absorbs such dips into
            // the cruise and never sees them (the key structural finding from the first sweep run).
            var j = i
            var cruiseSpeed = p.speedKmh
            var cruiseLimit = if (p.speedLimitKmh > 0.0) p.speedLimitKmh else 0.0
            val cruiseStart = p.tMs
            while (j < n && points[j].speedKmh >= cruiseSpeed * CRUISE_HOLD_FRAC) {
                cruiseSpeed = max(cruiseSpeed, points[j].speedKmh)
                if (points[j].speedLimitKmh > 0.0) cruiseLimit = max(cruiseLimit, points[j].speedLimitKmh)
                j++
            }
            val cruiseEnd = points[j - 1].tMs
            if (cruiseEnd - cruiseStart < MIN_CRUISE_S * 1000) { i = maxOf(j, i + 1); continue }

            // Limit-aware reference: never rewards speeding; falls back to observed cruise when unknown.
            val reference = if (cruiseLimit > 0.0) minOf(cruiseSpeed, cruiseLimit * 1.05) else cruiseSpeed
            val dropFloor = reference * (1.0 - cfg.minLossFraction)
            val recoverTo = reference * cfg.recoveryFraction
            val windowEnd = cruiseEnd + (cfg.recoveryWindowS * 1000).toLong()

            var k = j
            var trough = Double.MAX_VALUE
            var troughT = cruiseEnd
            var hitDrop = false
            var recoverIdx = -1
            while (k < n && points[k].tMs <= windowEnd) {
                val s = points[k].speedKmh
                if (s < trough) { trough = s; troughT = points[k].tMs }
                if (s <= dropFloor) hitDrop = true
                if (hitDrop && s >= recoverTo) { recoverIdx = k; break }
                k++
            }
            if (hitDrop && recoverIdx >= 0) {
                val recovery = points[recoverIdx].speedKmh
                val loss = (reference - trough).coerceAtLeast(0.0)
                val lossFraction = if (reference > 0) loss / reference else 0.0
                val type = classify(reference, lossFraction)
                out += SpeedInterruption(
                    tCruiseEndMs = cruiseEnd,
                    cruiseKmh = cruiseSpeed,
                    referenceKmh = reference,
                    troughKmh = trough,
                    recoveryKmh = recovery,
                    lossKmh = loss,
                    lossFraction = lossFraction,
                    timeToTroughS = (troughT - cruiseEnd) / 1000.0,
                    interruptionDurationS = (points[recoverIdx].tMs - cruiseEnd) / 1000.0,
                    recoveredFraction = if (reference > 0) recovery / reference else 0.0,
                    belowExpectedKmh = loss,
                    speedLimitKmh = cruiseLimit,
                    type = type,
                    severity = loss * loss
                )
                cooldownUntil = points[recoverIdx].tMs + (COOLDOWN_S * 1000).toLong()
                i = recoverIdx + 1
            } else {
                i = maxOf(j, i + 1)
            }
        }
        return out
    }

    /** Typing is independent of the config gate so counts-by-type are comparable across configs. */
    private fun classify(reference: Double, lossFraction: Double): SpeedInterruptionType = when {
        lossFraction >= 0.50 -> SpeedInterruptionType.MAJOR_FORCED_SLOWDOWN
        reference >= 60.0 -> SpeedInterruptionType.TRAFFIC_WAVE
        else -> SpeedInterruptionType.URBAN_INTERRUPTION
    }
}

/** Faithful port of the CURRENT production `Drawdowns.kt` (the baseline we're comparing against). */
object CurrentDrawdowns {
    private const val CRUISE_MIN_KMH = 60.0
    private const val DROP_FRACTION = 0.5
    private const val RECOVERY_FRACTION = 0.75
    private const val RECOVERY_WINDOW_S = 150.0
    private const val MIN_CRUISE_S = 4.0
    private const val EDGE_TRIM_S = 5.0

    data class Result(val count: Int, val severity: Double)

    fun detect(points: List<SweepPoint>): Result {
        val n = points.size
        if (n < 5) return Result(0, 0.0)
        val t0 = points.first().tMs
        val tN = points.last().tMs
        fun inEdge(tMs: Long) = (tMs - t0) < EDGE_TRIM_S * 1000 || (tN - tMs) < EDGE_TRIM_S * 1000
        var count = 0
        var severity = 0.0
        var i = 0
        while (i < n) {
            if (points[i].speedKmh < CRUISE_MIN_KMH || inEdge(points[i].tMs)) { i++; continue }
            var j = i
            var cruiseSpeed = 0.0
            val cruiseStart = points[i].tMs
            while (j < n && points[j].speedKmh >= CRUISE_MIN_KMH * 0.85) {
                cruiseSpeed = max(cruiseSpeed, points[j].speedKmh); j++
            }
            val cruiseEnd = points[j - 1].tMs
            if (cruiseEnd - cruiseStart < MIN_CRUISE_S * 1000) { i = maxOf(j, i + 1); continue }
            val dropFloor = cruiseSpeed * DROP_FRACTION
            val recoverTo = cruiseSpeed * RECOVERY_FRACTION
            val windowEnd = cruiseEnd + (RECOVERY_WINDOW_S * 1000).toLong()
            var k = j
            var trough = Double.MAX_VALUE
            var hitDrop = false
            var recoverIdx = -1
            while (k < n && points[k].tMs <= windowEnd) {
                val s = points[k].speedKmh
                if (s < trough) trough = s
                if (s <= dropFloor) hitDrop = true
                if (hitDrop && s >= recoverTo) { recoverIdx = k; break }
                k++
            }
            if (hitDrop && recoverIdx >= 0) {
                val drop = cruiseSpeed - trough
                count++; severity += drop * drop
                i = recoverIdx + 1
            } else i = maxOf(j, i + 1)
        }
        return Result(count, severity)
    }
}

/** Faithful port of the CURRENT production `StopAndGo.kt` crawl/no-break signals (unchanged baseline). */
object CrawlSignal {
    private const val MOVING_KMH = 3.0
    private const val CRAWL_KMH = 40.0
    private const val REST_KMH = 3.0
    private const val REST_MIN_S = 10.0
    private const val MAX_GAP_S = 30.0

    data class Result(val crawlSeconds: Double, val crawlFraction: Double, val longestNoBreakS: Double)

    fun analyze(points: List<SweepPoint>): Result {
        val n = points.size
        if (n < 2) return Result(0.0, 0.0, 0.0)
        var movingS = 0.0
        var crawlS = 0.0
        for (i in 0 until n - 1) {
            val p = points[i]
            val dt = (points[i + 1].tMs - p.tMs) / 1000.0
            if (dt <= 0.0 || dt > MAX_GAP_S) continue
            if (p.speedKmh >= MOVING_KMH) {
                movingS += dt
                if (p.speedKmh < CRAWL_KMH) crawlS += dt
            }
        }
        val restMinMs = (REST_MIN_S * 1000).toLong()
        var longest = 0.0
        var stretchStart = points.first().tMs
        var i = 0
        while (i < n) {
            if (points[i].speedKmh <= REST_KMH) {
                var j = i
                while (j < n && points[j].speedKmh <= REST_KMH) j++
                val restStart = points[i].tMs
                val restEnd = points[j - 1].tMs
                if (restEnd - restStart >= restMinMs) {
                    longest = max(longest, (restStart - stretchStart) / 1000.0)
                    stretchStart = restEnd
                }
                i = j
            } else i++
        }
        longest = max(longest, (points.last().tMs - stretchStart) / 1000.0)
        return Result(crawlS, if (movingS > 0) crawlS / movingS else 0.0, longest)
    }
}

// --- Trip model + builders -------------------------------------------------------------------------

class SweepTrip(val id: String, val note: String, val points: List<SweepPoint>) {
    val durationS: Double get() = if (points.size < 2) 0.0 else (points.last().tMs - points.first().tMs) / 1000.0
    val distanceKm: Double
        get() {
            var km = 0.0
            for (i in 0 until points.size - 1) {
                val dt = (points[i + 1].tMs - points[i].tMs) / 1000.0
                km += points[i].speedKmh / 3600.0 * dt
            }
            return km
        }
}

/** Build a 1 Hz track from (durationSeconds, speedKmh) segments at a constant [limit] (0 = unknown). */
private fun track(id: String, note: String, limit: Double, vararg segs: Pair<Int, Double>): SweepTrip {
    val pts = ArrayList<SweepPoint>()
    var t = 0L
    for ((dur, spd) in segs) {
        repeat(dur) { pts += SweepPoint(t, spd, limit); t += 1000 }
    }
    return SweepTrip(id, note, pts)
}

/** Representative synthetic trips mirroring the advisory's example shapes. */
fun syntheticTrips(): List<SweepTrip> = listOf(
    track("calm_highway", "Steady 110 in a 100 zone — should score ~0 everywhere.", 100.0,
        1200 to 110.0),
    track("highway_compression", "110 -> 70 -> 105 twice (advisory ex.1). Strict misses; HighwayWave catches.", 100.0,
        60 to 110.0, 20 to 70.0, 90 to 105.0, 20 to 70.0, 90 to 105.0),
    track("moderate_waves", "100/68/92/62/95 repeated waves (advisory ex.2), no posted limit.", 0.0,
        40 to 100.0, 15 to 68.0, 40 to 92.0, 15 to 62.0, 60 to 95.0),
    track("urban_arterial", "58 -> 30 -> 55 on a 50 zone (advisory ex.3). Strict's 60 cruise-min excludes it.", 50.0,
        30 to 58.0, 12 to 30.0, 40 to 55.0),
    track("sustained_crawl", "70 -> 25 for 180 s -> 55 (advisory ex.4). Recovery is past Strict's 150 s window.", 80.0,
        40 to 70.0, 180 to 25.0, 40 to 55.0),
    track("speedlimit_wave", "Limit 80, cruise 78, trough 48, recover 76 (advisory ex.5). 38% loss — Strict misses.", 80.0,
        40 to 78.0, 14 to 48.0, 40 to 76.0),
    track("stop_and_go_city", "Rush-hour arterial: 50/10 cycles in a 50 zone. Many urban events + high crawl.", 50.0,
        20 to 50.0, 15 to 10.0, 20 to 50.0, 15 to 10.0, 20 to 50.0, 15 to 10.0, 20 to 50.0, 15 to 10.0, 20 to 50.0),
    track("calm_city_45", "Steady 45 in a 50 zone — control: slow but uninterrupted, should score ~0.", 50.0,
        600 to 45.0)
)

/**
 * Load real trips exported from `AnalysisPointEntity`. Two accepted formats (both produced by the
 * Diagnostics "Export trip tracks" button -> `export/SweepTrackExport.kt`):
 *  - a combined `all_tracks.csv` (header `tripId,tMs,speedKmh,speedLimitKmh`), and/or
 *  - per-trip `trip_<id>.csv` (header `tMs,speedKmh,speedLimitKmh`).
 */
fun loadRealTrips(dir: File): List<SweepTrip> {
    if (!dir.isDirectory) return emptyList()
    val out = ArrayList<SweepTrip>()
    File(dir, "all_tracks.csv").takeIf { it.isFile }?.let { out += parseCombined(it) }
    dir.listFiles { f -> f.isFile && f.name.startsWith("trip_") && f.name.endsWith(".csv") }
        ?.sortedBy { it.name }
        ?.forEach { f ->
            val pts = parseTrack(f, idCol = false)
            if (pts.size >= 5) out += SweepTrip(f.nameWithoutExtension.removePrefix("trip_"), "real (exported)", pts)
        }
    return out
}

/** Combined file: group rows by the leading tripId column, preserving first-seen order. */
private fun parseCombined(f: File): List<SweepTrip> {
    val byTrip = LinkedHashMap<String, ArrayList<SweepPoint>>()
    f.readLines().drop(1).forEach { line ->
        val c = line.split(',')
        if (c.size >= 3) {
            val id = c[0].trim()
            val t = c[1].trim().toLongOrNull()
            val v = c[2].trim().toDoubleOrNull()
            val lim = c.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0
            if (id.isNotEmpty() && t != null && v != null) byTrip.getOrPut(id) { ArrayList() }.add(SweepPoint(t, v, lim))
        }
    }
    return byTrip.entries.filter { it.value.size >= 5 }.map { SweepTrip(it.key, "real (exported, combined)", it.value) }
}

/** Per-trip file: `tMs,speedKmh,speedLimitKmh` (idCol=false). */
private fun parseTrack(f: File, idCol: Boolean): List<SweepPoint> {
    val pts = ArrayList<SweepPoint>()
    val base = if (idCol) 1 else 0
    f.readLines().drop(1).forEach { line ->
        val c = line.split(',')
        if (c.size >= base + 2) {
            val t = c[base].trim().toLongOrNull()
            val v = c[base + 1].trim().toDoubleOrNull()
            val lim = c.getOrNull(base + 2)?.trim()?.toDoubleOrNull() ?: 0.0
            if (t != null && v != null) pts += SweepPoint(t, v, lim)
        }
    }
    return pts
}

// --- Report ----------------------------------------------------------------------------------------

private fun Double.r1() = (this * 10).roundToInt() / 10.0
private fun Double.r0() = this.roundToInt()

private class Row(
    val config: String, val major: Int, val wave: Int, val urban: Int, val total: Int,
    val crawlS: Double, val burden: Double, val burdenPerKm: Double, val largest: String
)

private fun rowsFor(trip: SweepTrip): List<Row> {
    val crawl = CrawlSignal.analyze(trip.points)
    val km = trip.distanceKm.coerceAtLeast(0.001)
    val rows = ArrayList<Row>()
    val cd = CurrentDrawdowns.detect(trip.points)
    rows += Row("CurrentDrawdowns(prod)", cd.count, 0, 0, cd.count, crawl.crawlSeconds, cd.severity, cd.severity / km, "—")
    for (cfg in SWEEP_CONFIGS) {
        val ev = CandidateDetector.detect(trip.points, cfg)
        val major = ev.count { it.type == SpeedInterruptionType.MAJOR_FORCED_SLOWDOWN }
        val wave = ev.count { it.type == SpeedInterruptionType.TRAFFIC_WAVE }
        val urban = ev.count { it.type == SpeedInterruptionType.URBAN_INTERRUPTION }
        val burden = ev.sumOf { it.severity }
        val biggest = ev.maxByOrNull { it.severity }
        val largest = if (biggest == null) "—" else
            "${biggest.cruiseKmh.r0()}->${biggest.troughKmh.r0()}->${biggest.recoveryKmh.r0()} km/h " +
                "(${(biggest.lossFraction * 100).r0()}% loss, ttTrough ${biggest.timeToTroughS.r0()}s, ${biggest.type})"
        rows += Row(cfg.name, major, wave, urban, ev.size, crawl.crawlSeconds, burden, burden / km, largest)
    }
    return rows
}

fun buildMarkdown(trips: List<SweepTrip>): String {
    val sb = StringBuilder()
    sb.appendLine("# Speed-Interruption Parameter Sweep — Evidence Report")
    sb.appendLine()
    sb.appendLine("> Generated by `core-engine/src/test/.../analysis/SpeedInterruptionSweep.kt`. **Evidence only** — ")
    sb.appendLine("> no production thresholds, `StressScore`, or `DriverLoad` were changed. The `CurrentDrawdowns(prod)` ")
    sb.appendLine("> row is a faithful port of the live `Drawdowns.kt`; the rest are candidate configs.")
    sb.appendLine()
    val real = trips.count { it.note.startsWith("real") }
    sb.appendLine("Trips analysed: **${trips.size}** (${real} real / ${trips.size - real} synthetic).")
    sb.appendLine()
    sb.appendLine("## Candidate configs")
    sb.appendLine()
    sb.appendLine("| Config | Cruise min | Min speed loss | Recovery | Window | Intent |")
    sb.appendLine("|---|--:|--:|--:|--:|---|")
    sb.appendLine("| CurrentDrawdowns(prod) | 60 | 50% | 75% | 150s | live production baseline |")
    val intents = mapOf(
        "CurrentStrict" to "strict thresholds on the improved cruise-exit detector",
        "Balanced" to "better real-world capture",
        "HighwayWave" to "highway traffic compression",
        "UrbanWave" to "arterial interruptions",
        "Sensitive" to "high recall; may overcount"
    )
    for (c in SWEEP_CONFIGS) sb.appendLine(
        "| ${c.name} | ${c.cruiseMinKmh.r0()} | ${(c.minLossFraction * 100).r0()}% | " +
            "${(c.recoveryFraction * 100).r0()}% | ${c.recoveryWindowS.r0()}s | ${intents[c.name] ?: ""} |"
    )
    sb.appendLine()
    sb.appendLine("## Per-trip results")
    for (trip in trips) {
        sb.appendLine()
        sb.appendLine("### ${trip.id}  —  ${trip.distanceKm.r1()} km, ${(trip.durationS / 60).r1()} min")
        sb.appendLine("_${trip.note}_")
        sb.appendLine()
        sb.appendLine("| Config | Major | Wave | Urban | Total | Crawl s | Burden Σloss² | /km | Largest event |")
        sb.appendLine("|---|--:|--:|--:|--:|--:|--:|--:|---|")
        for (row in rowsFor(trip)) sb.appendLine(
            "| ${row.config} | ${row.major} | ${row.wave} | ${row.urban} | **${row.total}** | " +
                "${row.crawlS.r0()} | ${row.burden.r0()} | ${row.burdenPerKm.r0()} | ${row.largest} |"
        )
    }
    sb.appendLine()
    sb.appendLine("## Total-events matrix (trip × config)")
    sb.appendLine()
    sb.append("| Trip | prod |"); SWEEP_CONFIGS.forEach { sb.append(" ${it.name} |") }; sb.appendLine()
    sb.append("|---|--:|"); SWEEP_CONFIGS.forEach { _ -> sb.append("--:|") }; sb.appendLine()
    for (trip in trips) {
        val cd = CurrentDrawdowns.detect(trip.points)
        sb.append("| ${trip.id} | ${cd.count} |")
        for (cfg in SWEEP_CONFIGS) sb.append(" ${CandidateDetector.detect(trip.points, cfg).size} |")
        sb.appendLine()
    }
    sb.appendLine()
    sb.appendLine("## How to read this")
    sb.appendLine()
    sb.appendLine("- **Major/Wave/Urban** are the candidate's typed events; **Crawl s** is the *unchanged* `StopAndGo`")
    sb.appendLine("  signal (same for every row of a trip — sustained crawl is already covered and is NOT an event).")
    sb.appendLine("- **Burden** = Σ(km/h lost)², directly comparable to production drawdown severity. Extra factors")
    sb.appendLine("  (time-to-trough, duration, below-expected) are shown in *Largest event* but are NOT folded into")
    sb.appendLine("  severity yet — that waits for calibration against real trips.")
    sb.appendLine("- Add real trips: on the device, Diagnostics -> **Export trip tracks (CSV)** writes")
    sb.appendLine("  `all_tracks.csv`; drop it in a dir and re-run with that dir as arg 2. (Per-trip")
    sb.appendLine("  `trip_<id>.csv` files also work.) Start with field-test trips 1187, 1189, 845, 847.")
    return sb.toString()
}

fun buildCsv(trips: List<SweepTrip>): String {
    val sb = StringBuilder()
    sb.appendLine("trip,distanceKm,durationMin,config,major,wave,urban,total,crawlSeconds,burdenSumLossSq,burdenPerKm,largestCruiseKmh,largestTroughKmh,largestRecoveryKmh,largestLossPct,largestTimeToTroughS,largestType")
    for (trip in trips) {
        val crawl = CrawlSignal.analyze(trip.points)
        val km = trip.distanceKm.coerceAtLeast(0.001)
        val cd = CurrentDrawdowns.detect(trip.points)
        sb.appendLine("${trip.id},${trip.distanceKm.r1()},${(trip.durationS / 60).r1()},CurrentDrawdowns_prod,${cd.count},0,0,${cd.count},${crawl.crawlSeconds.r0()},${cd.severity.r0()},${(cd.severity / km).r0()},,,,,,")
        for (cfg in SWEEP_CONFIGS) {
            val ev = CandidateDetector.detect(trip.points, cfg)
            val major = ev.count { it.type == SpeedInterruptionType.MAJOR_FORCED_SLOWDOWN }
            val wave = ev.count { it.type == SpeedInterruptionType.TRAFFIC_WAVE }
            val urban = ev.count { it.type == SpeedInterruptionType.URBAN_INTERRUPTION }
            val burden = ev.sumOf { it.severity }
            val b = ev.maxByOrNull { it.severity }
            val big = if (b == null) ",,,,," else
                "${b.cruiseKmh.r0()},${b.troughKmh.r0()},${b.recoveryKmh.r0()},${(b.lossFraction * 100).r0()},${b.timeToTroughS.r0()},${b.type}"
            sb.appendLine("${trip.id},${trip.distanceKm.r1()},${(trip.durationS / 60).r1()},${cfg.name},$major,$wave,$urban,${ev.size},${crawl.crawlSeconds.r0()},${burden.r0()},${(burden / km).r0()},$big")
        }
    }
    return sb.toString()
}

fun main(args: Array<String>) {
    val outDir = File(args.getOrNull(0) ?: "reports").apply { mkdirs() }
    val realDir = args.getOrNull(1)?.let { File(it) }
    val trips = syntheticTrips() + (realDir?.let { loadRealTrips(it) } ?: emptyList())
    File(outDir, "speed_interruption_sweep.md").writeText(buildMarkdown(trips))
    File(outDir, "speed_interruption_sweep.csv").writeText(buildCsv(trips))
    println("Wrote ${trips.size}-trip sweep to ${outDir.absolutePath} (md + csv).")
}
