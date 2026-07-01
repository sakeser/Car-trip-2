# Car Trip Analyzer — Comprehensive Handoff

_Last updated: 2026-07-01 · Source **3.42 (build 153), schema v22** · **The premium-modular redesign is now
MERGED to `main`** (branch `ux-premium-modular-v1` → `main`, merge commit `3dcb781`, pushed): `:core-engine`
extraction + `com.cartrip.engine.api` seam, the `:ui-next` premium Compose module (Trips/Health bottom-nav shell +
**map-first Trip Detail**, still debug-gated behind Diagnostics), Rev CX Driving Intelligence in the legacy
screens, Connected-Features privacy gating, and speed-interruption evidence tooling (real-data calibration →
**no new detector**). Legacy `app/ui` is unchanged as the working oracle. ⚠️ **Post-merge commit `8f6c44e`
(map-first detail) is on LOCAL `main`, NOT pushed** (push pending owner OK; check `git log origin/main..main`).
**For continuing the premium UI, the detailed next-agent handoff — `:ui-next` state, the engine-API surface to
build against, the boundary rule, build workflow, gaps, and the next UI priorities from the owner UX spec — is at
the TOP of `REDESIGN_PHASE1.md`.** · Prior source **3.37 (build 148)** · **Rev CX (branch `ux-premium-modular-v1`):
Driving Intelligence three-pillar consolidation** — new pure `analysis/DrivingIntelligence.kt` (Smoothness /
Demand-Load / Efficiency + a 2×2 style×demand "Drive Quality" conditional headline; NO blended composite),
`TripScores` moved `ui`→`analysis` (colour → `ui/ScoreColors`), surfaced in legacy Trip Detail / Insights /
AI export; Safety/Comfort/Pace/Stress/Fuel kept as drill-downs. Build green, +8 tests; **on-device verify owed**.
Source-of-truth `DRIVING_INTELLIGENCE_SCORING.md`; Phases C–E tabled. · Prior: **3.36 (build 147)** (Rev CW +
phase 2: **Driver Load / "Drive readiness"** — `analysis/DriverLoad.kt`, a pure recency-weighted leaky integrator of stress-weighted driving
time (TAU 28.8 h) that builds with recent stressful driving + decays with rest, DB-replay-calibrated
(`SATURATION_K=1.0`), surfaced as an Insights card with load/readiness + a one-line read + a 24 h recovery
curve + a not-a-medical-assessment disclaimer; **device-verified** live load 67/Elevated decaying from the
morning peak. **Phase 2:** an **ACWR** acute(~7 d)-vs-chronic(~28 d) "above your recent norm" overload chip
(red > 1.5), correctly suppressed until ≥14 d of history (owner had 7.1 d) — render-checked on-device;
**222 tests**) · atop Rev CT-fuel + CU (v3.34/build 145): a **"Fuel economy vs your average"** `PercentChangeChart` (smoothed
%-deviation from your window mean, green/red fill + a dashed live-OEM-rating line) + **bar-sizing pass 2** (the
"When you drive" daypart bars scale through `BarScale`, ~80% busiest) · atop Rev CV+CT (v3.33/build 144):
trip-view defaults + **Insights chart/filter overhaul** — dynamic `1/3/7/30/All` days filter, Drive-Stress
chart fixed 0..100 with y-axis labels + no min/max footer, bumps hidden from the trip "All events" list by
default · atop Rev CS: **Drive Stress Score v2** —
a stop-and-go / no-break demand-gated model + `analysis/StopAndGo.kt` + schema v22; atop the
Rev CP cont. v3.28–v3.31: reset-to-automatic, OSM/ODbL attribution, bar-scale audit, AI-export traffic/daypart
sections, export value-mapping tests, CR export retention, migration foundation, Places scaffold ·
**Rev CS device-verified on the S25 and pushed**: the v22 migration applied cleanly (`user_version=22`), Insights
"Re-analyze all trips" populated all **47/47** trips, and the live Drive-Stress scores match the calibration —
**trip 1187 = 40, trip 1189 = 78** (the stop-and-go crawl that used to score 18/Calm) · **208 unit tests,
all green** (incl. the 1187~40 / 1189~78 stress calibration anchors) · Earlier this session: **S25 verified 3.31 (build 142)**: the OSM/ODbL "©" attribution renders correctly (it's
built via `0xA9.toChar()` to dodge the Cp1252 mojibake trap) and the Past-Trips duration bars are no longer
edge-to-edge. **Field-validated on the 2026-06-29 home→work commute (trip 1187, 43.8 km / 41 min):**
drawdowns (4), Drive Stress **42 (Moderate)**, "you vs traffic **5 min faster**" + ~35% congestion vs
free-flow, and the **"North York → Work"** auto-name all matched a DB-replay exactly. **Correction
(2026-06-29):** the Past-Trips **open affordance is NOT broken** — it is an intentional **two-tap** (tap once
to preview the route on the frozen map, tap the *same* trip again to open), now **verified working
on-device**; the only wart is that the first tap inserts a preview line that shifts the row, so the second
tap is easy to miss (see §14 CP). "Reset to automatic" stays verified by-construction (the trip-detail ⋮
wasn't reliably clickable via script). Schema **v21**. **Newest arc (Rev BY–CN, 2026-06-28 — the "revision-plan"
session):** executed a comprehensive batch plan against the §9 backlog. **Batch 1 (BY–CD):** you-vs-traffic
"you"-line white-edge fix; "Load sample data" + Sheets card moved into the Options sheet; **Home-screen
auto-record quick toggle**; **Past-trips recency filter** (24h/3d/7d/30d/All, default 7d); fuel "spend over
time" → **smoothed $/week spend-rate**; **satellite/aerial map toggle** (shared `MapControls` + `UiPrefs`).
**Batch 2 (CE–CH):** Driving card shows **all events by default** with tap-to-map; **dynamic replay camera**
(then **revised in v3.18 to walk-only** — drives keep the whole-route replay, owner feedback); Insights
**"this window vs previous" delta strip** (re-imagined Health metrics); **walk trip-detail hero** mirrors the
list. **Batch 3 (CI, schema v21):** **Drawdowns** metric — forced cruise→slow→recover events
(`analysis/Drawdowns.kt`), DB-replay-validated; `trips.drawdownCount`/`drawdownSeverity`. **Batch 4 (CJ):**
**Drive Stress Score** (`ui/StressScore.kt`) — composite of drawdowns + hard events + congestion + speeding +
load, calibrated by DB-replay. **CK–CN:** compact past-trips filter + **list scrollbar affordance**; smarter
**you-vs-traffic bar sizing** (nice axis, ~80% fill, minute scale); **"Share for AI insights"** export
(`ui/AiInsightsExport.kt`) — compact markdown summary for ChatGPT/Claude. **Skipped this session:** Places API
(paid — owner exploring), CM POI naming (depends on Places), CO encrypt+biometric (queued). See
`REV_HISTORY.md` for per-rev detail, **`ROADMAP_NEW.md`** for the new backlog, **§13** for this session's
lessons, and the **owner backlog at the top of §9**._

_Prior arc (Rev BA–BX, 2026-06-28): auto-record re-arm-on-motion (BA) + START-side trip
trim (BB); **magnitude-weighted speeding penalty** for Safety + limit-misread grace (BF) and a backfill (BG);
**frequency-learned Home (BC) + Work (BH)** for trip names; **Fuel & cost Insights** with spend buckets +
smoothed $/km (BL/BN); **auto gas price** from Ontario's weekly Toronto average (BP); **walk flagging** in
trip cards (BD); **cross-trip trouble-spots map** with g-force slider, glyph pins, tap-to-instance detail,
list cards (BJ/BO/BT/BU/BX); Insights cleanup + you-vs-traffic 30-day headline (BR/BV); **re-analyze-all** (BV);
Google-blue routes + **single-finger map pan** (BW/BX). **Reverted** the Rev BK GPS battery-throttle (BQ) — it
degraded walk GPS._

_Earlier (Rev AL–AZ): hands-free auto-record via the persistent watcher; pothole-cluster + bump-echo fixes._
**Hands-free auto-record now works** via a persistent "armed" watcher (Rev AO); **Rev AP** fixed the
charger-trigger (stale-sticky→broadcast-edge); **Rev AQ** fixed a background crash — a `location` FGS started
from the background on Android 14 needs **`ACCESS_BACKGROUND_LOCATION` ("Allow all the time")**, which the
app now declares + requests (and the start is try/caught so it can't crash); **Rev AR** added recording
haptics + a code-verified failure-mode matrix (§3); **Rev AS** addressed a Codex review (the "Allow all the
time" CTA now opens Settings on Android 11+, a blocked auto-arm no longer logs a phantom "provisional
started"). **Workspace is the repo root `C:\Users\sinan\OneDrive\Desktop\cartrip`** (`cartrip-main` worktree
removed during consolidation — `main` is the only branch). The owner's next real background charge test is
the final validation._

This is the **authoritative** continuation brief. It supersedes `CLAUDE_CODE_HANDOFF.md`
(June 23, pre-Rev-G — now historical). `REV_HISTORY.md` has the per-revision changelog;
`FIELD_TEST_PLAN.md` has the on-road test scenarios.

**Premium UI direction:** for current `:ui-next` work, read the top of `REDESIGN_PHASE1.md` first. It now captures
the owner directive that today's numeric-heavy `:ui-next` screens are transitional framework scaffolding, not the
final premium design standard. Future work should keep expanding the product shape while planning a later pass to
make the UI cleaner, sleeker, less numeric, and more interpretation-led than legacy.

---

## 1. TL;DR — current state

- Android app (Kotlin, Jetpack Compose, Room, Google Maps) that records trips from phone GPS +
  motion sensors, analyzes them offline, and enriches with Google traffic ETAs, OSM speed limits,
  and Google Sheets sync. Package `com.cartrip.analyzer`.
- **Single workspace = the repo root `C:\Users\sinan\OneDrive\Desktop\cartrip`** on **`main`** (the only
  branch). The `cartrip-main` linked worktree and the stale `ux-redesign-v1` / `rev-g-functional` branches
  were removed on 2026-06-26; the old UX redesign is preserved as tags `archive/ux-redesign-v1-wip` +
  `archive/pre-ux-redesign-wip` (also pushed to origin). Branch `main` is current/pushed; verify with
  `git log origin/main..main`. Pushing to `main` needs explicit per-turn user authorization.
- Source + **S25 installed = 3.27 (build 138)**. Device auto-locks fast; for a
  UI-verify pass ask the owner to unlock it, then `adb shell svc power stayon true` keeps the screen
  awake (reset with `stayon false` after). Screencap to a **non-OneDrive** path.
- Unit tests all green (**187 tests**, all pure-JVM). Room schema **v21**: v18 `trips.userIsDrive` (walk
  override); v19 per-fix GPS accuracy + `gnss_measurements` (lane R&D); v20 `trips.speedingSeverity`
  (magnitude-weighted speeding for Safety); **v21 `trips.drawdownCount`/`drawdownSeverity`** (forced
  slowdowns — feeds the Drive Stress Score).
- **Process note (learned the hard way):** bump `versionName`/`versionCode` in `app/build.gradle.kts`
  **before** the final `assembleDebug`, or the installed APK keeps the *old* version label (happened twice).
  Verify with `dumpsys package ... | grep versionName` after install.
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
- **Scrolling Insights past the trouble-spots map:** the embedded map has single-finger pan, so an
  `input swipe` that *starts inside the map* is eaten by the map (the list won't scroll). Start the swipe
  **above or below** the map region (e.g. on a trouble-spot card) to scroll the `LazyColumn`. The fuel /
  daypart cards are below the map, so scroll from a card, not the map.

---

## 3. Architecture map

Pipeline: **record → analyze (offline) → persist → enrich → present.**

| Area | Key files |
|---|---|
| Recording | `record/RecordingService.kt` (FG service, GPS+sensors+GNSS+raw-GNSS; `ACTION_AUTO_ARM` = provisional record + 90 s motion-confirm by GPS **or** accelerometer vibration + discard; `ACTION_AUTO_STOP_GRACE` = retrospective-trim stop), `record/RecordingState.kt` (live state), `record/AutoStop.kt` (retrospective end-time, pure) |
| Auto-record | **`record/AutoRecordWatchService.kt`** = persistent "armed" FGS, the reliable hands-free trigger (see note below); `record/AutoRecordController.kt` (decision dispatch → arm/stop), `record/AutoRecordPolicy.kt` (pure trigger logic, unit-tested), `record/AutoRecordPrefs.kt`, `record/AutoRecordLog.kt` (Diagnostics decision log), `record/BootReceiver.kt` (re-arm after reboot), `record/CompanionCarManager.kt` + `record/CarPresenceService.kt` (CompanionDeviceManager — secondary/unreliable for classic-BT cars), `record/GnssLoggingPrefs.kt` (raw-GNSS toggle). Dead: `record/PowerConnectionReceiver.kt` / `record/CarBluetoothReceiver.kt` (manifest registration removed in Rev AO) |
| Analysis | `analysis/TripAnalyzer.kt` (Kalman/RTS speed+accel, events, metrics), `analysis/MotionFusion.kt` (potholes/rough-road/harsh-stops), `analysis/FusedEventDetector.kt` (magnitude-first sensor detector), `analysis/FuelEstimator.kt` (pure fuel/cost model), `analysis/Drawdowns.kt`, `analysis/StressScore.kt` (Drive Stress Score — pure; Rev CP-decoupled from ui), `analysis/TripKind.kt` (walk/non-drive guard — pure), `analysis/GnssQuality.kt`, `analysis/SpeedTier.kt` |
| Data | `data/Entities.kt`, `data/AppDatabase.kt` (Room, schema **v21** + migrations), `data/TripDao.kt`, `data/TripFinalizer.kt`, `data/TripStatus.kt` |
| Cloud | `cloud/SpeedLimits.kt` (OSM/Overpass + tile cache + pure `speedingSummary` w/ magnitude-weighted severity + limit-drop grace), `cloud/Tiles.kt`, `cloud/RoutesClient.kt` (Google Routes ETA), `cloud/GasPrice.kt` (auto fuel price from Ontario weekly Toronto CSV), `cloud/TripSync.kt`/`SheetsClient.kt`/`GoogleAuth.kt` (Sheets) |
| UI | `MainActivity.kt` (nav), `ui/HomeScreen.kt` (record + landscape big button), `ui/TripDetailScreen.kt` (hero, You-vs-Traffic, replay, Driving, map), `ui/TripListScreen.kt` (frozen map + buckets), `ui/TripMap.kt`, `ui/DisplayEvents.kt` (event cleanup/clustering), `ui/TripScores.kt`, `ui/TripDataQuality.kt`, `ui/DebugScreen.kt`, `ui/TripBuckets.kt`, `ui/Format.kt`, `ui/TripLabeler.kt`, `ui/GeoNamer.kt` (reverse-geocode), `ui/VehiclePrefs.kt` + `ui/VehicleScreen.kt` (fuel profile), `ui/InsightsScreen.kt`, `ui/Charts.kt` (TimeSeries/MiniSparkline/`DivergingBarChart`), `ui/TripNaming.kt` (same-name disambiguation), `ui/StressColors.kt` (stress green->red scale; logic is `analysis/StressScore`), `ui/DrivingTimes.kt` (daypart insights), `ui/EventGlyphs.kt` (shared bump glyph), `ui/UiPrefs.kt` (you-icon pref + event-g threshold), `ui/Components.kt` (shared bits + Options sheet), `ui/HomeDetector.kt` (frequency-learned home/work), `ui/EventHotspots.kt` (cross-trip recurring-event clustering), `ui/TroubleSpotsMap.kt` (hotspot map + tap-to-instance sheet), `ui/FuelInsights.kt` (fuel history/spend/$-per-km), `ui/TripHeatMap.kt` (dormant route heatmap) |

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

**Rev AQ — background location is REQUIRED, and the FGS start must not crash.** The first real field test
crashed the app when plugging in with the app **closed** (foreground worked). Cause: on Android 14, starting
a `location`-typed FGS from the **background** requires the app to currently hold location access —
`ACCESS_BACKGROUND_LOCATION` ("Allow all the time"). The watcher start succeeds (CDM exemption) but
`RecordingService.startInForeground()` → `startForeground(…, TYPE_LOCATION)` threw `SecurityException`,
which escaped `onStartCommand` and crash-looped (the watcher re-armed each restart). Fix: `startInForeground()`
is now try/caught (returns `Boolean`; on failure `onForegroundStartBlocked()` logs + posts a "tap to enable
hands-free" notice + `stopSelf()` — never crashes), and the app declares `ACCESS_BACKGROUND_LOCATION` with an
Auto-record card that requests "Allow all the time". **So hands-free background start needs the owner to grant
"Allow all the time" location** (done on the S25 via `pm grant`; the in-app card covers a fresh install).

**Known limitations / TODO:** (a) ~~no re-arm if you wait > 90 s after the trigger before driving~~ **DONE
(Rev BA)** — `AutoRecordWatchService` runs `MotionRearmDetector` on the accelerometer while
armed-but-not-recording and re-arms on sustained motion; (b) ~~start-side trip trim not done~~ **DONE
(Rev BB)** — `AutoStart.retrospectiveStartTime` trims the parked prefix on auto trips, the mirror of the
stop-side `AutoStop` trim (the *stop* side was already done); (c) CDM kept as a secondary path; the wrong
association could be cleaned up with a "remove pairing" button;
(d) with `requireWireless=false`, charging *anywhere* arms-then-discards a 90 s provisional. **Cost:** a
permanent silent "Auto-record on" notification (the accepted tradeoff).

### Degraded-mode & failure-mode matrix (code-verified 2026-06-26, Rev AQ)

Reviewed by reading the code paths, not by driving — so the owner can field-test the few rows marked
**DRIVE** instead of everything. "Code ✓" = the path exists and is exercised/guarded in source; "Device ✓"
= also reproduced on the S25 this session.

| Scenario | Behavior | Status |
|---|---|---|
| Plug in, **app open** | foreground arm → records | Device ✓ (owner saw it) |
| Plug in, **app closed**, bg-location granted | watcher arms → records hands-free | Code ✓ · **DRIVE** |
| Plug in, **app closed**, bg-location **missing** | log + "tap to enable hands-free" notice, `stopSelf`, no crash (Rev AQ) | Code ✓ |
| FGS start blocked (A12+ bg, no CDM exemption) | `arm()` try/catch → tap-to-start notification | Code ✓ |
| Unplug **during** a trip | 8 s grace → trim back to rest → save as AUTO_STOP | Code ✓ |
| Unplug then **replug within 8 s** | `CHARGING_RESUMED` cancels grace → keeps recording | Code ✓ |
| Plug in but **never drive** (≤90 s) | motion-confirm fails → silent discard, no junk trip | Device ✓ |
| Plug in, **wait > 90 s**, then drive | watcher re-arms on sustained accelerometer motion → fresh provisional records | Code ✓ (Rev BA) · **DRIVE** |
| Auto trip starts while parked (warm-up) | leading parked prefix trimmed to first motion; over-trim → too-short discard | Code ✓ (Rev BB) |
| Charger event **while already recording** | "trigger still present, cancel grace" (idempotent) | Code ✓ |
| `requireWireless` ON + **wired** charging | no arm (after 1.5 s settle re-read of plug type) | Code ✓ |
| Watcher process killed (low memory) | `START_STICKY` restart → re-arms | Code ✓ |
| Watcher **force-stopped** by user | stays dead until app opened / reboot | **KNOWN LIMIT** |
| **Reboot** | `BootReceiver` re-arms the watcher | Code ✓ · **DRIVE** |
| `dumpsys battery` simulate charge | does NOT broadcast power events on this S25 → can't simulate | Documented (real cable only) |
| **No GPS** at start (garage) | motion-confirm via accelerometer vibration (≥ 0.30) | Code ✓ |
| GPS lost mid-trip (≥ 2 min) | gap counted (`gpsGapCount`), track continues, no crash | Code ✓ |
| Motion sensor stalls (≥ 15 s) | auto-restart sensors, capped at 6 attempts | Code ✓ |
| Trip ends **< 5 m** or **< 10 s** (accidental Start→Stop) | deleted, not saved → "Trip not recorded" notice + in-app message (Rev AV) | Device ✓ |
| Service killed mid-trip (OS/crash) | next start finalizes the orphan as **PARTIAL** (`APP_RECOVERY`) | Code ✓ |
| Location permission **revoked** mid-trip | `SecurityException` caught; location stops, trip keeps what it has | Code ✓ |
| Raw GNSS enabled | per-sat rows captured + cleaned on delete/trim/discard (Rev AP) | Device ✓ |
| GNSS callback failure | wrapped — never aborts the core location/motion flush | Code ✓ |
| `POST_NOTIFICATIONS` denied (A13+) | recording still runs; fallback notices fail silently | Minor degraded |
| CDM duplicate/wrong association fires | arms → motion-confirm discards the non-drive | Low harm · open cleanup |

**Only three things genuinely need a real drive** (everything else is settled in code): (1) background
hands-free start end-to-end with bg-location granted; (2) reboot re-arm; (3) the new haptic timing *feel*.

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

## 5. Data model & schema (Room v21)
<!-- v20 (Rev BF): trips.speedingSeverity. v21 (Rev CI): trips.drawdownCount + trips.drawdownSeverity. -->


Tables: `trips`, `locations`, `motions`, `analysis_points`, `drive_events`, `cached_ways`,
`cached_tiles`, `gnss_samples`, **`gnss_measurements`** (raw per-satellite GNSS, lane R&D). Migrations
`1→21` are all present in `AppDatabase.kt` and verified on-device. **v18** = `trips.userIsDrive`
(nullable walk/non-drive override); **v19** = `locations.bearingAccuracy/speedAccuracy/verticalAccuracy`
+ the `gnss_measurements` table; **v20** = `trips.speedingSeverity`; **v21 (Rev CI)** =
`trips.drawdownCount` + `trips.drawdownSeverity` (forced cruise→slow→recover events — feeds the Drive
Stress Score; computed in `TripFinalizer` from the limit-annotated track, recomputed on re-analyze).

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
| `LONG_MIN_G_LOW` / `LONG_MIN_G_HIGH` | 0.42 / 0.35 | g needed for a brake/accel at ≤20 / ≥50 km/h (Rev BE) |
| `LONG_LOW_SPEED_KMH` / `LONG_HIGH_SPEED_KMH` | 20 / 50 | speed band the longitudinal threshold ramps across |

Brake/accel display threshold is now **speed-aware** (Rev BE, owner-requested): ramps from 0.42 g at a crawl
to 0.35 g at speed (was a flat 0.28 g), so low-speed bumps/parking nudges stop being flagged. This is the
DISPLAY layer only; the detector/scoring thresholds (`HARD_BRAKE`/`HARD_LONG_G`) are unchanged.

### Speed limits / cache — `cloud/SpeedLimits.kt`, `cloud/Tiles.kt`
`MATCH_RADIUS_M=35`, `OVER_TOL_KMH=3` (speeding stat tolerance), `SEV_TOL_KMH=5` (speeding-penalty
forgiveness), `LIMIT_DROP_GRACE_MS=6000` (trailing-max limit window — forgives exit decel + lone misreads),
`CACHE_TTL_MS=30d`, `TILE_DEG=0.02`.
Route coloring tiers (`SpeedTier.kt`): yellow 0–10 km/h over, red ≥10 (`RED_OVER_KMH=10`).
Speeding only counts in scoring when `limitCoverage ≥ 0.4`.

### Road & ride — `MotionFusion.kt`
`POTHOLE_VERT=3.2 m/s²`, `ROUGH_RMS=0.9` (vertical RMS), `STRETCH_WINDOW_MS=1000` (1 s rough-stretch
windows), `HARSH_STOP_JERK=3.0 m/s³`.

### Recording — `RecordingService.kt`, `AutoStop.kt`
`LOCATION_INTERVAL_MS=500` / `FASTEST=250` (**see Open Issue O1 — effectively 1 Hz**), motion ~50 Hz
(`SENSOR_DELAY_GAME`, written ≥20 ms apart), `MAX_SENSOR_RESTARTS=6`, `AUTO_STOP_IDLE_MS=6 min`.
**Too-short discard (Rev AV):** after finalize, a trip below `MIN_TRIP_DISTANCE_M=5.0` **or**
`MIN_TRIP_DURATION_S=10.0` is deleted (not saved) and the user gets a "Trip not recorded" notification +
in-app message (`postNotRecordedNotice`); the service returns to idle without opening a summary.
Auto-stop end time: last sample >`MOVING_MPS` (4 km/h) → first sample ≤`STATIONARY_MPS` (0.7 m/s).

### Scoring — `ui/TripScores.kt`
Safety/Comfort/Pace heuristics. Fused-detector trust ramps on **motion sample rate** (6→20 Hz), NOT
`fusedConfidence`. Peak-G uses `maxHorizGForce` (robust) when fused ran, else p99 `peakGForce`.
**Safety hard-maneuver term (Rev L):** `gpsHardPenalty` (GPS exposure %, ×5/4.5/2) blended by
`fusedTrust` with `fusedHardPenalty = min(28, (motionBrake·7 + motionAccel·3.5 + motionTurn·1.2)/max(2,km))`.
**Safety speeding term (Rev BF):** `min(45, 0.8 × speedingSeverity)` where `speedingSeverity` (stored,
schema v20) = mean over covered time of `max(0, over−5km/h)²` — magnitude-weighted + super-linear, with a
6 s limit-drop/misread grace (see `SpeedLimits.speedingSummary`). Replaced the old magnitude-blind
`speedingPct·0.8 + (maxOver−20)·0.5`. Peak-G uses `maxHorizGForce` (sustained peak, Rev BE) when fused ran.
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

## 7. Test suite (208 tests)

Run: `…\gradlew.bat --init-script '…\relocate-build.gradle' :app:testDebugUnitTest --no-daemon`.
Results: `C:\Users\sinan\cartrip-build-out\app\test-results\testDebugUnitTest\*.xml`.
Note: the Git-Bash `/c/...` path won't glob in Windows Python — count via `grep -c '<testcase'` on the
XMLs, or open one in the editor.

Files: `AutoStopTest` (6), `FusedEventDetectorTest` (11: peak-percentile, corner-veto, ambiguous-steering,
maneuver-peak magnitude), `DisplayEventsTest` (19), `TripAnalyzerTest` (3), `MotionFusionTest` (6),
`GnssQualityTest` (5), `SpeedTierTest` (4), `TilesTest` (4), `TripBucketsTest` (3), `TripLabelerTest` (2),
`GeoAndPolylineTest` (4), `GeoNamerTest` (11: pickName / composeLabel), `FuelEstimatorTest` (5),
`TripScoresTest` (3: fused drives Safety / cap / GPS-fallback), `AutoRecordPolicyTest` (10: trigger /
arm / stop / wireless / Bluetooth gating), `FormatTest` (2), `TripNamingTest` (3, same-name disambiguation),
`TripKindTest` (6: walk vs drive vs zero-speed), `DrivingTimesTest` (2: dayparts / summarize). All
pure-JVM (no Robolectric/instrumented).

Newer suites not in the list above: `DrawdownsTest` (6), `StressScoreTest` (10), `AiInsightsExportTest` (6),
`EventHotspotsTest` (7), `HomeDetectorTest` (8), `MotionRearmDetectorTest` (10), `AutoStartTest` (8),
`GasPriceTest` (4), `SpeedingSummaryTest` (6), `ExportDataTest` (5, header/row lockstep + value mapping),
`ExportRetentionTest` (4), `PlacesTest` (4), `FuelInsightsTest` (7), `BarScaleTest` (8), `StopAndGoTest` (6).
Count is **208** (`grep -rc '@Test'` across `app/src/test`). The per-suite counts above are exact and sum to 208 (verified 2026-06-29).

Gaps: **no Compose/UI tests, no instrumented tests.** Room migrations: **schema export is now on** (v3.27 —
`exportSchema=true` + `app/schemas/.../21.json`), so Room validates migrations at compile time and v21+ is a
captured baseline for future `MigrationTestHelper` tests; retroactive 1→20 step tests aren't feasible (historical
schemas were never captured — validated by real on-device upgrades instead). See §14.1 CP. Network paths
(Overpass, Routes, Sheets, GasPrice fetch, live Places fetch) and the GnssStatus reading are not unit-tested
(verified manually/on-device).

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
- **O6 — `TripLabeler` is GTA-hardcoded. [ADDRESSED, Rev O; home learned Rev BC]** `GeoNamer` now
  reverse-geocodes unnamed trips' start/end (fail-soft, SharedPreferences-cached by quantized coords) into
  `North York -> Scarborough` labels, with the GTA `TripLabeler` as the fallback. **Rev BC** adds
  frequency-based **home detection** (`HomeDetector`: densest endpoint cluster across all trips, radius-
  refined, persisted) so endpoints near home read "Home", and names same-name "loops" by their farthest
  point ("North York -> Scarborough -> back") instead of the uninformative "North York loop". Field-
  validated (lands 18 m from the owner's home; the stale hardcoded "Harrison Garden" was 560 m off — the
  owner confirmed the detected spot). Still open: **work** detection (only home is learned; `TripLabeler`'s
  hardcoded home/work remain the offline-fallback only, used when geocoding fails); a user-settable home.
- **O7 — Field data analyzed (Rev K–L).** Narrated trips 845/847 analyzed vs transcripts: capture
  excellent (~46 Hz motion, clean 1 Hz GPS, GNSS now logged). Fixed the corner→longitudinal
  contamination (Rev K) and the GPS-detector score-blindness by promoting the fused detector into
  Safety (Rev L; the GPS detector reports `hardBrakeCount=0`/`hardCornerCount=0` at 1 Hz —
  `maxBrake` 2.56–2.69 < 3.0, `maxLat` 3.14–3.42 < 3.5). **Refinements still open:** (a) ~~fused event
  **magnitudes under-report**~~ event magnitudes fixed in Rev M (windowed peak); **trip peak-G fixed in
  Rev BE** — `maxHorizGForce` was p99.5 over *all* samples (diluted to ~0.13 g by calm cruising even when
  the trip had a 0.5 g brake); now the **sustained** peak (`sustainedPeak`, highest level held ≥7 samples),
  field-confirmed the old peak read 0.11–0.19 g while real maneuvers hit 0.37–0.51 g. **Old trips need
  Re-analyze** to refresh the stored value. A severity-weighted Safety term is still a possible next step;
  (b) a dedicated
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

> ## ⭐ OWNER BACKLOG (2026-06-28) — read this first
> Future enhancements the owner has asked for. Grouped by area; each is enough to start cold.
>
> **⚠️ STATUS (Rev BY–CN, v3.22):** much of this list is now **DONE** — Tier-1 quick wins, consolidate
> Driving+Events, dynamic replay (walk-only), re-presented Health metrics, walk handling, Drawdowns, Drive
> Stress Score, past-trips compaction + scroll affordance, you-vs-traffic bar sizing, and an AI-insights
> export. See **§13.3 for the per-item done/queued/blocked status** and `ROADMAP_NEW.md` for the owner's
> newest items (POI naming CM — blocked on Places; encrypt+biometric CO; bar-sizing audit for the rest of
> the app). The items below are kept for context; cross-reference §13.3 before starting any of them.
>
> ### UI / UX
> - **Consolidate Driving + Events tabs.** Fold the "All events" tab (currently collapsed-by-default) INTO the
>   Driving tab so events are shown **by default**. Each event in the Driving tab should **hyperlink to the
>   map**, scrolling to + zooming the map on that event (the trip-detail map already supports `focusKey` +
>   `onEventClick=jumpToEvent` → reuse that). Goal: one place, events visible, every event tap → map at the
>   right zoom. Files: `ui/TripDetailScreen.kt` (Driving card + the events list + the map `focusKey` plumbing).
> - **Dynamic replay zoom/pan.** During replay, zoom/pan to suit the moment — zoom **out** on long highway
>   stretches (fast, sparse), zoom **in** for the slow "last mile" so detail shows. Concept; tune from speed.
>   Files: `ui/TripMap.kt` replay camera (currently fixed bounds + a focus/reset key).
> - **Satellite/aerial overlay toggle** on the maps (`MapType.SATELLITE`/`HYBRID` via a small toggle).
>   `MapProperties(mapType=…)` is already how `TripMap`/`TripHeatMap`/`TroubleSpotsMap` set type.
> - **"Auto-record on" toggle on the Home screen** (in addition to the Auto-record screen) for quick access
>   (it costs battery, so the owner wants to flip it easily). Pref: `AutoRecordPrefs.enabled`.
> - **Hide "Load sample data" from the UI** (enough real data now) — keep `SampleData.kt` code, just remove the
>   front-end entry. **Shrink/move the Google-Sheets "Sync" button** (de-emphasize; it doesn't need prominence).
> - **Re-present the removed "Health metrics"** in a better form (Rev BR removed the sparkline grid) — e.g. a
>   compact "this window vs previous" delta strip, or just the 2–3 metrics that matter.
> - **Past-trips map: recency filter.** Add a low-profile multi-toggle above the Past-Trips frozen map —
>   **24 h / 3-day / 7-day / 30-day / all-time** — that filters which trips the map (and ideally the list)
>   shows. **Default 24 h or 7-day so it loads faster** (the all-time map is heavy). Files: `ui/TripListScreen.kt`
>   (the frozen-map preview + the trip list) and the dormant `ui/TripHeatMap.kt` (multi-route map) if that's
>   what's rendered.
> - **Fuel "Spend over time" chart isn't useful → make it spend *rate*.** Replace the cumulative-cost line
>   with the **derivative: $/week over time**, smoothed with a **1–4 week trailing average**. And keep
>   **cost-per-km smoothed** (Rev BN already does a trailing 5-drive moving average once ≥10 drives — make sure
>   it reads as a smooth trend; consider time-based weeks rather than per-drive). Files: `ui/FuelInsights.kt`
>   (`summarize` series — replace `cumulativeCost` with a weekly-rate series) + `ui/InsightsScreen.kt`
>   (`FuelSection` chart).
>
> ### Bugs / polish
> - **You-vs-traffic bar: white edge artifact.** On the trip-detail "You vs traffic" bar, the green vertical
>   line that marks "you" across the Google-estimate bar has **white beside it** — clean it up so it's a crisp
>   single line (drop the white halo/spacer). Files: `ui/TripDetailScreen.kt` (the You-vs-traffic / EtaCompare
>   bar drawing).
>
> ### Analytics / scoring (high owner interest)
> - **"Drawdowns" metric (unnecessary braking).** Detect when speed drops **>50%** while near the limit (esp.
>   highway: cruising ~100 → forced down to ~50 or a stop) — distinct from a normal stop-sign/light stop.
>   Characterize each: time, v0, v1, full-stop?, time-to-next-peak. A strong **traffic-quality** signal
>   (stop-and-go). Needs per-point speed (`analysis_points`), so compute in `TripAnalyzer`/a new analyzer and
>   store aggregates (schema bump) — pairs with re-analyze-all.
> - **Drive Stress Score** (composite "stress cost/load/grade" per trip). Inputs to consider: drawdown count
>   /severity, **time since last at-rest >1 min** (sustained no-break driving), stop-and-go density, hard-event
>   rate, speeding exposure, trip duration vs distance (congestion), time-of-day. Brainstorm + weight; surface
>   per-trip + an Insights trend. (Owner explicitly wants this; drawdowns feed it.)
> - **Novel use of Google APIs for premium value.** Beyond the existing Routes ETA: live/historical **traffic
>   data**, places (the parking helper below), roads/speed — combined into insights users would **pay for**.
>   Think premium tier. (Ties to commercialization below.)
>
> ### External data / Google APIs (owner-evaluated 2026-06-28)
> - **Richer destination tagging via Places API (New) — top pick.** Replace/augment the on-device Geocoder
>   (gives only a neighbourhood, "North York") with the actual POI at each trip endpoint → names become
>   **"Home → Costco"** instead of "North York → North York". Query only the start/end (and loop "via")
>   points; **cache by quantized cell** exactly like `GeoNamer`'s existing geocode cache (extend it). This is
>   also the enabler for **find-my-car** (knowing you're *at* a store) and **destination analytics** (where
>   you go, dwell, frequency) — strong **premium-tier** value. Use the *New* Places API, not legacy.
> - **Roads API snap-to-roads** (2nd) — snap noisy GPS to the road for cleaner replay lines, tidier maps, and
>   better-localized trouble spots. NB: its *speed-limit* feature is separately gated + expensive — keep OSM
>   for limits, use snap-to-roads only.
> - **Routes API (already used) — expand** its traffic data for the drawdowns / drive-stress / you-vs-traffic
>   analytics.
> - **Situational:** Maps **Elevation API** (clean terrain elevation → a "climb"/hilliness metric + fuel
>   refinement + a stress factor; GPS altitude is too noisy); **Geocoding API** (server-side, only if the
>   on-device Geocoder proves flaky — Places New largely supersedes it); **Street View Static API** (a
>   thumbnail of a hotspot/endpoint — premium flavour, low priority).
> - **Skip:** Geolocation (cell/wifi; app is GPS-centric), Route Optimization (multi-stop logistics), Places
>   Aggregate (area stats, not point tagging), Street View **Publish** (uploading 360s).
> - **⚠️ Cost/architecture caveat for ALL of these:** they're **paid, metered** APIs (unlike the current free
>   on-device Geocoder + OSM). Needs billing + a keyed quota; cost scales with users → fold into the premium /
>   commercialization plan; more location data leaves the device (relevant to secure-data). **Aggressive
>   caching is mandatory** (endpoints only, cache by cell).
>
> ### New features
> - **"Find my car" passive helper** (e.g. Costco/big-box). Auto-detect arrival + the **drive→walk
>   transition**, high-precision pin the parking spot, track the user; when they leave range then return,
>   auto-launch **Google Maps walking nav** to the pinned spot + notification/haptic; auto-clear on return.
>   Confirm via car BT / wireless / GPS (multiple options). Builds on the existing motion + auto-record +
>   `CompanionDeviceManager` machinery.
>
> ### Commercialization / hardening (for an app-store version)
> - **Secure the data properly.** Replace the Google-Sheets sync approach with the standard secure pattern for
>   storing a user's data under their Google account; **encrypt at rest** (SQLCipher — see O-list). Plan a
>   path to a shippable, privacy-respecting app-store build.
>
> ### Already-queued (from earlier this session, still open)
> - **User-settable Home/Work** (item 6) — detection works but is a guess when two clusters are near-equal; a
>   "set home/work" control removes the guess. (Home BC + Work BH detection already shipped.)
> - **Safer battery optimization** (item 8) — the naive GPS throttle was reverted (Rev BQ, degraded walks);
>   needs a trigger that can't mistake a slow walk for a park (e.g. charging-gated, or post-auto-stop idle only).
> - **Raise swerve/corner detector thresholds** so weak turns (yaw-gated swerves storing ~0.13 g lateral)
>   aren't recorded at all (vs. the current display-only g-force slider). Affects scoring → re-validate + re-analyze.
>
> ---
>
> **Status update (overnight autonomous session, 2026-06-28, Rev BG–BJ):**
> - **DONE:** speeding-severity **backfill** for old trips (**BG**, on-device verified — 8 trips populated);
>   **work detection** (**BH/BI** — learns a 2nd place beyond the home neighbourhood; validated it resolves
>   to Speakman Dr, so names can read "Home → Work"; item 6 work part done); **cross-trip recurring-event
>   hotspots** (**BJ**, item 9 — `EventHotspots` + Insights "Recurring spots" card; data-validated: recurring
>   pothole at home, recurring turn at the work entrance). All built + unit-tested + pushed + installed
>   (v3.00). Device was intermittently offline overnight (USB flaky).
> - **NEEDS THE OWNER'S EYES (deferred — UI not visually verifiable while asleep):** the **"Recurring spots"
>   Insights card** renders from validated logic but wasn't seen on-screen — eyeball it; possible follow-ups:
>   map pins for hotspots + tap-to-open a representative trip, and reverse-geocode a neighbourhood name for
>   non-home/work spots. **Work labels** ("Home → Work") only persist after opening Past Trips once.
> - **STILL OPEN (left intentionally):** item 7 **walk handling on the trip-detail screen** (UI); item 8
>   **battery optimization** (needs over-time device measurement); **user-settable home/work** (item 6 UI).
>
> **Status update (Rev AS–AV + design notes, 2026-06-26):**
> - Shipped: Codex-review fixes (**AS**), Past-Trips **multi-select / batch delete (AT)**, trip-detail polish
>   (**AU**: date eyebrow + short-trip fuel-chip suppression), **short-trip auto-discard (AV)** — a trip under
>   5 m **or** 10 s is deleted, not saved, with a "Trip not recorded" alert. See `REV_HISTORY.md`.
> - **Two-stage auto-record trigger (PLANNED — owner's idea, and the right design).** Today a Bluetooth/charger
>   trigger immediately *arms a provisional recording* and commits it on motion within 90 s. But the owner's
>   Tucson Bluetooth connects 20–40 s before departure, often while still **walking up** to the car — and
>   motion-confirm accepts accelerometer vibration, so **walking can falsely confirm a trip** (contaminated
>   start, since there's no start-side trim), and a long warm-up (>90 s) makes the provisional discard with no
>   re-arm. Fix: **decouple the signals — Bluetooth presence = ARM (no recording yet); wireless charging =
>   START.** By the time the phone is on the mount the owner is seated and about to drive, so the start is
>   clean, walking never confirms, and the warm-up-timeout gap disappears. Largely supersedes start-side trim
>   on the BT path. Classic-BT trigger (`useBluetooth`) is wired + the Tucson MAC is set correctly; the CDM
>   "Pair car" path can't pair the classic-BT Tucson (it bound the wrong device) — those stale CDM
>   associations were cleared 2026-06-26.
>
> **Status update (Rev AP–AR, 2026-06-26):**
> - **Auto-record charger trigger fixed (Rev AP)** — the decision read a stale/inverted sticky battery
>   state; now trusts the broadcast edge (`AutoRecordPolicy.effectiveCharging`). **Background crash fixed
>   (Rev AQ)** — a background-started `location` FGS needs `ACCESS_BACKGROUND_LOCATION` ("Allow all the
>   time"); the app now declares + requests it, and the FGS start is try/caught so it can't crash.
> - **Recording haptics — DONE (Rev AR).** Distinct vibration cues: light double-tick = auto *armed*,
>   firm buzz = *recording* (manual start or motion-confirmed), two firm buzzes = *stopped/saved*, soft
>   tick = *discarded* (provisional that didn't move). Always-on; a user toggle is a possible follow-up.
> - **Failure-mode matrix** added in §3 (code-verified) — most degraded scenarios are settled in code;
>   only background hands-free start, reboot re-arm, and the haptic feel still want a real drive.
>
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
> - **Auto-record re-arm-on-motion — DONE (Rev BA, v2.91).** While armed-but-not-recording,
>   `AutoRecordWatchService` runs a pure `MotionRearmDetector` on the accelerometer (jerk EMA ≥ 0.40 for
>   ≥ 4 s, 15 s cooldown) and re-arms via `reevaluate("rearm-motion")`, closing the >90 s-then-drive gap.
> - **START-side trip trim — DONE (Rev BB, v2.92).** `AutoStart.retrospectiveStartTime` (mirror of
>   `AutoStop`) trims the parked prefix (warm-up / backing out) on **auto trips only**, before finalize, so
>   the too-short discard also catches an over-trimmed non-drive. New `delete*Before` DAO queries.
> - **Better trip names — DONE (Rev BC, v2.93).** `HomeDetector` learns home from endpoint frequency
>   (endpoints near home read "Home"); same-name "loops" named by their farthest point ("North York ->
>   Scarborough -> back") instead of "North York loop". See item 6 + O6 + memory
>   `home-detection-and-stale-hardcode`.
> - **Still TODO:** **trip-naming follow-ups** — *work* detection + a *user-settable home* (item 6); the
>   **two-stage trigger** (BT = arm, charging = start — owner's design, see status box above) which would
>   also clean the BT-path start; a **haptic on/off toggle** + optionally a CDM "remove pairing" / dedupe
>   button; severe-corner count (item 2); Insights depth (item 4); rough-stretch mapping (item 5);
>   lane-detection offline algorithm. The Rev U receiver design below is **superseded** (history).

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
6. **Trip-naming follow-ups (from Rev BC — see O6 / memory `home-detection-and-stale-hardcode`).**
   - **Work detection.** Rev BC learns only *home* (`HomeDetector` = most-frequent endpoint cluster). Apply
     the same frequency clustering to find the **2nd** frequent cluster as "Work" so commute-ish trips read
     "Home -> Work" / "Work -> Home". The owner's workplace shows as a ~6-endpoint cluster near
     `43.516,-79.671` (Speakman Dr, ~34 km). Guard against a 2nd cluster that's really just a sub-cluster of
     home; require it to be well-separated and frequent. (`TripLabeler`'s hardcoded home/work are stale —
     fallback-only — so don't rely on them.)
   - **User-settable home (robust fix).** Frequency detection is ambiguous when two spots are near-equal
     (Rev BC field case: 19 vs 14 endpoints, 560 m apart — needed the owner to disambiguate). Add a
     **"Set home" control** (Settings / Vehicle screen, or long-press a trip's start/end on the map) that
     persists an explicit home; naming prefers the user-set home over the detected one, and seeds the
     control with the detected home. Same pattern could let the user set Work. Most robust answer; removes
     the guess. Persist alongside the Rev BC `cartrip_home` SharedPreferences.
7. **Walk/non-drive handling depth (from Rev BD).** Rev BD flags walks in the past-trips card (walking icon
   + moving-avg speed instead of driving Safety/Comfort/Pace). Still to consider: the **trip-detail screen**
   for a walk (it still shows the driving hero/scores — mirror the list treatment); whether walks deserve
   **their own metrics** (steps/pace/elevation) vs. just suppressing driving ones; and whether to auto-set
   `userIsDrive=false` so they're consistently excluded everywhere. `TripKind.isLikelyNonDrive` (top speed
   0.1..12 km/h + manual override) is the gate. See memory `walk-non-drive-finding`.
8. **Battery optimization (owner-flagged 2026-06-27: "draining a bit too fast, ok for now").** GPS-on
   exceeds active recording (Rev BD: ~2 h GPS vs ~1 h recorded). No background runaway (the watcher does no
   GPS; BT scanning 0). Levers, in rough priority: (a) **drop GPS to a low rate once stationary** — don't
   need 1 Hz to confirm a continued stop through the ~6 min pre-auto-stop idle window; (b) **discard a
   never-confirmed provisional fast** instead of the 8 s grace + record, to cut per-bounce GPS/FGS spin-up
   (the **charger-bounce** at mount-in fires 2-3 provisionals over ~45 s as contact makes/breaks in 8-13 s
   stretches — handled correctly today, just wasteful; can't be cheaply debounced without delaying real
   auto-starts, so fix it here at the spin-up cost, not with arm-timing); (c) the Rev BA re-arm accelerometer
   watch runs `SENSOR_DELAY_GAME` (50 Hz) while armed-but-not-recording — fine while plugged in, but could
   drop to a slower rate. Coarse Android attribution, so measure before/after. Revisit with O1 (1 Hz cap).
9. **Cross-trip recurring-event hotspots (owner-requested 2026-06-27).** As trips accumulate and routes
   overlap, surface places where the *same* event recurs — a turn taken hard every time, a pothole/rough
   patch on the commute, a regular hard-brake spot. Sketch: load `drive_events` across all trips with their
   map coords (each event maps to a location via nearest `locations`/`analysis_points` by time), **spatially
   cluster** them (reuse a grid like `GeoNamer.cellKey`, or radius clustering like `HomeDetector`), and flag
   a cluster as a hotspot when ≥N events of the same type from ≥M *distinct* trips coincide (distinct-trip
   count guards against one bad drive). Surface in **Insights** ("You brake hard at X on most drives",
   "Recurring rough patch on Y") and/or as map pins. Naturally grows with data — gate the card on having
   enough overlapping trips. Should consume the **Rev BE** display thresholds so it clusters the same events
   the user sees. Sizable feature → its own rev. Pairs with O9 (rough-stretch geometry) for rough-patch
   hotspots, and item 4 (Insights depth / repeated-route comparison, which also needs per-trip coords).

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

## 11. Design deep-dives & build plan (for the §9 backlog)

Turning the owner backlog into actionable specs. These are blueprints + analysis, **not yet built**.

### 11.0 Recommended build sequence (value × effort, dependency-aware)

**Tier 1 — quick wins (low effort, visible, low risk). Do these first.**
- You-vs-traffic "you" line white-edge bug (polish).
- Hide "Load sample data" from the UI; shrink/de-emphasize the Sheets "Sync" button.
- Home-screen "Auto-record on" toggle (the pref already exists — just surface it).
- Past-trips recency filter (24h/3d/7d/30d/all, default short) — also a perf win.
- Fuel "Spend over time" → spend-rate ($/week, trailing avg).
- Satellite/aerial `MapType` toggle.
Each is a self-contained rev; ship + verify individually.

**Tier 2 — foundational data (unlocks the high-value analytics/features).**
- **Places API (New) destination tagging** → unlocks find-my-car + destination analytics + better names. (§11.4)
- **Drawdowns metric** (per-point speed analysis + stored aggregates) → unlocks the stress score. (§11.2)
  Pairs with a schema bump + re-analyze-all.

**Tier 3 — marquee features (build on Tier 2).**
- **Drive Stress Score** (consumes drawdowns + new factors). (§11.1)
- **Find-my-car** passive helper (consumes Places + motion). (§11.3)
- Consolidate Driving+Events tabs (event→map hyperlink); dynamic replay zoom.

**Tier 4 — productization (large, do when the above are proven).**
- Commercialization: secure-at-rest (SQLCipher) + replace Sheets with proper Google-account storage; premium tier (the Google-API analytics are the paid value).

**Dependencies:** drawdowns → stress score · Places → find-my-car + destination analytics · secure-data → commercialization. **Cross-cutting:** anything touching detector thresholds or new per-point aggregates wants a **schema bump + re-analyze-all** (the button exists, Rev BV) and re-validation by **DB-replay** (pull DB, replay the pure logic in Python — the method used all through Rev BB–BX).

### 11.1 Drive Stress Score — design

Goal: a per-trip "how stressful was this drive" score/grade (0–100 or A–F), surfaced on the trip + an Insights trend. Heuristic + calibrated, like `TripScores` (not regressed against ground truth).

Candidate factors (all per-trip, normalized 0–1 then weighted):
- **Drawdown rate** — drawdowns per 10 min (or per km). The core traffic-stress signal (§11.2).
- **Stop-and-go density** — speed accel↔decel cycles per km, or fraction of moving time with frequent sign changes in acceleration. High = creeping traffic.
- **Speed variance** — stdev of speed (or of speed/limit). Smooth cruise = low; lurching = high.
- **No-rest load** — longest continuous driving stretch with no >1-min stop (owner's idea), and/or fraction of the trip in continuous motion. Long unbroken driving = fatigue.
- **Hard-event rate** — brakes+accels+turns per km (already stored).
- **Congestion ratio** — actual duration ÷ free-flow ETA (Routes API gives traffic vs free-flow; being far slower than free-flow = congested = stressful). Distinct from the *traffic-beating* You-vs-traffic metric.
- **Duration & time-of-day** — long trips + night/rush-hour add load (mild weights).

Formula sketch: `stress = Σ wᵢ·fᵢ` → scale to 0–100 (higher = more stressful), or invert to a "calm" grade to match the existing green=good convention. Calibrate weights on real trips (a relaxed highway cruise should score low; a rush-hour stop-and-go crawl high). **Data:** most needs per-point speed (`analysis_points`) → compute in a new analyzer pass, store aggregates (schema bump), re-analyze. Validate by DB-replay across the owner's trips before wiring the UI.

### 11.2 Drawdowns — detection algorithm

Definition (owner): a **forced, unnecessary** speed reduction — cruising near the limit, then having to brake hard/long (>50% speed loss), **not** a normal stop-sign/light/destination stop. The signature traffic-quality event.

Algorithm over the per-point speed track (with matched speed limit where available):
1. Find segments where speed was "cruising" — speed ≥ ~70% of the posted limit (or ≥ ~70 km/h if no limit), sustained a few seconds.
2. Detect a **drop**: speed falls > 50% (or below an absolute floor) within a short window after cruising.
3. **Recovery test** to distinguish from a real stop: the drawdown *recovers* — speed returns toward the prior level within N minutes (you got going again). A destination stop ends the trip; a controlled-intersection stop is brief/expected and often at lower-limit roads.
4. **Characterize each:** `v0` (pre-drop cruise speed), `v1` (trough), `Δv`, decel duration, `fullStop?`, `timeToRecover` (to ~v0), location, road limit.
5. **Edge cases (suppress):** highway exits (legit decel — reuse the Rev BF limit-drop grace idea), merges, toll booths, trip start/end.

Store: `drawdownCount` + a severity aggregate (e.g. Σ Δv² or Σ Δv·duration) + optionally per-drawdown rows (could be a new `EventType` so they map/cluster like other events → also feed trouble-spots). Strong input to the stress score. Validate by DB-replay on highway trips (where "cruise then forced slow" is unambiguous).

### 11.3 Find-my-car — state machine

Passive parking helper (e.g. Costco). State machine:
`IDLE → DRIVING (recording) → ARRIVED (drive→walk transition: sustained speed≈0 + walking activity, or car-BT disconnect/unplug) → PARKED (capture a high-precision pin = centroid of the last stationary GPS cluster; optionally confirm the venue via Places, §11.4) → AWAY (user > radius R from pin) → RETURNING (re-enters R, or heading back) → NAVIGATE (auto-launch Google Maps walking directions to the pin + notification/haptic) → CLEARED (back at car / driving resumes).`

Signals: accelerometer activity (in-vehicle vs walking), GPS speed, car-BT ACL connect/disconnect, charger. Precision: the parking pin should come from the last few stationary fixes before the walk began (not the first post-stop fix). Battery: only active around the arrival/return window (geofence-style), not continuous. Reuses the existing motion pipeline + `AutoRecordWatchService` + `CompanionDeviceManager` + `GeoNamer`/Places. Privacy: a parked-location pin is sensitive — store locally, clear on return.

### 11.4 Places API (New) enrichment — architecture & cost

- New `cloud/Places.kt` (sibling of `GasPrice.kt`/`SpeedLimits.kt`): given lat/lng, a **Nearby Search (rank by distance)** or place-from-location → the top POI (name, type, distance). Fail-soft (null on error/over-budget).
- **Caching is mandatory:** extend the existing `GeoNamer` cell cache (`cartrip_geocache`, ~110 m cells) → store the resolved place name per cell. Only query trip **endpoints** (+ loop "via"); most resolve from cache on refresh.
- **Naming:** prefer the Place name over the neighbourhood when a POI is within ~50 m of the endpoint and confidence is high → "Home → Costco"; else fall back to the current `GeoNamer` neighbourhood, then `TripLabeler`.
- **Cost:** Places Nearby is ~$0.03/call (verify current pricing). With caching, a personal user is pennies/month; at scale it scales with new endpoints → meter it, cap with the per-refresh `Budget` pattern already in `GeoNamer`, and gate premium analytics behind the paid tier.
- **Unlocks:** find-my-car (venue confirmation), destination analytics (where/how often/dwell), richer trip names.

### 11.5 Consolidated tech-debt / risks (for the next agent)

- **GPS is effectively 1 Hz** on this device (O1) — the analysis ceiling; sub-1 Hz would need raw GNSS.
- **`EventHotspots` uses a raw grid** (~55 m cells) → events near a cell boundary can split. `HomeDetector` solved the same with a radius-refinement; apply that to hotspots if boundary-splitting shows up.
- **`confidence` (fused events, 0.4–0.9) is computed but never gates** — an opportunity to weight/filter events (vs the current display-only g-force slider).
- **"Light events" are by design, not relics** — swerves gate on yaw-rate so their stored *lateral* g is low (~0.13 g); brake/accel fire at ≥0.25 g. If the stress/scoring work wants only strong events, **raise the detector thresholds** (a deliberate scoring change → re-validate + re-analyze), don't just filter at display.
- **Battery:** the GPS throttle was reverted (Rev BQ); GPS-on still exceeds active recording. A *safe* optimization needs a trigger that can't mistake a slow walk for a park (charging-gated, or post-auto-stop-idle only).
- **Raw retention is 30 days** → re-analyze-all only rebuilds trips whose raw `locations`/`motions` survive.
- **Test gaps:** pure logic is well-covered; **no instrumented/Compose/Room-migration tests**, and network paths (Overpass, Routes, Places, GasPrice fetch) are verified manually/on-device only.
- **Process:** OneDrive build-relocation workaround is mandatory (§2.1); **bump version before the final assemble** (§1); validate detector/scoring changes by **DB-replay** before shipping.

---

## 12. Commercialization assessment & roadmap (Play Store, for-profit)

Framing (owner advisor, 2026-06-28): treat this as a **scalable driving-intelligence platform**, not "an app" —
design so 1 user and 100k users cost ~the same *per active user*. The good news below: the app already
embodies most of the cost-critical architecture; the gaps are the **commercial/operational/compliance** layer.

### 12.1 Verdict
**Architecturally ~70% ready, and it's the expensive 70%.** The hard, retrofit-costly parts (offline-first
processing, caching, the raw→summary data model, reliability, privacy-by-architecture) are **done**. What's
missing is productization: accounts + billing, Play compliance (esp. background location), battery, polish,
and the premium analytics that justify a subscription. None of those require re-architecting the engine.

### 12.2 Scorecard vs the 14 commercialization principles
| # | Principle | Status | Evidence / gap |
|---|---|---|---|
| 1 | Offline-first | ✅ Strong | All analysis on-device (`TripAnalyzer`, `FusedEventDetector`, `MotionFusion`, `FuelEstimator`, `TripScores`, `GeoNamer` on-device, `EventHotspots`, `HomeDetector`). Cloud only: cached OSM limits, per-trip Routes ETA, daily gas price, optional Sheets. |
| 2 | Cache everything | ✅ Strong | Speed-limit `cached_ways`/`cached_tiles` (30-day TTL), geocode cell cache (`cartrip_geocache`), daily-throttled gas price. Extend to Places/elevation (§11.4). |
| 3 | Design around API cost | ✅ Mostly | No continuous-while-driving calls; ETA + limits batched post-trip + cached. Gap: Routes ETA is 1 paid call/trip (scales w/ trips → cache/gate); Places not yet. |
| 4 | Battery as a feature | ⚠️ Gap | Recording uses GPS + 50 Hz sensors (needed); persistent auto-record FGS; the idle GPS-throttle was **reverted** (Rev BQ). No Android **Activity Recognition**, no adaptive sampling. Owner-flagged "drains too fast." Highest pre-launch eng item. |
| 5 | Unreliable connectivity | ✅ Strong | Fully offline-capable; all network paths fail-soft (record never blocks on network); history is local. |
| 6 | Modular pipeline | ✅ Good | Staged record→analyze→persist→enrich→present with pure, unit-tested components. |
| 7 | Lean cloud | ✅ by default / ⚠️ for product | Currently the only "cloud" is the user's *own* Google Sheet. No server storing GPS. Good — but the Sheets-per-user hack must be replaced by a real account/sub backend for a product. |
| 8 | Subscriptions | ❌ Not built | No Play Billing, no tiers/gating. |
| 9 | Privacy as a selling point | ✅ Arch / ❌ Formal | `allowBackup=false` (Rev W), local-first, no sale. But **no privacy policy, no Play Data Safety form**. |
| 10 | AI cost control | ✅ Foundation | The app already emits compact metrics/events — ideal for sending tiny structured summaries to an LLM (not raw logs). No AI yet. |
| 11 | Plan for scale | ✅ Mostly | Offline-first ⇒ near-zero marginal compute/storage per user; bounded API cost via cache. Sheets approach is the non-scaling piece. |
| 12 | Polish | ⚠️ Partial | Lots of Compose UI + dark mode + charts/maps done; onboarding, empty states, permission rationale, loading states need a pass. |
| 13 | Optimize data model | ✅ Strong | Already: 30-day raw retention (`RAW_SENSOR_RETENTION_MS`) → long-term summaries. Exactly the recommended model; could add user-opt-in extended retention. |
| 14 | Extensible | ✅ Good | Modular pure components + auto-record abstraction → OBD-II / Android Auto / Wear / fleet are additive. |

### 12.3 Play Store compliance readiness
| Area | Status | Notes |
|---|---|---|
| **Performance** | ⚠️ | Trips start fast, UI smooth, schema stable. **Battery** is the weak point (§12.2 #4). |
| **Reliability** | ✅ Strong | Handles GPS loss (`gpsGapCount`), reboot (`BootReceiver`), permission revocation (caught), app updates (migrations 1→21), service-kill (PARTIAL/`APP_RECOVERY`). See the failure-mode matrix (§3). |
| **Privacy & Compliance** | ❌ Gaps | Need: a published **privacy policy** URL; the Play **Data Safety** form; **location-permission rationale** UX (partly there); **subscription policy** compliance. |
| **Business Operations** | ❌ Gaps | Need: Play **Billing** + subscription management; **crash reporting** (Crashlytics/Sentry); analytics; a feedback channel; staged rollout; a bug/feature triage process. |

**⚠️ The single biggest compliance risk — background location.** Auto-record relies on
`ACCESS_BACKGROUND_LOCATION` + a persistent `location` foreground service. Google Play **manually reviews**
background-location use: it requires a *prominent in-app disclosure*, a compelling justification, and often a
**demo video**, and rejects many submissions. Plan: (a) make auto-record/background **opt-in** with an
explicit disclosure screen; (b) ship a **foreground-only mode** (record only while the app/Activity is
visible) as the default-compliant path so the app is approvable even if background review stalls; (c) prepare
the justification + video. Also justify the **FGS types** (`location`, `specialUse` — declared Rev AQ) per
Android 14 requirements.

### 12.4 Cost model & scale
Per-active-user marginal cost is already **low and bounded**: offline processing/storage ≈ free; map APIs are
cached so repeat-route drivers cost ≈ $0, and only new-area driving spends calls. The two linear-with-trips
costs to watch: the **per-trip Routes ETA** and any future **Places** lookups — cache aggressively, batch
post-trip, and gate the heavy/premium analytics behind the paid tier. A real backend (below) adds a small
fixed per-user cost (auth + a few KB of summary sync), not per-drive cost — consistent with the "1 vs 100k"
goal. Conclusion: the architecture supports profitable scale; the work is wiring the commercial layer without
breaking offline-first.

### 12.5 Phased roadmap to a profitable launch
- **Phase 0 — Foundation (DONE):** offline-first, caching, raw→summary data model, reliability, privacy-by-arch.
- **Phase 1 — Pre-launch hardening:** safe **battery** optimization (Activity Recognition–gated sampling, sensors off when parked — the *safe* version of the reverted throttle); **onboarding + empty states + permission rationale**; **crash reporting + analytics**; remove dev-only bits (hide "Load sample data", de-emphasize Sheets sync — already in §9); the §9 Tier-1 quick wins.
- **Phase 2 — Compliance:** **privacy policy** + **Data Safety** form; **background-location** disclosure + foreground-only fallback (§12.3); FGS justification; Play Console listing/assets.
- **Phase 3 — Monetization infra:** real **account system** + **subscription/backup backend** (e.g. Firebase Auth + Firestore for account/subscription-status/opt-in summary backup, or a lean serverless API) **replacing** the Google-Sheets sync; **Play Billing** subscriptions; free vs premium **gating**.
- **Phase 4 — Premium value (the hook):** the **driving-intelligence dashboard** — drawdowns (§11.2), **drive-stress score** (§11.1), Places **destination analytics** (§11.4), cohort/vehicle **comparisons** (needs backend aggregates), **AI coaching** from compact summaries (§ principle 10), long-term trends, exports.
- **Phase 5 — Launch ops:** staged rollout, feedback loop, crash triage, iterate.

### 12.6 Positioning & premium mapping
Niche: not navigation / insurance / dashcam, but a **personal driving-intelligence platform**. The example
premium insights map onto existing or near-term work:
- "3 intersections = 42% of your hard braking" → **trouble-spots** (already built).
- "You waste ~$23/month accelerating harder than necessary" → **drawdowns** + fuel model (§11.2 + `FuelEstimator`).
- "Leave 12 min earlier and your commute is faster" → Routes traffic-by-time + home/work + departure patterns.
- "Top 15% fuel economy for your vehicle" / "31 kg CO₂ saved" → **cohort aggregates** (needs the backend) + fuel model.
- "18% smoother over 3 months" → score trends (already have score history).
Suggested free vs premium split is in the advisor notes (free = recording + basic score + heatmap + 30-day
history; premium $3.99–4.99/mo = AI coaching, lifetime history, trends, comparisons, fuel analytics, exports,
cloud backup). Principle: premium = **deeper insight**, never gating basic recording.

---

## 13. Session log Rev BY–CN (2026-06-28) + lessons for the next agent

This session executed a **comprehensive batch revision plan** against the §9 backlog. Everything below is
built, unit-tested, installed on the S25, committed, and **pushed** to `origin/main` (`ccaff69`).

### 13.1 What shipped (file map)
| Rev | Version | Area | Key files |
|---|---|---|---|
| BY–CD | 3.15 | Tier-1 UI quick wins | `ui/TripDetailScreen.kt` (EtaCompare you-line), `ui/HomeScreen.kt` (Options sheet + auto-record quick toggle), `ui/TripListScreen.kt` (recency filter), `ui/FuelInsights.kt` (weekly spend-rate), `ui/MapControls.kt` (new, satellite toggle) + `ui/UiPrefs.kt` |
| CE–CH | 3.17 | Consolidation & replay | `ui/TripDetailScreen.kt` (events-by-default, walk hero, replay wiring), `ui/TripMap.kt` (replay follow), `ui/InsightsScreen.kt` (delta strip) |
| (CF fix) | 3.18 | Replay follow → walk-only | `ui/TripDetailScreen.kt`, `ui/TripMap.kt` |
| CI | 3.19 | **Drawdowns** (schema v21) | `analysis/Drawdowns.kt` (new), `data/Entities.kt`, `data/AppDatabase.kt`, `data/TripFinalizer.kt` |
| CJ | 3.20 | **Drive Stress Score** | `ui/StressScore.kt` (new), surfaced in `ui/TripDetailScreen.kt` + `ui/InsightsScreen.kt` |
| CK–CN | 3.22 | Filter compaction + scrollbar, bar sizing, **AI export** | `ui/TripListScreen.kt`, `ui/TripDetailScreen.kt`, `ui/AiInsightsExport.kt` (new), `ui/InsightsScreen.kt` |

New tests: `DrawdownsTest`, `StressScoreTest`, `AiInsightsExportTest`; `FuelInsightsTest` updated for the
weekly-spend series. All green.

### 13.2 Lessons learned this session (hard-won — read before you build)
- **The build pipe hides Gradle failures.** `gradlew ... | tail` returns `tail`'s exit code (0) even when the
  build FAILED. **Never trust the exit-code/notification — grep the output for `BUILD SUCCESSFUL`/`BUILD
  FAILED`.** (Caught a `const val` forward-reference error that the "exit 0" notification masked.)
- **`const val` cannot forward-reference another `const val`** declared later in the same object — Kotlin
  evaluates them in source order (`WEEK_MS = 7 * DAY_MS` failed because `DAY_MS` was below it). Inline the
  literal or order them.
- **DB-replay is the right way to validate a new detector/score before shipping** and it cross-checks the
  Kotlin: porting `Drawdowns` to Python and running it on the pulled DB gave counts that **matched the
  on-device finalizer exactly** after re-analyze (trip 1180 = 17/83771). Always: pull WAL-inclusive DB →
  replay in Python → **delete the DB** (personal location data) → ship.
- **`StressScore`/`Drawdowns` are dormant on the owner's current data in some views** — e.g. the Insights
  "vs previous window" strip and the weekly spend-rate need ≥2 periods/weeks, and all 42 trips are within ~1
  week / 435 km. The logic is correct; it "grows with data." Don't mistake an empty card for a bug.
- **The trip-detail map swallows vertical drags** (page scroll is paused while a finger is on the map —
  `mapTouched`). To script-scroll the page, drag on a **non-map** region (hero/cards). Tapping trip rows is
  fiddly because selecting a row **adds a detail line and shifts the list** — the "open" tap then misses.
- **Respect the owner's live phone.** Mid-session the screen showed the owner's **personal Messages**; a
  polling screenshot loop captured it. Lesson: before any `screencap`, check the foreground app
  (`dumpsys window | grep mCurrentFocus`); if it's not our app/launcher, **do not screenshot**, delete any
  that captured personal content, and defer device verification. Install silently (`-r`) without launching.
- **The map-marker filter chips are NOT the event list filter.** `visibleEvents` (chip-gated) is for map
  markers only; the Driving "All events" list must use the full `shownEvents` (fixed in CE).
- **Replay camera UX:** following + speed-zoom reads poorly on long drives but well on walks — gate camera
  behaviour on `TripKind.isLikelyNonDrive`. For smooth following use a per-frame `camera.move`
  (`withFrameNanos`), not a sampled `animate` loop (which stutters: animate-duration + delay per step).
- **Mojibake trap still applies** (§1): keep new source ASCII; the UI delta arrows were done as `+N`/`-N`
  text, not Unicode triangles.

### 13.3 Detailed backlog status (every item — done / queued / blocked)
**DONE this session** (was in §9 / §11): you-vs-traffic white-edge bug; hide sample-data + de-emphasize
Sheets; Home auto-record toggle; past-trips recency filter; fuel spend-rate; satellite map toggle;
consolidate Driving+Events (events default + event→map); dynamic replay (walk-only); re-presented "Health
metrics" as the delta strip; walk trip-detail handling; **Drawdowns** (§11.2); **Drive Stress Score**
(§11.1, first cut); past-trips compaction + **scroll affordance**; you-vs-traffic **bar sizing**; **AI
export** (novel — principle 10).

**QUEUED / improved next steps:**
- **Bar-sizing audit (rest of the app).** CL fixed you-vs-traffic (nice axis, ~80% fill, minute scale).
  Apply the same judgment to `SpeedingShareBar`, `PeakLimitBar` (`ui/TripDetailScreen.kt`), the Insights
  `TimeSeriesChart`/`DivergingBarChart` (`ui/Charts.kt`), and `DurationBar` (`ui/TripListScreen.kt`): pick a
  "nice" max with headroom, enforce a **minimum** visible bar, label the scale, avoid edge-to-edge and
  near-zero degenerate lengths.
- **CM — POI endpoint naming.** See §13.4. **Blocked on the owner's Places decision.** The free on-device
  `Geocoder` (`ui/GeoNamer.kt`) rarely returns business names; a confidence-gated `featureName` best-effort
  is possible but won't reliably catch "IKEA/Tim Hortons." Places API (New) is the reliable path.
- **CO — encrypt-at-rest + biometric.** Highest-value privacy item, highest-risk. Plan: **SQLCipher** for
  the Room DB (key in the Android **Keystore**, unlocked via **BiometricPrompt**), a one-time migration of
  the existing plaintext `cartrip.db` to an encrypted copy (export→re-import or `sqlcipher_export`), and a
  graceful fallback (device without biometrics → device-credential). Its own focused, carefully-verified rev;
  pairs with the commercialization "secure the data" item. **Do not rush at the end of a session.**
- **Drive Stress Score v2.** First cut is calibrated on the owner only. Refinements: add **speed-variance**
  and **no-rest-load** factors (need per-point data → either a schema aggregate like drawdowns, or compute in
  a new analyzer pass), an Insights **trend** (not just the average card), and per-user re-calibration as data
  grows. Re-validate by DB-replay.
- **Drawdowns v2.** Use the **speed limit** when present (cruise = ≥70% of limit) to better separate forced
  slowdowns from normal arterial light-stops; consider a `DRAWDOWN` `EventType` so they map/cluster in
  trouble-spots. Needs re-analyze.
- **Still open from earlier §9:** safe battery optimization (Activity-Recognition-gated; the naive throttle
  was reverted Rev BQ); two-stage auto-record trigger (BT=arm, charging=start); user-settable Home/Work;
  find-my-car (needs a drive test + Places for venue confirmation); rough-stretch mapping (O9); lane-detection
  offline algorithm.

### 13.4 Places API (New) — cost analysis for this owner (CM enabler)
**Why CM needs it:** the free on-device `Geocoder` returns neighbourhoods/streets, not reliable business
names. **Places API (New) "Nearby Search" (rank-by-distance)** at each trip endpoint returns the actual POI
("IKEA", "Tim Hortons") with types + distance → enables "Home → IKEA → Home", find-my-car venue confirmation,
and destination analytics.

**Cost estimate for a user like the owner (~6 drives/day ≈ ~180 trips/month):**
- Worst case = 2 endpoint lookups/trip = **~360 calls/month**.
- **With mandatory caching** (extend `GeoNamer`'s ~110 m cell cache to store the resolved place per cell):
  home/work/regular stores resolve from cache after the first visit, so only **new/rare destinations** cost a
  call — realistically **~40–100 calls/month**.
- Nearby Search (New), "Pro" field tier (name/location/types) ≈ **~$0.032/call** → **~$1.30–$3.20/month** at
  list price for this volume.
- **Google's free monthly per-SKU allowance** (the universal $200/mo credit was replaced in 2025 by per-SKU
  free tiers) almost certainly covers a single personal user → **effectively $0/month** in practice.
- **Scaling caveat:** cost grows ~linearly with *new* endpoints across the user base. Keep caching aggressive,
  query **endpoints only** (+ loop "via" points), cap per-refresh with the existing `Budget` pattern, and
  **gate destination analytics behind the premium tier**. (⚠️ Verify current pricing + free caps in the Google
  Cloud console — Maps pricing changes; treat the numbers above as an order-of-magnitude.)
- **Setup when the owner is ready:** enable *Places API (New)* + billing on the Cloud project, restrict the
  key (Android app + API restriction), add it to `local.properties` (gitignored, like `MAPS_API_KEY`), then a
  new `cloud/Places.kt` (sibling of `GasPrice.kt`) + extend the `GeoNamer` cell cache (§11.4).

### 13.5 Commercialization & Google Play launch roadmap (premium-tier plan)
Builds on §12. Goal: a **paid, scalable driving-intelligence app** on Play with a free tier + subscription.
The engine is ~70% there (§12.1); the work is the commercial/compliance/polish layer.

**Phase 1 — Pre-launch hardening (engineering):**
- **Battery (highest priority, §12.2 #4):** Activity-Recognition-gated sampling, sensors off when parked, the
  *safe* version of the reverted idle GPS-throttle. Owner-flagged "drains too fast."
- **Encrypt-at-rest + biometric (CO, §13.3)** — privacy is the headline selling point; ship it before launch.
- Onboarding, empty states, permission rationale, loading states; crash reporting (Crashlytics/Sentry) +
  lightweight analytics; finish the bar-sizing/polish audit.
- **AI insights (CN) → premium "AI coaching"**: the share-export already produces the compact summary; a
  premium tier can call an LLM directly (send the same tiny structured summary, never raw logs — principle 10).

**Phase 2 — Compliance (the gating risk):**
- **Background location** is the #1 Play rejection risk (§12.3): ship a **foreground-only default** mode so
  the app is approvable, make auto-record/background **opt-in with a prominent disclosure screen**, prepare
  the justification + demo video; justify the FGS types (`location`, `specialUse`).
- Published **privacy policy** URL; Play **Data Safety** form; subscription-policy compliance.

**Phase 3 — Monetization infra:**
- Replace the **Google-Sheets sync hack** with a real **account + backend** (e.g. Firebase Auth + Firestore,
  or a lean serverless API) storing **only compact summaries** (not raw GPS) for cross-device + cohort
  features. **Play Billing** subscriptions; free-vs-premium **gating**.

**Phase 4 — Premium value (the hook):** the driving-intelligence dashboard — Drawdowns + **Drive Stress
Score** trends (now built), Places **destination analytics** (needs CM), **cohort/vehicle comparisons** (needs
backend aggregates), **AI coaching** from the CN summary, lifetime history, exports.

**Suggested tiers (from advisor notes, §12.6):** *Free* = recording + basic Safety/Comfort/Pace + heatmap +
30-day history. *Premium ($3.99–4.99/mo)* = AI coaching, lifetime history, score/stress trends, comparisons,
fuel + destination analytics, exports, cloud backup. **Principle: premium = deeper insight; never gate basic
recording.** Per-active-user marginal cost stays low/bounded because processing is offline and map/Places
calls are cached + endpoint-only (§12.4).

---

## 14. Rev CO/CP — second-reviewer pass (2026-06-28) & current plan

A second reviewer (Codex, read-only) audited the post-Batch-4 repo. Each finding was **re-verified against
source** before acting (some Codex notes were stale or already-handled). Verdicts:

| # | Finding | Verdict | Source |
|---|---|---|---|
| 1 | Docs stale (Rev AT / 3.14 / schema v20; README mapping stack) | **TRUE** — fixed in §1/§3/§7/§12.3 + README this pass | header vs body drift |
| 2 | Sample cleanup misses GNSS rows | **TRUE but latent** (samples insert no GNSS today) — fixed defensively | `TripDao.deleteSample*`, `SampleData` |
| 3 | Export schema behind analytics | **TRUE** — refreshed (appended ETA/severity/drawdowns/stress/GNSS/labels) | `ExportData.kt` |
| 4 | Quick-toggle bypasses setup/disclosure | **TRUE** — now gates on background-location → routes to disclosure | `ui/HomeScreen.kt` |
| 5 | Test coverage too narrow | **TRUE** — no migration/Compose/instrumented tests (see Rev CP) | `app/build.gradle.kts` |
| 6 | AI export empty labels; fuel "week" wording | **TRUE** — AI export now passes real labels; fuel wording corrected (logic is deliberately rolling-7-day, not calendar) | `InsightsScreen.kt`, `FuelInsights.kt` |
| 7 | Commercialization/policy risks | **AGREE** (bg-location, Places pricing, export privacy); **User-Agent already set** (challenge); OSM attribution weak | `SpeedLimits.kt:288`, `GuideScreen.kt` |

**Rev CO — shipped this pass (docs + low-risk correctness, 176 tests green):**
- Doc-truth pass (§1/§3/§7/§12.3 + README + `ROADMAP_NEW.md` frontier).
- Sample GNSS cleanup: `deleteSampleGnssSamples`/`deleteSampleGnssMeasurements` + call site (defensive).
- AI export labels: `InsightsScreen` "Share for AI insights" now resolves geocoded labels before building.
- Quick-toggle gate: enabling hands-free from Home with no background-location routes to `AutoRecordScreen`
  (disclosure + permission) instead of silently starting the watcher; resume-refresh keeps it in sync.
- Export schema refresh: `ExportData.SUMMARY_HEADER`/`summaryRow` append ETA, speedingSeverity, rough-stretch/
  bumpy, drawdowns, **Stress Score**, moving-min, GNSS quality, name, drive/walk. **Appended at the end** so
  existing Google Sheets columns don't shift (old rows show blanks for new cols — expected).
- Fuel-week wording corrected (kept the deterministic rolling-7-day bucketing — calendar weeks would add
  timezone/locale fragility for no user gain).

**Still open — Rev CP/CQ/CR (priority order):**
- **CP (P1):** Room **migration tests** — **FOUNDATION DONE (v3.27):** `exportSchema=true` +
  `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` now capture the schema as JSON
  (`app/schemas/com.cartrip.analyzer.data.AppDatabase/21.json` committed; Room also validates migrations
  against it at compile time). ⚠️ **Retroactive 1→20 step tests aren't feasible** — historical schemas were
  never captured (exportSchema was false), and reconstructing 20 versions by hand is error-prone; those
  migrations were instead validated by **real on-device app upgrades** (failure-mode matrix §3). **Next
  migration (v22):** add `androidTestImplementation("androidx.room:room-testing:2.6.1")` + a `testInstrumentationRunner`
  + a `MigrationTestHelper` androidTest for v21→v22 (both schemas will then exist) — needs an emulator/device
  run. **Done in v3.28/build 139 (Rev CP cont.):** Trip Detail **"Reset to automatic"** for the drive/walk
  override (clears `userIsDrive` back to null; only shown when a manual override exists), and a visible
  **OSM "© OpenStreetMap contributors (ODbL)"** attribution credit in the "How it works" guide (a new
  "Credits & data sources" card; the © glyph is built via `0xA9.toChar()` so the `.kt` source stays ASCII —
  mojibake trap). **Bar-sizing audit — shared scale shipped (v3.29/build 140):** `ui/BarScale.kt` (pure,
  unit-tested) — `niceAxisMax(dataMax, headroom)` + `fillFraction(value, axisMax, minVisible)`; the
  you-vs-traffic ETA axis now delegates to it (behaviour-identical) and the Past-Trips `DurationBar` sizes
  against it (no longer edge-to-edge, keeps a min sliver). `SpeedingShareBar` (a 0–100% proportion) and
  `PeakLimitBar` (already carries headroom) were intentionally left. **AI-export enrichment (v3.30/v3.31):**
  added a **Traffic** section (you-vs-Google + congestion vs free-flow) and a **"When you drive"** daypart
  section to the "Share for AI insights" markdown, plus export value-mapping regression tests. **Past Trips
  open affordance — by design a two-tap; corrected 2026-06-29.** The interaction is intentional
  (`TripListScreen` line ~80 + the row `onClick`): the **first tap selects + previews the route on the frozen
  map; a second tap on the *same* trip opens the detail.** This was **verified working on-device** (2026-06-29
  — first tap selected "North York → Work" and showed the −5-min-vs-Google preview, second tap opened the
  detail). An earlier note here called it "broken / confirmed problematic" — that was a **scripted-test
  error** (my taps hit different trips/positions and the map, never two in a row on one trip); corrected. The
  smaller, real wart (per §13.2) is that the first tap inserts a preview line that **shifts the row**, so the
  second tap is easy to miss for a human too. **Improvement (worthwhile, lower priority):** make opening more
  discoverable/robust — an explicit Open chevron/button on the selected row, or a hit-target that survives the
  preview-line shift.
- **CQ (P2, gated on owner's Places go/no-go):** **SCAFFOLD DONE (v3.27, inert):** `cloud/Places.kt`
  (Nearby Search New: pure `topPlaceName` parser + `cellKey` cache key, both unit-tested; `nearbyName` fetch
  with `X-Goog-FieldMask=places.displayName`, ~60 m radius, rank-by-distance; `placeNameCached` caches by
  ~110 m cell incl. misses) + `cloud/PlacesPrefs` flag **defaulting OFF**. Reuses `RoutesConfig` for the key +
  X-Android headers. **Not wired into the live naming path and no UI toggle yet** (deliberate — it's a paid,
  metered API; flag-off = zero cost + zero behaviour change). **To activate (the real CQ rev):** owner enables
  "Places API (New)" + billing on the Maps key; then add the UI toggle + call `Places.placeNameCached` in
  `TripViewModel.loadTripLabels`/`GeoNamer` to prefer the POI over the neighbourhood, Geocoder as fallback.
  NB: a real `org.json` was added to `testImplementation` so JSON parsers are unit-testable (Android's is stubbed).
- **CR (pre-launch, P1):** commercialization hardening — privacy policy, Data Safety inventory, ~~export
  file retention/disclosure~~ **✅ DONE (v3.27/build 138):** `export/ExportRetention` (pure: age 30d + count
  cap 50, unit-tested) auto-prunes the unencrypted per-trip `.xlsx` files on each export (`TripExcel`), plus
  an Options-sheet "Clear exported trip files" action + disclosure line. Still open: background-location
  review package + foreground-only default, API-key restrictions, billing guardrails, and **CO encrypt-at-
  rest + biometric** (the SQLCipher item, distinct from this "Rev CO" review pass — naming collision noted).

### 14.1 Second re-review of Rev CO (2026-06-28) — verdicts + follow-ups

A re-review (Codex) of the committed Rev CO. Verified each against source:

| # | Finding | Verdict | Action |
|---|---|---|---|
| 1 | System-guide `.docx` is stale (2.86/build 97/Rev AV) | **TRUE** | Marked non-authoritative (banner + `ARCHIVE` rename) — not regenerated |
| 2 | HANDOFF says CO "uncommitted"; 173-vs-176 test count | **TRUE** | Fixed (line 3 → pushed `041ca86`; §14 → 176) |
| 3 | `AutoRecordScreen` lets you enable before bg-location granted | **TRUE, by design** | **Decision: don't hard-gate** — foreground-only is the Play-compliant default (§12.3). Added a state-aware "Foreground-only until…" subtitle so the limited state is explicit |
| 4 | AI export `loadTripLabels(completed)` loads whole history but only 25 are printed | **PARTLY** | **Kept the full load on purpose** — it also persists learned Home/Work, so narrowing it would degrade detection; it's budget-capped + cached. Added a `sharing` guard so repeat taps can't launch duplicate jobs/share sheets |
| 5 | Export `TripName` writes only `trip.name` (blank for unnamed) | **TRUE** | Renamed header → **`UserTripName`**; documented that the generated "A→B" label is intentionally not in the pure builder |
| 6 | `ExportData` imports `ui.StressScore` (export→UI coupling) | **TRUE** | **✅ DONE (v3.26/build 137):** `StressScore` + `TripKind` (both pure) moved to `analysis/`; `color()` split into `ui/StressColors`; `ExportData` now imports `analysis.StressScore` — export→ui dependency removed |

**Added to Rev CP (P2 unless noted):**
- ~~**Decouple `StressScore`**~~ **✅ DONE (v3.26/build 137).** `analysis/StressScore.kt` (pure model +
  `from`/`band`/`kmWeightedAvg`/`series`/`trailingAvg`) + `analysis/TripKind.kt` (it had to move too — the
  stress score depends on it and it's pure domain); `color()` is now `ui/StressColors`. All callers
  (`ExportData`, `TripDetailScreen`, `InsightsScreen`, `AiInsightsExport`, `FuelInsights`, `TripListScreen`,
  `TripViewModel`, `TripScores`) re-imported; tests moved to the `analysis` test package. 179 tests green.
- **`GeneratedTripLabel` export column** (optional) — a label-aware export path that includes the geocoded
  "A→B" name, not just the user rename. Needs IO/Geocoder at export time, so it's a deliberate design change
  (the current builder is pure/sync); weigh against keeping export pure.
- **⭐ Drive Stress Score depth (HIGH owner interest) — ✅ SHIPPED v3.24/build 135 (Rev CP).** `StressHeroPill`
  in the trip-detail **hero** (replaced the old compact line); `StressScore.kmWeightedAvg` (each trip's 0..100
  score **distance-weighted** — a km-weighted average on the band scale, NOT a per-km burden rate);
  `StressScore.series`/`trailingAvg` (pure, tested) feeding an Insights **`StressTrendCard`** — km-weighted
  headline + a trailing-average-smoothed `TimeSeriesChart` (its dashed reference = the mean of the plotted
  smoothed series) + a delta vs the previous window (replaced the single-average `StressSummaryRow`). No schema
  change. Spec in **`ROADMAP_NEW.md` → "Drive Stress Score — depth"**. The `StressScore` **decouple is now
  ✅ DONE** (v3.26 — see finding #6 above). **Still open:** EMA-vs-trailing tuning + per-user re-calibration,
  and an optional true per-distance metric.

---

_Questions a fresh agent should be able to answer from this doc: how to build (§2.1), where the APK
lands (§2.1), how to read the real DB (§2.4), what every detector threshold means (§6), what's broken
or capped (§8), what to do next + the owner backlog (§9), how to build the marquee features (§11), the
commercialization/Play-Store roadmap (§12), and **what shipped + the hard-won lessons of the latest
session, the detailed backlog status, the Places cost analysis, and the launch roadmap (§13)**.
Also see `ROADMAP_NEW.md` for the owner's newest item list with per-item assessment._

_The Driving-Health **score taxonomy** + the science-backed **Style / Demand / Efficiency** metric model
(why Safety/Comfort/Pace/Stress/Fuel collapse to ~2 driver axes + 1 outcome, the evidence-based metric menu,
the "don't average style with demand" composite guardrail, and the ACWR-ratio caveat) live in
`ADVISORY_ASSESSMENT.md` §1 / §1.1, with a direction entry in `ROADMAP_NEW.md` → "Metric consolidation."
The **scoring source-of-truth** (full product + UI + phased-engineering spec, incl. the `Rev CX` UI-only
first-rev ticket) is `DRIVING_INTELLIGENCE_SCORING.md`. Guardrail for any future agent: separate driver
**style** from road **demand**, keep fuel/cost as an **outcome**, and do NOT build a raw composite that
blends all three without preserving that explanation._
