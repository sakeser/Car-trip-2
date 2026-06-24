# Car Trip Analyzer — Comprehensive Handoff

_Last updated: 2026-06-24 · App version **2.55 (build 66)** · Branch `main` (Rev R)_

This is the **authoritative** continuation brief. It supersedes `CLAUDE_CODE_HANDOFF.md`
(June 23, pre-Rev-G — now historical). `REV_HISTORY.md` has the per-revision changelog;
`FIELD_TEST_PLAN.md` has the on-road test scenarios.

---

## 1. TL;DR — current state

- Android app (Kotlin, Jetpack Compose, Room, Google Maps) that records trips from phone GPS +
  motion sensors, analyzes them offline, and enriches with Google traffic ETAs, OSM speed limits,
  and Google Sheets sync. Package `com.cartrip.analyzer`.
- **Branch `main` = `rev-g-functional` = `origin/main`**, all at `16d6c64` (fully pushed/aligned).
  `rev-g-functional` is a vestigial ref kept fast-forwarded to `main`; work happens on `main`.
- Installed on the Samsung **S25 (SM_S931W)** as **2.47/58**. Device reachable via adb.
- **46 unit tests**, all green. Room schema **v17** (migrations 1→17 all verified on the device DB).
- The separate UX-redesign worktree (`C:\Users\sinan\OneDrive\Desktop\cartrip`, branch
  `ux-redesign-v1`) is **untouched and unrelated** — do not merge it in.
- A field test (narrated drives) was planned; analyzing that data is the top open task (§9).

---

## 2. Build, install & device workflow ⚠️ READ FIRST

### 2.1 The OneDrive build-lock workaround (required)

The project lives under OneDrive, which holds file handles on `app/build` (`kotlin-classes`,
`snapshot`) and breaks incremental Kotlin compile with `Unable to delete directory`. **All builds
must relocate the build output out of OneDrive** via an init script:

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

The init script sets `layout.buildDirectory` to `C:/Users/sinan/cartrip-build-out/<project.name>`.
**APK output:** `C:\Users\sinan\cartrip-build-out\app\outputs\apk\debug\app-debug.apk`.

### 2.2 Maps API key

`local.properties` exists in this worktree (gitignored) with `MAPS_API_KEY`, `GOOGLE_MAP_ID`,
`sdk.dir`. **Do not commit or print the key.** Without it the app builds but maps are blank — never
ship a keyless APK.

### 2.3 Install + verify

```bash
ADB='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk\platform-tools\adb.exe'
"$ADB" install -r '/c/Users/sinan/cartrip-build-out/app/outputs/apk/debug/app-debug.apk'
"$ADB" shell dumpsys package com.cartrip.analyzer | grep versionName
```

Bump `versionCode`/`versionName` in `app/build.gradle.kts` for every installed revision.

### 2.4 Pulling the on-device DB (for analysis) — WAL caveat

```bash
# Copy ALL THREE files — cartrip.db alone is WAL-stale and gives wrong counts/values.
for f in cartrip.db cartrip.db-wal cartrip.db-shm; do
  "$ADB" exec-out run-as com.cartrip.analyzer sh -c "cat databases/$f" > "/tmp/$f"
done
```

Analyze with Python's `sqlite3` (no `sqlite3` CLI on Windows or the device). **Always delete the
pulled DB after** — it's personal location history.

### 2.5 On-device UI testing (screenshots)

- `screencap` to a **non-OneDrive** path: `"$ADB" exec-out screencap -p > /c/Users/sinan/cartrip-build-out/_s/x.png`
  (pulling into the OneDrive folder is flaky).
- Compose has sparse `uiautomator` output, so locate buttons by analyzing the PNG with Pillow
  (`python -m pip install Pillow`) — detect colored regions/score digits to get tap coordinates.
- The phone **auto-locks** (secure lock); if locked, UI taps no-op and you can't unlock it. Ask the
  user to unlock before a UI-verification pass.
- Forcing landscape for testing: `settings put system accelerometer_rotation 0; settings put system
  user_rotation 1` (restore with `user_rotation 0; accelerometer_rotation 1`).
- The `RecordingService` is `exported=false` — you **cannot** start a trip via `am`; tap the UI
  "Start trip" button. Delete any synthetic test trips afterward (Trip detail → ⋮ → Delete).

---

## 3. Architecture map

Pipeline: **record → analyze (offline) → persist → enrich → present.**

| Area | Key files |
|---|---|
| Recording | `record/RecordingService.kt` (FG service, GPS+sensors+GNSS), `record/RecordingState.kt` (live state), `record/AutoStop.kt` (retrospective end-time, pure) |
| Analysis | `analysis/TripAnalyzer.kt` (Kalman/RTS speed+accel, events, metrics), `analysis/MotionFusion.kt` (potholes/rough-road/harsh-stops), `analysis/FusedEventDetector.kt` (magnitude-first sensor detector), `analysis/GnssQuality.kt`, `analysis/SpeedTier.kt` |
| Data | `data/Entities.kt`, `data/AppDatabase.kt` (Room, schema v17 + migrations), `data/TripDao.kt`, `data/TripFinalizer.kt`, `data/TripStatus.kt` |
| Cloud | `cloud/SpeedLimits.kt` (OSM/Overpass + tile cache), `cloud/Tiles.kt`, `cloud/RoutesClient.kt` (Google Routes ETA), `cloud/TripSync.kt`/`SheetsClient.kt`/`GoogleAuth.kt` (Sheets) |
| UI | `MainActivity.kt` (nav), `ui/HomeScreen.kt` (record + landscape big button), `ui/TripDetailScreen.kt` (hero, You-vs-Traffic, replay, Driving, map), `ui/TripListScreen.kt` (frozen map + buckets), `ui/TripMap.kt`, `ui/DisplayEvents.kt` (event cleanup/clustering), `ui/TripScores.kt`, `ui/TripDataQuality.kt`, `ui/DebugScreen.kt`, `ui/TripBuckets.kt`, `ui/Format.kt`, `ui/TripLabeler.kt` |

Speed/accel are estimated with a **constant-acceleration Kalman filter + RTS smoother** (offline,
zero-lag) with ZUPT at stops. The GPS detector drives scoring; the sensor-fused detector is
"review-grade" (counts compared, peak-G used when motion rate is high enough).

---

## 4. What's been done (Rev G → J)

Full detail in `REV_HISTORY.md`. Condensed:

- **Rev G (2.35–2.36):** first unit-test suite; auto-stop retrospective end time; robust peak-G
  (p99.5 not raw max); mid-trip sensor-stall recovery; swerve speed-gating + vertical-bump veto;
  Routes ETA retry/backoff/diagnostics; trip-name radius guard.
- **Rev H (2.37–2.39):** AM/PM formatting; Past Trips recency buckets; **OSM speed-limit cache**
  (`cached_ways` by OSM way id + `cached_tiles`, cache-first, 30-day TTL); **GNSS quality layer**
  (`GnssStatus` per-trip summary → data-quality badge).
- **Rev I (2.40–2.41):** **debug screen** (live capture health); **per-window GNSS** (`gnss_samples`);
  speeding color tiers; bumps off by default; replay autoplay; **Past Trips frozen map + tap-to-preview**.
- **Rev J (2.42–2.47):** event-clustering bug fix (time-only, 8 s span cap) + **speed-aware
  turn/swerve flagging**; corner threshold 0.27→0.32 g; variable autoplay; **landscape full-screen
  Start/Stop**; hero score rings (no overall ring); **You-vs-Traffic range gauge**; replay speed
  gauge; **rough road → discrete stretches** (`roughStretchCount`/`bumpyScore`); am/pm no-dots;
  speed-readout smoothing; **Driving + Events consolidated** into one card.
- **Rev K (2.48):** field-data calibration of `FusedEventDetector` from narrated trips 845/847 (O7).
  Fixed **corners/swerves logged as linear accel/brake**: the longitudinal turn-veto now uses a
  **windowed** centripetal max (`CORNER_VETO_MS=450`) instead of one sample (the gyro-yaw dip and the
  lateral-accel peak don't coincide — trip 847 logged a 0.47 g "ACCEL" mid-curve), and an
  ambiguous-slope spike during clear rotation (`AMB_TURN_YAW`/`AMB_TURN_LAT_G`) is treated as steering
  rather than guessed. Validated offline to drop exactly the 6 corner/swerve-contaminated longitudinals
  across both trips while keeping every genuine brake/accel; +2 regression tests (48 total).
- **Rev L (2.49):** **promoted the fused detector into the Safety score.** 1 Hz GPS exposure is ~0 even
  on hard drives (845/847 scored 0 hard brakes/corners), so on dense-motion trips the Safety
  hard-maneuver term is now driven by the corner-corrected sensor hard-event rate
  (`brake·7 + accel·3.5 + turn·1.2` per km, floor 2 km, capped 28), blended by the existing motion-Hz
  trust; old/low-rate trips still use GPS exposure. Field-calibrated so calm drives stay 97–98 while
  deliberately-hard ones drop to ~89–95. +3 `TripScoresTest` (51 total). Existing trips need
  **Re-analyze** to refresh counts/scores.
- **Rev M (2.50):** fused event **magnitude = maneuver peak**, not the first threshold-crossing sample
  (`PEAK_WINDOW_MS=1500`; longitudinal peak excludes bump/turn samples). A narrated 0.5 g brake was
  stored as 0.28 g; markers/export/severity now read true. Counts and `maxHorizGForce` unchanged.
  +1 regression test (52 total). Prerequisite for the severity-weighted Safety term (Rev N).

---

## 5. Data model & schema (Room v17)

Tables: `trips`, `locations`, `motions`, `analysis_points`, `drive_events`, `cached_ways`,
`cached_tiles`, `gnss_samples`. Migrations `1→17` are all present in `AppDatabase.kt` and
verified on-device.

- **Raw retention:** `locations`/`motions`/`gnss_samples` for completed trips are purged after
  **30 days** (`RAW_SENSOR_RETENTION_MS`). Re-analysis only works while raw data survives.
- **Speed-limit cache:** ways keyed by stable OSM way id; tiles (~2.2 km, `TILE_DEG=0.02`) mark
  fetched areas; 30-day TTL; OSM/ODbL permits caching with attribution (source+timestamp stored).
- **GNSS:** per-trip summary columns on `trips` (`gnssAvgSatsUsed`, `gnssAvgCn0`, `gnssTopCn0`,
  `gnssL5Seen`, `gnssSampleCount`) + per-window `gnss_samples` (~every 3 s).

⚠️ When adding a column: add to `Entities.kt`, bump `@Database(version=…)`, add a `MIGRATION_n_n+1`,
register it in `addMigrations(...)`, and thread through `TripFinalizer.finalizeTrip` (and the
`storedAnalysis`/`persistedAnalysis` metric reconstructions if it feeds the analysis object).

---

## 6. Detection & tuning reference

These are the knobs most likely to need tuning from field data. Values as of 2.47.

### GPS event detector — `TripAnalyzer.kt`
| Const | Value | Meaning |
|---|---|---|
| `HARD_ACCEL` | 2.5 m/s² (~0.25g) | hard acceleration |
| `HARD_BRAKE` | 3.0 m/s² (~0.30g) | hard braking |
| `HARD_CORNER` | 3.5 m/s² (~0.36g) | sharp turn (lateral) |

### Sensor-fused detector — `FusedEventDetector.kt`
| Const | Value | Meaning |
|---|---|---|
| `HARD_LONG_G` | 0.25 | horizontal-accel spike → brake/accel candidate |
| `HARD_CORNER_G` | 0.32 | sustained lateral-g → corner (raised from 0.27 in Rev J) |
| `SWERVE_YAW` | 0.45 rad/s | sharp yaw flick → swerve candidate |
| `SWERVE_MIN_SPEED_MPS` | 25 km/h | below this a yaw flick is a normal turn, not a swerve |
| `BUMP_VERT_DOMINANCE` | 1.0 | veto brake/accel if vertical g ≥ horizontal g (road bump) |
| `SLOPE_MIN` | 0.5 m/s² | GPS slope needed to call brake-vs-accel sign |
| `LONG_GAP_MS` / `TURN_GAP_MS` | 2500 / 3000 | event debounce |
| `CORNER_VETO_MS` | 450 | ± window for the turn-veto (Rev K — stops corners leaking as longitudinals) |
| `AMB_TURN_YAW` / `AMB_TURN_LAT_G` | 0.20 rad/s / 0.15 g | windowed rotation that makes an ambiguous-slope spike "steering, not brake/accel" (Rev K) |

### Display flagging / clustering — `ui/DisplayEvents.kt`
| Const | Value | Meaning |
|---|---|---|
| `CLUSTER_TIME_MS` | 4000 | events within this gap join one moment |
| `MAX_CLUSTER_SPAN_MS` | 8000 | hard cap on a cluster's total span (fixes the 31 s merge bug) |
| `TURN_MIN_SPEED_KMH` | 10 | turns/swerves below this are never flagged |
| `TURN_MIN_G_LOW` / `TURN_MIN_G_HIGH` | 0.40 / 0.30 | g needed for a turn at <30 / ≥30 km/h |

Brake/accel display threshold is g≥0.28 (intentionally left untuned per the owner).

### Speed limits / cache — `cloud/SpeedLimits.kt`, `cloud/Tiles.kt`
`MATCH_RADIUS_M=35`, `OVER_TOL_KMH=3` (speeding stat tolerance), `CACHE_TTL_MS=30d`, `TILE_DEG=0.02`.
Route coloring tiers (`SpeedTier.kt`): yellow 0–10 km/h over, red ≥10 (`RED_OVER_KMH=10`).
Speeding only counts in scoring when `limitCoverage ≥ 0.4`.

### Road & ride — `MotionFusion.kt`
`POTHOLE_VERT=3.2 m/s²`, `ROUGH_RMS=0.9` (vertical RMS), `STRETCH_WINDOW_MS=1000` (1 s rough-stretch
windows), `HARSH_STOP_JERK=3.0 m/s³`.

### Recording — `RecordingService.kt`, `AutoStop.kt`
`LOCATION_INTERVAL_MS=500` / `FASTEST=250` (**see Open Issue O1 — effectively 1 Hz**), motion ~50 Hz
(`SENSOR_DELAY_GAME`, written ≥20 ms apart), `MAX_SENSOR_RESTARTS=6`, `AUTO_STOP_IDLE_MS=6 min`.
Auto-stop end time: last sample >`MOVING_MPS` (4 km/h) → first sample ≤`STATIONARY_MPS` (0.7 m/s).

### Scoring — `ui/TripScores.kt`
Safety/Comfort/Pace heuristics. Fused-detector trust ramps on **motion sample rate** (6→20 Hz), NOT
`fusedConfidence`. Peak-G uses `maxHorizGForce` (robust) when fused ran, else p99 `peakGForce`.
**Safety hard-maneuver term (Rev L):** `gpsHardPenalty` (GPS exposure %, ×5/4.5/2) blended by
`fusedTrust` with `fusedHardPenalty = min(28, (motionBrake·7 + motionAccel·3.5 + motionTurn·1.2)/max(2,km))`.
Comfort already blends GPS+fused event rate. Weights field-calibrated on trips 845/847 — re-tune here.

---

## 7. Test suite (46 tests)

Run: `…\gradlew.bat --init-script '…\relocate-build.gradle' :app:testDebugUnitTest --no-daemon`.
Results: `C:\Users\sinan\cartrip-build-out\app\test-results\testDebugUnitTest\*.xml`.

Files: `AutoStopTest`, `FusedEventDetectorTest`, `DisplayEventsTest`, `TripAnalyzerTest`,
`MotionFusionTest`, `GnssQualityTest`, `SpeedTierTest`, `TilesTest`, `TripBucketsTest`,
`TripLabelerTest`, `GeoAndPolylineTest`. All pure-JVM (no Robolectric/instrumented).

Gaps: no instrumented/Room/Compose tests; network paths (Overpass, Routes, Sheets) and the
GnssStatus reading are not unit-tested (verified manually/on-device).

---

## 8. Open issues & known limitations

- **O1 — GPS is effectively 1 Hz.** The fused provider caps delivery at ~1 Hz on the S25 regardless
  of the 500/250 ms request (verified: trip 790, 2002 fixes, median 1000 ms, ~0 variance). All
  analysis runs on 1 Hz GPS. The faster-GPS request adds battery cost for no gain. To truly exceed
  1 Hz would require raw `GnssMeasurements`/`GPS_PROVIDER` work, or accept the cap and revert the
  request interval. Decision pending.
- **O2 — `fusedConfidence` is noise. [DONE, Rev O]** Empirically ~0.06 even on the best trip (790,
  45 Hz); never gated trust (scoring uses motion-Hz). The "forward-axis confidence" line was removed
  from the trip-detail data-quality UI in Rev O. The field still exists in the DB/metrics, just unsurfaced.
- **O3 — Privacy: `allowBackup=true`** backs up the unencrypted location-history DB to Google. Consider
  `allowBackup=false` or backup rules excluding `cartrip.db`; DB-at-rest encryption (SQLCipher) is a
  larger option.
- **O4 — Overpass dependency.** Free public endpoints; the cache greatly reduces calls, but heavy
  first-time use of a new area still hits them. No per-request rate limiting beyond endpoint failover.
- **O5 — Dead code.** `TripDetailScreen.kt` still has unused `FactorBar`, `FactorCell`,
  `TripLinksCard`, `ActionButton`, `ScorePanel`, `ScoreMeter`, `ReplayControls`; `TripActions` has an
  unused `label` param. Harmless (warnings); clean up opportunistically.
- **O6 — `TripLabeler` is GTA-hardcoded. [ADDRESSED, Rev O]** `GeoNamer` now reverse-geocodes
  unnamed trips' start/end (fail-soft, SharedPreferences-cached by quantized coords) into
  `North York -> Scarborough` labels, with the GTA `TripLabeler` as the fallback. Still open: learn
  home/work from frequency rather than hardcoding, and the labeler remains the (GTA-biased) fallback.
- **O7 — Field data analyzed (Rev K–L).** Narrated trips 845/847 analyzed vs transcripts: capture
  excellent (~46 Hz motion, clean 1 Hz GPS, GNSS now logged). Fixed the corner→longitudinal
  contamination (Rev K) and the GPS-detector score-blindness by promoting the fused detector into
  Safety (Rev L; the GPS detector reports `hardBrakeCount=0`/`hardCornerCount=0` at 1 Hz —
  `maxBrake` 2.56–2.69 < 3.0, `maxLat` 3.14–3.42 < 3.5). **Refinements still open:** (a) fused event
  **magnitudes under-report** (stored at first threshold-crossing sample, not the maneuver peak) — a
  severity-weighted Safety term (vs the current count rate) is the natural next step; (b) a dedicated
  **severe-corner** count (≥~0.45 g) would let hard cornering hit Safety distinctly without penalizing
  normal city turns (needs a stored field). See memory `field-test-2026-06-24-trips-845-847`.
- **O8 — Peak-over speeding** has no minimum-run-length requirement (a brief snap can set the peak).
  Smoothing of isolated limit mismatches exists; a run-length guard would harden it further.
- **O9 — Rough stretches aren't mappable.** `MotionFusion` stores `roughStretchCount`/`bumpyScore`/
  `roughRoadPct` as aggregates only (no per-stretch geometry), unlike potholes (timestamped `POTHOLE`
  events). To label rough stretches on the map like potholes, the analyzer would need to emit stretch
  segments (time ranges / a per-point rough flag); only newly re-analyzed trips with raw motion still
  present would populate. Deferred from the Rev P–R UI overhaul by request — revisit with new field data.

---

## 9. Roadmap / next steps (prioritized)

1. **Analyze field-test data (O7).** Pull the DB (§2.4), align events against the narration time
   anchors, and check: did speed-aware gating behave (5 vs 4 in the plan)? bumps not flagged as
   brakes? auto-stop end time correct? speed-limit cache hit on repeat roads (`lastCacheStat`)? GNSS
   quality tracking urban-canyon stretches? Tune the §6 constants from findings.
2. **Fuel / trip-cost feature.** Vehicle profile (city/hwy L/100km) + gas price + distance/speed →
   estimated fuel + $ per trip; monthly/commute cost trends. New entity/prefs; surface on trip
   detail + Insights. (From the owner's earlier backlog.)
3. **Reverse-geocoded trip naming (O6). [DONE, Rev O]** `GeoNamer` uses Android `Geocoder`
   (fail-soft, SharedPreferences-cached by ~110 m cells, per-refresh budget) for real
   origin/destination names, with `TripLabeler` as fallback. Still to do: learn home/work from
   frequency instead of hardcoding the fallback.
4. **GNSS phase 2 (optional/research).** Use `gnss_samples` to drive route/event confidence
   downweighting in urban canyons; optional raw-GNSS export behind debug. Not positioning.
5. **Validated hybrid detector.** Promote the fused detector from "review-grade" to scored: label
   each event GPS-confirmed / sensor-only / bump-echo / ambiguous; score only reviewed events.
6. **Insights depth.** Repeated-commute comparison, Last-X-km/days/trips filters, speeding/pothole
   heatmaps, per-km metrics. (`InsightsScreen.kt` exists as a base.)
7. **Cleanup (O5)** + consider `allowBackup` privacy (O3) + revisit O1/O2.

---

## 10. Operational gotchas

- **Commit/push only when asked.** Pushing to the default branch (`main`) is blocked by the
  auto-classifier unless the user explicitly authorizes it that turn. Keep `rev-g-functional`
  fast-forwarded to `main` after pushing.
- **Every code revision:** build + run unit tests + bump version + install + (if possible) screenshot-
  verify on device, then commit. Migrations: verify on-device by launching (which opens/migrates the
  DB) and checking `PRAGMA user_version` + new columns via a WAL-inclusive pull.
- **Don't fabricate test data** in the user's DB without cleanup (no on-device `sqlite3`; delete via
  the app UI). Don't commit/print the Maps key or the pulled DB.
- **Memory:** durable project state lives in the agent memory file `rev-g-build-and-status.md`
  (build workaround + branch/version status) and `review-2026-06-23-findings.md` (verified findings).

---

_Questions a fresh agent should be able to answer from this doc: how to build (§2.1), where the APK
lands (§2.1), how to read the real DB (§2.4), what every detector threshold means (§6), what's broken
or capped (§8), and what to do next (§9)._
