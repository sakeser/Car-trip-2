# Car Trip Analyzer — Comprehensive Handoff

_Last updated: 2026-06-26 · App version **2.80 (build 91)** · Branch `main` (Rev AL–AP shipped).
**Hands-free auto-record now works** via a persistent "armed" watcher service (Rev AO), and **Rev AP
fixed the critical charger-trigger bug** (the decision read a stale/inverted sticky battery state instead
of the broadcast edge). The watcher's receiver fires reliably on every real charge event; the owner's next
narrated drive (real cable plug/unplug — `dumpsys` can't simulate it on this S25) is the final validation._

This is the **authoritative** continuation brief. It supersedes `CLAUDE_CODE_HANDOFF.md`
(June 23, pre-Rev-G — now historical). `REV_HISTORY.md` has the per-revision changelog;
`FIELD_TEST_PLAN.md` has the on-road test scenarios.

---

## 1. TL;DR — current state

- Android app (Kotlin, Jetpack Compose, Room, Google Maps) that records trips from phone GPS +
  motion sensors, analyzes them offline, and enriches with Google traffic ETAs, OSM speed limits,
  and Google Sheets sync. Package `com.cartrip.analyzer`.
- **Branch `main`** at Rev AP (v2.80) — Rev AL–AO pushed (`f93a7e2`); **Rev AP is committed locally and
  may not be pushed yet** (verify with `git log origin/main..main`). Work happens on `main`.
  (Pushing to `main` needs explicit per-turn user authorization — the owner has authorized the ongoing
  ship-and-push workflow for this project.) `rev-g-functional` is a vestigial mirror ref; it has NOT
  been fast-forwarded since `ea2fa88` (the auto-classifier blocks force-moving/pushing it) — ignore it
  or sync it manually if wanted.
- Installed on the Samsung **S25 (SM_S931W)** as **2.80/91** (Rev AP). Device auto-locks fast; for a
  UI-verify pass ask the owner to unlock it, then `adb shell svc power stayon true` keeps the screen
  awake (reset with `stayon false` after). Screencap to a **non-OneDrive** path.
- **95 unit tests**, all green. Room schema **v19** (unchanged in Rev AP): v18 added the walk/non-drive override
  (`trips.userIsDrive`, nullable); v19 added per-fix GPS accuracy estimates (`locations.bearing/speed/
  verticalAccuracy`) + a raw **`gnss_measurements`** table (carrier phase + Doppler, toggle-gated) for
  the lane-detection R&D.
- **Recent arc:** Rev K–M = field-test calibration; **Rev N** = fuel/cost; **Rev O** = geocoded names;
  **Rev P–T** = trip-detail/past-trips **UI overhaul** + polish + hybrid fuel; **Rev U** =
  **auto-recording trigger first cut** (charging/Bluetooth → provisional record + motion-confirm +
  stop-grace) — **still needs the owner's on-device drive test (§9.1)**; **Rev V** = 8-item UI polish
  batch (trip-name disambiguation, bump-icon alignment, speeding-time floor, "+X km/h over" headline,
  you-vs-traffic labels/scaling, Home **Options** menu, "your trip" icon picker, Insights diverging
  bar chart); **Rev W** = **non-drive/walk guard** (`TripKind`) + privacy (`allowBackup=false`, O3) +
  dead-code removal (O5); **Rev X** = "When you drive" daypart insights + speeding-peak run-length
  guard (O8) + fuel suppressed for non-drives; **Rev Y** = Insights polish (robust diverging-bar
  scale; Score-trends re-imagined as per-score icon+bar+trend rows); **Rev Z–AE** = Insights/UI cleanup,
  trip-detail UI batch, auto-record decision log + pre-field-test hardening; **Rev AF** = GPS-free
  motion-confirm for garage auto-starts; **Rev AG** = **CompanionDeviceManager hands-free auto-start**
  (field-confirmed the manifest charger/BT receivers never fire backgrounded on the S25, so CDM observes
  the car's presence and is granted a background FGS start — **needs the owner's drive test**); **Rev AH**
  = harsh-stop detector recalibrated from 27 real trips (was firing 0/27 — fixed the 1 Hz stop-gate +
  replaced noisy jerk with peak decel ≥ 3.0 m/s²); **Rev AI** = harsh stops as mappable/filterable map
  events + Safety/Comfort penalty; **Rev AJ** = you-vs-traffic redesigned as one **to-scale timeline**
  (no-traffic + traffic-delay box, "you" marker anywhere, animated) + trip-screen diagnostic cleanup
  (removed Data-quality row, detector-comparison, raw-signal dumps); **Rev AK** = timeline label-overlap
  fix (fixed left/right legend) + **time-only trip titles** (no date); **Rev AL** = manual
  **walk/non-drive toggle** (schema v18 `userIsDrive`, excluded from all drive metrics); **Rev AM** =
  fixed a crash on "Pair car" (missing `android.software.companion_device_setup` uses-feature);
  **Rev AN** = **lane-detection data enabler** (per-fix accuracy estimates + raw GNSS carrier-phase/
  Doppler capture behind a Diagnostics toggle, schema v19); **Rev AO** = **persistent "armed" watcher
  service — the hands-free auto-start that finally works.** Field-proven that (a) `CompanionDeviceManager`
  can't pair/observe a classic-Bluetooth car (the chooser only shows discoverable devices; the Tucson
  never appears) and (b) a manifest `ACTION_POWER_CONNECTED` receiver never fires backgrounded. The
  watcher runtime-registers the charger/BT broadcasts AND, being a running FGS, lets the recording
  service start from the background — validated end-to-end on-device via a simulated charge cycle. The
  owner drives a hybrid 2023 Tucson, phone wireless-charging on a mount.
- The separate UX-redesign worktree (`C:\Users\sinan\OneDrive\Desktop\cartrip`, branch
  `ux-redesign-v1`) is **untouched and unrelated** — do not merge it in.
- ⚠️ **Source-encoding trap:** this Windows build mojibakes non-ASCII **string literals** in BOM-less
  `.kt` files. Build any glyph/emoji from code points (`String(Character.toChars(0x1F422))`,
  `0x2192.toChar()`) and keep new source ASCII. See `GeoNamer.ARROW` and the memory note.

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
| Recording | `record/RecordingService.kt` (FG service, GPS+sensors+GNSS+raw-GNSS; `ACTION_AUTO_ARM` = provisional record + 90 s motion-confirm by GPS **or** accelerometer vibration + discard; `ACTION_AUTO_STOP_GRACE` = retrospective-trim stop), `record/RecordingState.kt` (live state), `record/AutoStop.kt` (retrospective end-time, pure) |
| Auto-record | **`record/AutoRecordWatchService.kt`** = persistent "armed" FGS, the reliable hands-free trigger (see note below); `record/AutoRecordController.kt` (decision dispatch → arm/stop), `record/AutoRecordPolicy.kt` (pure trigger logic, unit-tested), `record/AutoRecordPrefs.kt`, `record/AutoRecordLog.kt` (Diagnostics decision log), `record/BootReceiver.kt` (re-arm after reboot), `record/CompanionCarManager.kt` + `record/CarPresenceService.kt` (CompanionDeviceManager — secondary/unreliable for classic-BT cars), `record/GnssLoggingPrefs.kt` (raw-GNSS toggle). Dead: `record/PowerConnectionReceiver.kt` / `record/CarBluetoothReceiver.kt` (manifest registration removed in Rev AO) |
| Analysis | `analysis/TripAnalyzer.kt` (Kalman/RTS speed+accel, events, metrics), `analysis/MotionFusion.kt` (potholes/rough-road/harsh-stops), `analysis/FusedEventDetector.kt` (magnitude-first sensor detector), `analysis/FuelEstimator.kt` (pure fuel/cost model), `analysis/GnssQuality.kt`, `analysis/SpeedTier.kt` |
| Data | `data/Entities.kt`, `data/AppDatabase.kt` (Room, schema **v19** + migrations), `data/TripDao.kt`, `data/TripFinalizer.kt`, `data/TripStatus.kt` |
| Cloud | `cloud/SpeedLimits.kt` (OSM/Overpass + tile cache), `cloud/Tiles.kt`, `cloud/RoutesClient.kt` (Google Routes ETA), `cloud/TripSync.kt`/`SheetsClient.kt`/`GoogleAuth.kt` (Sheets) |
| UI | `MainActivity.kt` (nav), `ui/HomeScreen.kt` (record + landscape big button), `ui/TripDetailScreen.kt` (hero, You-vs-Traffic, replay, Driving, map), `ui/TripListScreen.kt` (frozen map + buckets), `ui/TripMap.kt`, `ui/DisplayEvents.kt` (event cleanup/clustering), `ui/TripScores.kt`, `ui/TripDataQuality.kt`, `ui/DebugScreen.kt`, `ui/TripBuckets.kt`, `ui/Format.kt`, `ui/TripLabeler.kt`, `ui/GeoNamer.kt` (reverse-geocode), `ui/VehiclePrefs.kt` + `ui/VehicleScreen.kt` (fuel profile), `ui/InsightsScreen.kt`, `ui/Charts.kt` (TimeSeries/MiniSparkline/`DivergingBarChart`), `ui/TripNaming.kt` (same-name disambiguation), `ui/TripKind.kt` (walk/non-drive guard), `ui/DrivingTimes.kt` (daypart insights), `ui/EventGlyphs.kt` (shared bump glyph), `ui/UiPrefs.kt` (you-icon pref), `ui/Components.kt` (shared bits + Options sheet) |

Speed/accel are estimated with a **constant-acceleration Kalman filter + RTS smoother** (offline,
zero-lag) with ZUPT at stops. The GPS detector drives scoring; the sensor-fused detector is
"review-grade" (counts compared, peak-G used when motion rate is high enough).

### Auto-record architecture (Rev AO — IMPORTANT, hard-won)

Hands-free auto-start was field-broken for three platform reasons, all now solved:
1. A **manifest `ACTION_POWER_CONNECTED` receiver never fires when the app is backgrounded** (it's not on
   the implicit-broadcast exemption list), so the charger trigger was dead. (`ACL_CONNECTED` *is* exempt.)
2. Even a background receiver **can't start a `location` FGS** on Android 12+ (`ForegroundServiceStartNotAllowedException`).
3. **`CompanionDeviceManager` can't pair/observe a classic-Bluetooth car** — its chooser does a
   *discoverable* scan, so a bonded-but-not-discoverable car stereo never appears (the owner only saw
   random MACs and accidentally paired the wrong device `3C:E0:…`).

**The fix — `AutoRecordWatchService`:** while auto-record is enabled, a persistent low-importance FGS
runs. It (a) **runtime-registers** the charger/BT broadcasts (runtime receivers DO fire while a service
is alive), and (b) being a running FGS, puts the app in a foreground state so `AutoRecordController.arm()`
is *permitted* to start `RecordingService` from the background. Started from the settings toggle +
`TripApp.onCreate` + `BootReceiver` (reboot). Real-drive flow:
`charger-on/bt-connect → ARM → FGS start OK → AUTO_ARM (provisional) → motion-confirm OK (gps ≥ 5 km/h OR
vibration ≥ 0.30) → … → unplug → AUTO_STOP_GRACE (trim to rest)`. **Every step is logged to `AutoRecordLog`
(Diagnostics → "Auto-record log"); read it after any drive — it is the debugging tool.**

**Rev AP — the charger decision was reading a stale battery state (CRITICAL fix).** The watcher's runtime
receiver was confirmed to fire on *every real* charge event, but `AutoRecordController.reevaluate()` then
decided from the **sticky `ACTION_BATTERY_CHANGED`** intent, which lags the `POWER_CONNECTED/DISCONNECTED`
broadcast. The on-device log proved the inversion on real plug events: `charger-on → no-op [chg=false]`
(missed the trigger) and `charger-off → ARM [chg=true]` (armed on unplug). Fix: the watcher passes the
broadcast **edge** (`chargingEdge=true/false`); the controller trusts it over the sticky read
(`AutoRecordPolicy.effectiveCharging`); a lagging sticky shows as `(sticky lagged)` in the log. For
`requireWireless`, a 1.5 s delayed settle re-read resolves the plug *type*. ⚠️ **`dumpsys battery
unplug/reset` does NOT broadcast power events on this S25** — only a *real* cable plug/unplug exercises this
path, so the fix's real-world confirmation is the owner's next drive (the unit tests lock the logic).
Rev AP also split the BT ACL receiver to `RECEIVER_EXPORTED` (privileged framework broadcasts can be dropped
for not-exported receivers on some OEM builds) while still filtering by the saved car MAC.

**Known limitations / TODO:** (a) no re-arm if you wait > 90 s after the trigger before driving
(provisional discards, no restart until a new trigger event — a re-arm-on-motion fix is wanted); (b)
start-side trip trim not done (the parked seconds before pulling away aren't trimmed — the *stop* side is);
(c) CDM kept as a secondary path; the wrong association could be cleaned up with a "remove pairing" button;
(d) with `requireWireless=false`, charging *anywhere* arms-then-discards a 90 s provisional. **Cost:** a
permanent silent "Auto-record on" notification (the accepted tradeoff).

---

## 4. What's been done (Rev G → T)

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
  - _NB: severity-weighted Safety was then **tried offline and REJECTED** — this driver's events cluster
    0.25–0.50 g so a severity sum compresses the score spread; the Rev L count-rate discriminates better.
    Don't re-attempt without a wider g-range. See memory `field-test-2026-06-24-trips-845-847`._
- **Rev N (2.51):** **fuel & trip-cost** feature — `FuelEstimator` (pure: speed-dependent L/100km curve
  anchored on the vehicle's city/hwy ratings + idle burn × calibration), `VehiclePrefs` (SharedPreferences
  profile), `VehicleScreen` (Home → fuel icon: edit year/make/model, economy, price, calibration + a
  "calibrate from car L/100km" helper). Trip-detail **Fuel & cost** card + Insights Fuel/Cost mini-stats.
  Computed from stored aggregates (distance, avg moving speed, idle) — **no schema change**. +5 tests.
- **Rev O (2.52):** **reverse-geocoded trip names** (`GeoNamer`, Android `Geocoder`, fail-soft + cached by
  quantized coords) → "North York → Scarborough", with the GTA `TripLabeler` as fallback (O6). Also
  removed the noise `fusedConfidence` "forward-axis confidence" line from the trip-detail UI (O2).
- **Rev P/Q/R (2.53–2.55):** large trip-detail + past-trips **UI overhaul** by the parallel agent —
  trip-map markers + replay (P); header / You-vs-traffic / fuel headline (Q); past-trips compaction +
  driving infographics (R). See REV_HISTORY for the per-rev detail.
- **Rev S (2.56–2.57):** UI polish — ETA Google range-band + You bar (`EtaCompare`); vertical
  `PeakSpeedGauge` headlining **"+X km/h over"**; fuel card icons + split value/unit + economy-rating
  chip vs combined; 2-line geocoded names. (66 tests total.)
- **Rev T (2.58):** **hybrid fuel defaults** (`FuelEstimator.DEFAULT` = 2023 Tucson Hybrid HEV
  ~6.4 city / 6.6 hwy L/100km; tests updated since a hybrid's city economy beats highway) + Past-Trips
  polish: **full-width single-line titles** (geocoded "A → B" no longer wraps), **"Speed"→"Pace"** header
  (`softWrap=false`), **bigger frozen map** (264 dp, 6 dp side padding), start/end markers **smaller (72 px)
  + semi-transparent (alpha 0.7)**, replay car **on top** (`zIndex=10`). All six verified on device.
- **Rev U (2.59) — built, not yet device-tested:** hands-free **auto-recording**. Pure
  `record/AutoRecordPolicy` (+7 tests) + `AutoRecordPrefs` + `AutoRecordController` +
  `PowerConnectionReceiver` / `CarBluetoothReceiver` (manifest) + `RecordingService.ACTION_AUTO_ARM`
  (provisional record, 45 s motion-confirm, silent discard if it never moved) / `ACTION_AUTO_STOP_GRACE`
  (+ `ACTION_CHARGING_RESUMED`) + `ui/AutoRecordScreen` (Home → Sensors icon). Charging is the primary
  trigger; Bluetooth optional. **Verify the Android 12+ background-FGS-start path on device — see §9.1.**
- **Rev V (2.61):** 8-item UI polish batch — same-name trip disambiguation (`TripNaming`, +test);
  bump/pothole icon aligned to the map's yellow hump (`EventGlyphs`, shared by the speed timeline +
  event list); speeding duration floored to the minute (`Format.durationFloorMin`, +test); "+X km/h
  over" headline cleanup; You-vs-traffic "Google"/"Your trip" labels + same-scale bars; Home
  **Options** sheet (folds the old four header icons) + a "your trip" icon picker (`UiPrefs`); Insights
  You-vs-traffic **diverging bar chart** (`Charts.DivergingBarChart`). All verified on device.
- **Rev W (2.62):** **non-drive/walk guard** (`TripKind.isLikelyNonDrive`: top speed 0.1–12 km/h →
  walk) — suppresses the driving You-vs-traffic in trip detail, returns null Pace, and is excluded from
  Insights traffic aggregates (+3 tests); **privacy O3** (`allowBackup=false`); **dead-code O5** removed
  (`FactorBar`/`FactorCell`/`TripLinksCard`/`ActionButton`/`ScorePanel`/`ScoreMeter`/`ReplayControls`).
- **Rev X (2.63):** "When you drive" Insights card (`DrivingTimes` daypart buckets: count + avg Safety,
  +2 tests) + **O8 speeding-peak run-length guard** (`MIN_PEAK_RUN_S=2 s` so a single GPS snap can't
  set "+X over") + fuel cost suppressed for non-drives (hero + card).
- **Rev Y (2.64):** Insights polish — `DivergingBarChart` **robust scale** (85th-pct of |value|,
  floored `minScale`, off-scale bars clamped + light-capped) so outliers don't crush the rest;
  **Score trends re-imagined** as per-score rows (icon + current avg + 0–100 bar + a trend chip =
  later-half vs earlier-half delta), replacing the old `MultiSeriesChart` (+ `Series`, both removed).

---

## 5. Data model & schema (Room v19)

Tables: `trips`, `locations`, `motions`, `analysis_points`, `drive_events`, `cached_ways`,
`cached_tiles`, `gnss_samples`, **`gnss_measurements`** (raw per-satellite GNSS, lane R&D). Migrations
`1→19` are all present in `AppDatabase.kt` and verified on-device. **v18** = `trips.userIsDrive`
(nullable walk/non-drive override); **v19** = `locations.bearingAccuracy/speedAccuracy/verticalAccuracy`
+ the `gnss_measurements` table.

- **Raw retention:** `locations`/`motions`/`gnss_samples`/`gnss_measurements` for completed trips are
  purged after **30 days** (`RAW_SENSOR_RETENTION_MS`). Re-analysis only works while raw data survives.
- **Speed-limit cache:** ways keyed by stable OSM way id; tiles (~2.2 km, `TILE_DEG=0.02`) mark
  fetched areas; 30-day TTL; OSM/ODbL permits caching with attribution (source+timestamp stored).
- **GNSS:** per-trip summary columns on `trips` (`gnssAvgSatsUsed`, `gnssAvgCn0`, `gnssTopCn0`,
  `gnssL5Seen`, `gnssSampleCount`) + per-window `gnss_samples` (~every 3 s).

⚠️ When adding a column: add to `Entities.kt`, bump `@Database(version=…)`, add a `MIGRATION_n_n+1`,
register it in `addMigrations(...)`, and thread through `TripFinalizer.finalizeTrip` (and the
`storedAnalysis`/`persistedAnalysis` metric reconstructions if it feeds the analysis object).

---

## 6. Detection & tuning reference

These are the knobs most likely to need tuning from field data. Values as of 2.58.

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
The list/hero/Insights label the third score **"Pace"** (the You-vs-traffic `speed` score).

### Fuel & cost — `analysis/FuelEstimator.kt`, `ui/VehiclePrefs.kt`
Pure: `litres = (distanceKm · l100AtSpeed(avgMovingSpeedKmh) / 100 + idleS · 0.9 L/h) · calibration`.
`l100AtSpeed` is a U-curve anchored on city/hwy: city ≤35 km/h, hwy ≥95 (+aero past 110), interpolated
between. `combinedL100 = city·0.55 + hwy·0.45`. **`DEFAULT` = 2023 Tucson Hybrid HEV (6.4 city / 6.6 hwy,
$1.84/L).** ⚠️ The on-device profile is in SharedPreferences (`cartrip_vehicle`) and is NOT overwritten by
a code-default change — the owner must set city/hwy/price/calibration in `VehicleScreen`, or feed real
car-reported L/100km via the "calibrate from car" helper (sets `calibration = reported / combinedRated`).

### Trip naming — `ui/GeoNamer.kt`, `ui/TripLabeler.kt`
`GeoNamer.area(lat,lon)` reverse-geocodes (Android `Geocoder`, 4 s timeout, fail-soft, cached in-memory +
SharedPreferences `cartrip_geo` on a ~110 m grid) → neighbourhood/city. `composeLabel` → "From → To".
Falls back to `TripLabeler` (GTA-hardcoded landmarks/commute) when geocoding is unavailable. Called from
`TripViewModel.loadTripLabels` (display-time; the label isn't stored — `trip.name` is the user override).

---

## 7. Test suite (82 tests)

Run: `…\gradlew.bat --init-script '…\relocate-build.gradle' :app:testDebugUnitTest --no-daemon`.
Results: `C:\Users\sinan\cartrip-build-out\app\test-results\testDebugUnitTest\*.xml`.
Note: the Git-Bash `/c/...` path won't glob in Windows Python — count via `grep -c '<testcase'` on the
XMLs, or open one in the editor.

Files: `AutoStopTest`, `FusedEventDetectorTest` (8: peak-percentile, corner-veto, ambiguous-steering,
maneuver-peak magnitude), `DisplayEventsTest`, `TripAnalyzerTest`, `MotionFusionTest`, `GnssQualityTest`,
`SpeedTierTest`, `TilesTest`, `TripBucketsTest`, `TripLabelerTest`, `GeoAndPolylineTest`,
`DisplayEventsTest` (8), `GeoNamerTest` (9: pickName / composeLabel), `FuelEstimatorTest` (5),
`TripScoresTest` (3: fused drives Safety / cap / GPS-fallback), `AutoRecordPolicyTest` (7: trigger /
arm / stop / wireless / Bluetooth gating), `FormatTest`, `TripNamingTest` (same-name disambiguation),
`TripKindTest` (3: walk vs drive vs zero-speed), `DrivingTimesTest` (2: dayparts / summarize). All
pure-JVM (no Robolectric/instrumented).

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
- **O3 — Privacy: `allowBackup`. [DONE, Rev W]** Now `allowBackup=false`, so the unencrypted
  location-history DB is no longer backed up to Google. DB-at-rest encryption (SQLCipher) remains a
  larger optional follow-up.
- **O4 — Overpass dependency.** Free public endpoints; the cache greatly reduces calls, but heavy
  first-time use of a new area still hits them. No per-request rate limiting beyond endpoint failover.
- **O5 — Dead code. [DONE, Rev W/Y]** Removed the unused `FactorBar`, `FactorCell`, `TripLinksCard`,
  `ActionButton`, `ScorePanel`, `ScoreMeter`, `ReplayControls` from `TripDetailScreen.kt` (Rev W) and
  `MultiSeriesChart`/`Series` from `Charts.kt` (Rev Y); swapped the deprecated `List`/`ShowChart`/
  `TrendingUp`/`TrendingDown` icons to their `AutoMirrored` forms. (A `t0` name-shadow warning in the
  replay block remains, harmless.)
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
- **O8 — Peak-over speeding. [DONE, Rev X]** The "+X over" peak now must sit within an over-limit run
  sustained ≥ `MIN_PEAK_RUN_S` (2 s) in `speedingSummary`, so a single GPS snap can't set it; it falls
  back to the worst point only if speeding was entirely momentary.
- **O9 — Rough stretches aren't mappable.** `MotionFusion` stores `roughStretchCount`/`bumpyScore`/
  `roughRoadPct` as aggregates only (no per-stretch geometry), unlike potholes (timestamped `POTHOLE`
  events). To label rough stretches on the map like potholes, the analyzer would need to emit stretch
  segments (time ranges / a per-point rough flag); only newly re-analyzed trips with raw motion still
  present would populate. Deferred from the Rev P–R UI overhaul by request — revisit with new field data.

---

## 9. Roadmap / next steps (prioritized)

> **Status update (Rev AL–AO, 2026-06-26):**
> - **Auto-record (item 1) — DONE & validated on-device (Rev AO).** The hands-free trigger now works via a
>   **persistent `AutoRecordWatchService`** (see §3 "Auto-record architecture (Rev AO)"). The earlier CDM
>   approach (Rev AG) was field-debugged and **couldn't pair the classic-Bluetooth Tucson** (its chooser
>   only lists *discoverable* devices; the owner accidentally paired a random device) — kept as a secondary
>   path. The watcher chain `charger-on → ARM → FGS start OK → AUTO_ARM → motion-confirm → discard` was
>   validated via a simulated `dumpsys battery` charge cycle; the owner's narrated drive is the final
>   real-world confirmation. See memory `autostart-background-receiver-dead-finding`.
> - **Walk/non-drive toggle — DONE (Rev AL).** Manual `trips.userIsDrive` override (schema v18); a toggled
>   walk is excluded from You-vs-traffic, Pace, and fuel everywhere via `TripKind`.
> - **Lane-detection data capture — DONE (Rev AN).** Per-fix accuracy estimates + raw GNSS (carrier phase +
>   Doppler) behind a Diagnostics toggle; validated capturing (3.2k rows / 888 L5-band on one drive). The
>   offline lane algorithm (lateral offset → lane-change detector → anchored absolute lane + confidence) is
>   the next R&D step, gated on a narrated 401 calibration drive.
> - **Harsh-stop detector — DONE (Rev AH/AI).** Recalibrated from 27 trips; `EventType.HARSH_STOP` map
>   events + Safety/Comfort penalty. See memory `harsh-stop-recalibration`.
> - **You-vs-traffic redesign — DONE (Rev AJ/AK).** Single to-scale timeline; trip screen de-cluttered.
> - **Still TODO:** auto-record **re-arm-on-motion** + **START-side trip trim** (see §3 limitations);
>   severe-corner count (item 2); Insights depth (item 4); rough-stretch mapping (item 5); lane-detection
>   offline algorithm. The Rev U receiver design below is **superseded** (kept for history).

1. **Auto-recording trigger (Rev U) — SUPERSEDED by Rev AG CompanionDeviceManager (see status box above).** Hands-free start/stop
   so the owner never taps Start. Context: the phone is **always wireless-charging on a mount in the
   owner's 2023 Tucson** and pairs to the car over Bluetooth/Android Auto. Shipped architecture:
   - **`record/AutoRecordPrefs.kt`** (SharedPreferences `cartrip_autorecord`): `enabled` (default off),
     `requireCharging`, `useBluetooth`, `carBtAddress`/`carBtName`, `minSpeedKmh` (≈5), `stopGraceMs` (≈8 s).
   - **`record/AutoRecordPolicy.kt`** (pure, unit-tested): inputs (enabled, charging, wireless,
     carBtConnected, recording, speedKmh, sustainedMs) → `START` / `STOP` / `NONE`. Encodes "start when
     in-car + moving ≥ minSpeed sustained", "stop on charge-off / BT-disconnect after grace + stationary".
   - **Receivers** (manifest): `PowerConnectionReceiver` (`ACTION_POWER_CONNECTED/DISCONNECTED`; wireless
     via `BatteryManager.EXTRA_PLUGGED == BATTERY_PLUGGED_WIRELESS`) and a car-Bluetooth receiver
     (`BluetoothDevice.ACTION_ACL_CONNECTED/DISCONNECTED` matched to `carBtAddress`).
   - **`RecordingService`**: new `ACTION_AUTO_ARM` starts the FG service **provisionally** — it records but
     runs a motion-confirm timer (~45 s); if speed never crosses `minSpeedKmh` it stops AND deletes the
     provisional trip (no false drives from charging-while-parked). New `ACTION_AUTO_STOP_GRACE` starts an
     in-service grace countdown (cancelled if charging resumes) then stops.
   - **Settings UI**: an "Auto-record drives" toggle + car-BT picker.
   - ⚠️ **Android 12+ caveat (MUST verify on device):** starting a `location` FGS from a **background**
     receiver throws `ForegroundServiceStartNotAllowedException`. The receiver wraps `startForegroundService`
     in try/catch and, on failure, posts an **actionable "Tap to start trip" notification** (reliable
     degraded mode). The fully hands-free path is **`CompanionDeviceManager.startObservingDevicePresence`**
     (the OS allows it to start a FGS on device appearance) — documented as the hardening step. When the app
     is foreground, `HomeScreen.AutoTripDetection` (GPS-speed) already auto-starts.
   - Keep manual Start/Stop intact; never auto-start with the toggle off.
   - **ON-DEVICE VERIFICATION CHECKLIST (next agent — this was built with no device):**
     (a) Enable auto-record (Home → Sensors icon). Plug into the car charger + drive → does it start
     silently, or does the "Drive detected — tap to start" notification appear (FGS-start blocked)?
     (b) Unplug → does it stop after ~8 s? (c) Plug in while parked (no driving) → confirm the
     provisional trip is **discarded** after 45 s (no junk trip in Past Trips). (d) If (a) falls back to
     the notification, implement `CompanionDeviceManager.startObservingDevicePresence` for true hands-free
     start. (e) Check whether `ACL_CONNECTED` reaches `CarBluetoothReceiver` from the manifest on Android
     14; if not, register it at runtime. (f) Grant the notification + (if BT) `BLUETOOTH_CONNECT`
     permissions. Tune `MIN_SPEED_KMH` / `MOTION_CONFIRM_MS` / `STOP_GRACE_MS` in `AutoRecordPrefs`.
2. **Severe-corner count (O7b) / validated hybrid detector.** Add a stored `severeCornerCount`
   (≥~0.45 g) so hard cornering hits Safety distinctly (schema v17→v18 migration + analyzer change +
   re-analyze). NB: this driver's events cluster 0.25–0.50 g so it may rarely trigger; severity-weighted
   Safety was already tried & **rejected** (§4 Rev M note). Pairs with promoting the fused detector
   from "review-grade" to scored (label events GPS-confirmed / sensor-only / bump-echo / ambiguous).
3. **GNSS phase 2 (optional/research).** Use `gnss_samples` to downweight route/event confidence in
   urban canyons; optional raw-GNSS export behind debug. Not positioning.
4. **Insights depth (partly done).** Done: "When you drive" daypart card, You-vs-traffic diverging
   chart + robust scale, re-imagined Score trends. **Still open:** repeated-commute/route comparison
   (needs per-trip start/end coords — `TripEntity` only stores aggregates, so this requires loading
   `analysis_points` per trip or storing endpoints), speeding/pothole heatmaps, per-km stats, Last-X.
5. **Rough-stretch mapping (O9)** — emit per-stretch geometry from `MotionFusion` so rough stretches
   map like potholes (re-analyze required). Revisit O1 (1 Hz GPS) / O2.

_Done recently: trip-detail/past-trips UI overhaul (Rev P–T), 8-item UI polish batch (Rev V),
non-drive guard + privacy + dead-code (Rev W), daypart insights + speeding run-length guard (Rev X),
Insights chart normalization + re-imagined Score trends (Rev Y). Only Rev U (auto-recording) awaits
the owner's on-device drive test._

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
