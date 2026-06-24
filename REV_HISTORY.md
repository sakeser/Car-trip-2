# Revision History and Handoff

This file is the working handoff for the main branch. The UX redesign worktree was intentionally not used for the June 23, 2026 field-test fixes.

For the full Claude Code continuation brief, including UX worktree notes, GNSS/raw-measurement findings, and a prioritized next-step backlog, see `CLAUDE_CODE_HANDOFF.md`.

## Current phone build

- Package: `com.cartrip.analyzer`
- Installed on S25: `versionName=2.51`, `versionCode=62`
- Build artifact (relocated, see note): `C:\Users\sinan\cartrip-build-out\app\outputs\apk\debug\app-debug.apk`
- Maps key: now present in `cartrip-main\local.properties` (gitignored), copied from the original worktree; do not commit or print it.

### Build note: OneDrive lock workaround

OneDrive holds handles on `app/build` (kotlin-classes / snapshot), which breaks incremental
Kotlin compile with `Unable to delete directory`. Builds now relocate output out of OneDrive via an
init script:

```powershell
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' :app:assembleDebug --no-daemon
```

The APK then lands under `C:\Users\sinan\cartrip-build-out\app\outputs\...`.

## Rev N (v2.51): fuel & trip-cost feature

Estimates fuel use and $ cost per trip from stored aggregates (distance, avg moving speed, idle) — no
schema change. New pure `FuelEstimator` (analysis): a speed-dependent L/100km curve anchored on the
vehicle's city/highway ratings (U-shaped: worst crawling, best ~35–95 km/h, mild aero penalty past 110)
+ idle burn, all scaled by a `calibration` factor. Seeded for a **2023 Hyundai Tucson 2.5L AWD**
(~10.2 city / 8.4 hwy L/100km) at a **GTA regular price ~$1.84/L** (June 2026: McTeague ~178¢, StatCan
~189¢); everything user-editable.

- `VehiclePrefs` (SharedPreferences) stores the single active vehicle profile.
- `VehicleScreen` (Home → fuel icon): edit year/make/model, city/hwy economy, fuel price, and a
  calibration factor — incl. a "calibrate from your car's reported combined L/100km" helper.
- Trip detail gains a **Fuel & cost** card (litres, $, effective L/100km); Insights gains **Fuel / trip**
  and **Cost / trip** mini-stats with a window total.
- **Calibration loop:** the car reports actual distance / fuel / L-100km per trip; feeding those back
  (edit the ratings or the factor, or via the helper) converges the estimate. 5 `FuelEstimatorTest`
  added (57 total). On-device UI not yet screenshot-verified (phone was locked).

## Rev M (v2.50): fused event magnitude = maneuver peak

Fused events fired (and stored their magnitude) at the *first* sample that crossed the threshold, but
a hard maneuver keeps building afterward — a narrated 0.5 g brake was stored as 0.28 g (~2× under). The
stored magnitude is now the peak over a 1500 ms forward window (`PEAK_WINDOW_MS`); the longitudinal peak
scan ignores samples that are vertical-bump- or turn-dominated so a following pothole/corner can't
inflate it. Event counts and the percentile `maxHorizGForce` are unchanged — this only fixes the
per-event severity shown on map markers, the replay, the export sheet, and (next) the Safety term.
One regression test added (`eventMagnitudeReflectsManeuverPeakNotFirstCrossing`); 52 tests total.

## Rev L (v2.49): promoted the fused detector into the Safety score

The 1 Hz GPS detector that drove Safety is blind to hard braking/cornering — trips 845/847 (and most
of the trip history) reported `hardBrakeCount=0`/`hardCornerCount=0` despite many narrated hard events,
because the Kalman + lateral low-pass wash brief ~0.3 g events below the 3.0 / 3.5 m/s² thresholds. So
**Safety was pinned at 98–100 regardless of how hard you drove** (only speeding or a peak-G outlier
moved it).

- Safety's hard-maneuver term is now `gpsHardPenalty` (GPS exposure %) **blended by motion-Hz trust
  with** `fusedHardPenalty = min(28, (motionBrake·7 + motionAccel·3.5 + motionTurn·1.2) / max(2 km, dist))`.
  On dense-motion trips (trust → 1) the corner-corrected sensor detector drives the term; old/low-rate
  trips still use GPS exposure unchanged. Turns are weighted low (firm city corners aren't unsafe) and
  the term is capped so one rough stretch can't zero the score.
- Field-calibrated on the trip set: calm long drives stay 97–98, the deliberately-aggressive test drives
  drop to ~92–95, and a dense-hard benchmark to ~89–90. Three `TripScoresTest` added (51 tests total).
- Existing trips show the new score after **Re-analyze** (re-runs the detector + recomputes counts/scores).

Open refinements: fused event magnitudes under-report (fire at first threshold crossing, not the peak),
so a severity-weighted term would be a better long-term Safety signal than a raw count rate; and a
dedicated severe-corner count (≥~0.45 g) would let hard cornering register in Safety without penalizing
normal turns.

## Rev K (v2.48): field-data calibration — corners no longer logged as linear accel

Analyzed two narrated field drives (`Recording 34.txt`→trip 845; `Recording 35.txt`→trip 847) pulled
off the S25, aligned to the transcripts. Capture was excellent (motion ~46 Hz, clean 1 Hz GPS/0 gaps,
GNSS now logged). Confirmed the owner's hunch — **fast corners were being scored as large linear
accelerations**:

- Trip 847 logged its single hardest "ACCEL" (0.47 g, conf 0.90) 1.4 s after a CORNER during the
  narrated "curve / hard turn"; both narrated swerves on each trip also fabricated phantom ACCEL/BRAKE.
- Root cause in `FusedEventDetector`: the longitudinal turn-veto compared the **instantaneous**
  centripetal estimate (`speed × gyro-yaw`) to the horizontal-accel magnitude, but those two peak on
  different samples within a corner, and corner/longitudinal have independent debounces.
- **Fix:** the veto now uses a **windowed** centripetal max (`CORNER_VETO_MS = 450`); and an
  ambiguous-GPS-slope spike during clear rotation (`AMB_TURN_YAW = 0.20` rad/s or
  `AMB_TURN_LAT_G = 0.15` g, windowed) is treated as steering instead of being guessed as brake/accel.
- Validated offline against the exact stored raw data: removes precisely the 6 corner/swerve-contaminated
  longitudinals across both trips while keeping every genuine narrated brake/accel. Two regression tests
  added (`cornerForceNotMiscountedAsAcceleration`, `swerveWithAmbiguousSlopeIsNotALongitudinal`); 48 tests green.

Still open from this field test (see HANDOFF §8 O7): the **GPS detector that drives the score** reports
0 hard brakes / 0 hard corners on both trips despite many narrated hard events (1 Hz Kalman + lateral
low-pass wash transients under the 3.0 / 3.5 m/s² thresholds). Decision pending — lower GPS thresholds
vs. promote the fused detector to scored.

## Rev J (v2.42–v2.43): detection fixes + graphical UX — branch `rev-g-functional`

- **J1 (v2.42) — event detection:** fixed the clustering bug where distinct slow-drive events
  10–30 s apart merged into one (3 swerves + a brake across 31 s). `DisplayEvents` now clusters by
  time only (4 s window, hard 8 s span cap); the detail card's grouped-signals list is ±6 s
  (no spatial chaining). **Speed-aware turn/swerve flagging**: <10 km/h never; 10–30 km/h needs
  ≥0.40g; ≥30 km/h needs ≥0.30g (a 0.26g swerve at 20 km/h is gone). Corner detection 0.27→0.32 g.
  Brake/accel left as-is. **Variable replay autoplay**: max(5 s, tripSeconds/360), capped 30 s.
- **J2 (v2.43) — graphical UX:** landscape (car-mount) shows one full-screen Start/Stop button for
  eyes-free pressing (verified on device); portrait buttons enlarged. Trip hero shows Safety/Comfort/
  Pace as small score rings (fixes word-wrap); avg speed dropped from the headline. Event filter
  toggles are compact icon chips.

- **J3 (v2.44) — graphical redesign:** trip hero drops the overall ring, centered/compacted (time +
  clock/route chips + Safety/Comfort/Pace rings). **You vs Traffic** is now one range gauge
  (free-flow→typical band + a "you" marker, Maps green/amber/red, one-word verdict, tight legend) —
  much less text. Replay header: big circular play button, clean M:SS/M:SS timer, live SpeedGauge.
  Removed dead Eta composables.
- **J4 (v2.45) — rough road:** replaced the opaque "rough road %" with discrete **rough stretches**
  (runs of ≥1 s sustained vibration) + a `bumpyScore` (RMS×duration). Schema v17 (migration 16→17);
  Road & ride card shows the stretch count.

- **J5 (v2.46) — review feedback:** lowercase am/pm with no dots/space ("12:14pm"); You-vs-Traffic
  gauge line now runs only between the two outer dots (no overhang), recoloured (blue "No traffic" /
  grey "Google" / perf-coloured "Actual"), legend renamed; replay speed readout averaged so it
  doesn't flicker.
- **J6 (v2.47) — consolidation:** folded the standalone Events card into the Driving card as an
  expandable "All events · N" list (summaries + speeding always visible, detail on demand). Removed
  dead EventsSection/CountPill.

Test suite 46, all green. All of the 2026-06-24 review feedback addressed and verified on device.

## Rev I (v2.40–v2.41): field-test prep + review UX — branch `rev-g-functional`

Field-test enablement (capture visibility + richer data) and review-experience changes.

- **Debug screen** (home title-bar bug icon): live capture health — motion Hz, GPS Hz, GNSS
  sats/C/N0/L5, sensor restarts — so capture can be confirmed mid-drive; build info; last
  speed-limit cache + lookup/Routes diagnostics. Validated live: motion 44 Hz, GNSS callback firing,
  cache served tiles 2/2 from cache.
- **Per-window GNSS capture**: new `gnss_samples` table (schema v16) written ~every 3 s for
  route-level GNSS analysis; fail-soft, trimmed/purged/deleted with the trip. Validated: 19 samples
  on a 58 s trip; per-trip `gnssSampleCount` populated.
- **Speeding colour tiers** (`SpeedTier`, unit-tested): yellow 0–10 km/h over, red 10+; on the map
  overlay and the replay speed curve. Replaces the old single-threshold red.
- **Bumps/potholes off by default** in the event filter; **"Speed" → "Speeding"**; trimmed event
  detail text.
- **Replay autoplay**: scrubs the whole trip over ~5 s on load, then a play button to replay; manual
  scrubbing pauses.
- **Past Trips**: frozen map on top + scrolling list; first tap previews a trip's route on the map
  (and shows time/quality/vs-Google), second tap opens it.
- `FIELD_TEST_PLAN.md` added (pre-drive check, narration, 13 scenarios).

Test suite 39 unit tests (added `SpeedTier`). Migrations 13→16 verified on device. Note: live
DB reads via `run-as cat` can be WAL-stale — pull `cartrip.db-wal`/`-shm` too for accurate counts.

## Rev H (v2.37–v2.39): caching, GNSS, list structure — branch `rev-g-functional`

Addendum work, prioritised for technical value (accuracy/robustness/reduced API dependency).
First a verification pass on the prior backlog (auto-stop, ETA fallback, naming, odd-turn all done
in Rev G; You-vs-Traffic and replay timeline already existed; AM/PM, debug mode, passive mode,
fuel/cost still open).

- **H1 (v2.37):** AM/PM time formatting; Past Trips grouped into collapsible recency buckets
  (Last 24 h / 3 days / 7 days / Last month / Older) via pure `TripBuckets`.
- **H2 (v2.38) — speed-limit cache:** new Room tables (schema v14) `cached_ways` (keyed by stable
  **OSM way id**, not Google Roads placeId — this app uses OSM/Overpass, and ODbL permits caching)
  and `cached_tiles` (~2.2 km fetch markers). `SpeedLimits` is now cache-first: only Overpass-queries
  tiles not fetched within a 30-day TTL, then serves the route from cache → a repeat drive makes zero
  network calls. `Tiles` helper (pure, tested); `lastCacheStat()` for debug.
- **H3 (v2.39) — GNSS quality layer:** `GnssStatus.Callback` aggregates a per-trip summary (sats
  used, mean/top C/N0, L5 seen) into trip schema v15; `GnssQuality` (pure, tested) → Strong/Moderate/
  Weak; folded into the data-quality badge (weak sky view caps High→Medium; detail shows "GNSS N
  sats M dB-Hz L5"). Diagnostics/confidence only — does not replace fused positioning.

Test suite now 35 unit tests (all green). Migrations 13→14→15 verified on the device DB.
Deferred (next): speed-limit-cache population QA (drive same road twice), reverse-geocoded naming,
debug screen (surface `lastCacheStat`/GNSS), passive auto-start, fuel/cost, frozen-map trip selection.

## Rev G (v2.35 / v2.36): functional hardening — branch `rev-g-functional`

Directive: focus on functionality, accuracy, correctness, robustness, sensitivity — not UI. Added
the project's first unit-test suite (23 tests) and used it to verify each fix.

- **Auto-stop end time (P0):** trip end is now placed at the real rest moment (last sample >4 km/h,
  then first stationary sample after it) instead of a fixed +60 s grace ~6 min late. New pure
  `record/AutoStop.kt`, fed by a rolling speed window in `RecordingService`.
- **Robust peak-G:** `FusedEventDetector.maxHorizG` uses p99.5 of horizontal-accel magnitude, not the
  raw max. Verified on real data: trip 784's spurious 1.10 g → 0.29 g; 790's 0.73 g → 0.14 g.
- **Sensor-stall recovery:** now also recovers a mid-trip motion stall, not just startup starvation.
- **Detection accuracy:** swerves gated on speed (≥25 km/h) so routine low-speed turns aren't labelled
  swerves; vertical-bump veto stops potholes fabricating brake/accel events.
- **ETA robustness:** `RoutesClient` retries transient failures with backoff, longer timeouts for long
  routes, and surfaces `lastDiagnostic()` instead of a silent null.
- **Trip naming:** nearest-place matching guarded by a 4 km radius (no more stray GTA landmark for
  out-of-area trips); time-of-day generic fallback. Full reverse-geocoding remains a roadmap item.
- Minor: `DisplayEvents.nearestPoint` trailing-event branch fix; correct `rawFixes` on trip reload.

## Main changes since v2.21

## Build environment used

PowerShell from `C:\Users\sinan\OneDrive\Desktop\cartrip-main`.

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat :app:assembleDebug --no-daemon
```

Install command:

```powershell
$adb='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk\platform-tools\adb.exe'
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Git may not be on the default shell PATH. Use:

```powershell
& 'C:\Program Files\Git\cmd\git.exe' status
```

## Main changes since v2.21

### Restored pre-UX functional work

- Restored the stashed functional changes that had produced the earlier installed `2.26 / 37` build.
- Added `DisplayEvents.kt` for cleaning overlapping raw detector signals into a smaller human-facing event list.
- Restored richer trip detail event presentation, clickable map event markers, and speed-limit preservation through reanalysis.
- Kept work on the main branch/worktree only; the separate UX redesign files were not merged.

### Speed-limit lookup and scoring fixes

- Rewrote `SpeedLimits.kt` to query Overpass in route-corridor chunks instead of one broad city-sized bounding box.
- Added OSM way dedupe, bounds pruning during nearest-way matching, and fail-soft diagnostics.
- Added smoothing for isolated one-point speed-limit mismatches. This avoids false peaks such as one highway point snapping to a nearby 50 km/h road.
- Added transactional speed-limit update in `TripDao.updateTripSpeedLimits(...)` so trip aggregates and per-point limits update together.
- Trip detail now uses route point coverage, not just aggregate trip coverage, to decide whether route coloring is available.
- Existing scored trips now show a `Refresh speed limits` control.

### Field-test findings

- Trip `786` morning commute had 2,488 GPS fixes but only 1 motion sample. GPS continued until trip end; motion stopped near trip start. Later trip `790` recorded about 91k motion samples, so this was likely a sensor callback starvation/startup issue rather than a permanent permission or hardware issue.
- Added sensor-stall mitigation in `RecordingService`: when GPS is alive but motion samples do not arrive after startup, the service unregisters/re-registers sensors up to three times.
- Trip `790` showed `speeding 24% / peak 59 over`. The 24% was supported by the points, but the peak was inflated by isolated limit mismatches. With smoothing, the peak becomes roughly 25 km/h over.

### Recording changes

- Fused Location now requests `500 ms` updates with `250 ms` fastest interval, rather than `1000 ms` / `500 ms`.
- Fused Location callback now processes every location in a delivered batch instead of only `lastLocation`.
- GPS delivery may still be capped by Android/Play Services/GNSS; the app now requests a faster cadence but cannot force the provider to supply it.

### Sensor and data-quality UI

- Data quality now reflects capture health only: motion Hz, GPS Hz, and GPS gaps.
- The old `fusion X%` label was removed from the top badge because it was only forward-axis confidence, not the amount of motion data captured.
- Forward-axis confidence remains in the beta detector section where it has context.

### Trip detail UI changes

- Route maps now fit expanded bounds so the trip initially occupies about two-thirds of the viewport instead of being tightly framed.
- Removed Driving-section percentage bars for braking, turns, acceleration, and jerky exposure from the rendered UI.
- Replaced them with a major-events summary for notable hard braking, hard acceleration, and sharp turns using the cleaned event list.
- Speeding is shown as a readable callout with:
  - time spent over the limit,
  - percent of covered drive,
  - peak recorded speed and matched speed-limit zone.

## Current caveats / next work

- The old `FactorBar` helper remains unused in `TripDetailScreen.kt` and can be removed in a cleanup pass.
- The beta sensor detector should eventually become a validated hybrid detector:
  - GPS provides route/speed context and brake-vs-accel sign.
  - Accelerometer provides true horizontal g and exact event timing.
  - Gyro provides yaw/turn evidence.
  - Vertical acceleration should suppress false brake/turn spikes caused by bumps.
- Consider replacing the single `fusedConfidence` field with clearer fields:
  - sensor capture quality,
  - axis confidence,
  - event agreement.
- Past trips cannot gain extra GPS fixes from the new 2 Hz request. Reanalysis can only recompute from samples that already exist.
