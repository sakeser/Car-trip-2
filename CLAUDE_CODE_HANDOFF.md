# Claude Code Handoff - Car Trip Analyzer

Date: June 23, 2026

This is the comprehensive handoff for continuing work on Car Trip Analyzer after the field-test/debugging session that started from `v2.21`.

## Executive Summary

Use `C:\Users\sinan\OneDrive\Desktop\cartrip-main` for mainline work.

Do not start from `C:\Users\sinan\OneDrive\Desktop\cartrip` unless the task is explicitly about the UX redesign branch. That worktree is on `ux-redesign-v1`, is dirty, and still reports `versionName=2.21`, `versionCode=32`.

Current mainline state:

- Branch/worktree: `main` in `C:\Users\sinan\OneDrive\Desktop\cartrip-main`
- Latest committed code before this handoff doc: `03afb0d Rev F: field-test fixes and trip detail polish`
- After committing this handoff doc locally, `main` is expected to be ahead of `origin/main` by 2 commits unless it has been pushed.
- Installed on the Samsung S25: `versionName=2.34`, `versionCode=45`
- Package: `com.cartrip.analyzer`
- Last verified install: June 23, 2026 at 15:31 local time
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`

The user has been field-testing on a connected Samsung S25. The most important themes were:

- Speed-limit lookup was freezing/looping on long trips.
- Long trips had speed-limit aggregate data but missing/incorrect per-point route coloring in some versions.
- Trip `786` had only one motion sample despite normal GPS.
- The installed app version was newer than source because pre-UX functional work had been stashed.
- The UX redesign branch is separate and should not be mixed into mainline field-test fixes unless explicitly requested.
- The trip detail UI was being iterated toward a cleaner, more useful field-test analysis view.
- The user enabled Android developer option `Allow GNSS Raw Measurements` and wants to know how raw GNSS/GNSSLogger ideas could apply.

## Worktree and Branch Layout

There are two active worktrees:

```text
C:\Users\sinan\OneDrive\Desktop\cartrip       ux-redesign-v1
C:\Users\sinan\OneDrive\Desktop\cartrip-main  main
```

### `cartrip-main`

This is the correct worktree for current functional work.

At the time of this handoff:

- `app/build.gradle.kts`: `versionName=2.34`, `versionCode=45`
- Main functional fixes are committed in `03afb0d`.
- A shorter summary is also in `REV_HISTORY.md`.

### `cartrip` / `ux-redesign-v1`

This worktree is dirty and should be treated as user/previous-agent WIP.

Current known dirty files there:

```text
M  app/src/main/java/com/cartrip/analyzer/MainActivity.kt
M  app/src/main/java/com/cartrip/analyzer/ui/GuideScreen.kt
M  app/src/main/java/com/cartrip/analyzer/ui/HomeScreen.kt
M  app/src/main/java/com/cartrip/analyzer/ui/TripDetailScreen.kt
M  app/src/main/java/com/cartrip/analyzer/ui/TripListScreen.kt
?? app/src/main/java/com/cartrip/analyzer/ui/Onboarding.kt
?? app/src/main/java/com/cartrip/analyzer/ui/Premium.kt
?? app/src/main/java/com/cartrip/analyzer/ui/VehicleFuel.kt
```

It still reports:

```kotlin
versionCode = 32
versionName = "2.21"
```

Do not overwrite, reset, or merge this UX worktree casually. If the user resumes UX redesign work, first decide whether to:

1. Commit/stash the dirty UX WIP as-is, then rebase/merge mainline Rev F into it, or
2. Create a fresh branch from `main` and port UX screens selectively, or
3. Keep UX redesign separate and continue mainline data/analysis work.

Recommended path for UX later:

- Create a new integration branch from `main`.
- Bring over UX components screen-by-screen.
- Preserve the `main` analysis fixes, speed-limit pipeline, map behavior, and recording service changes.
- Resolve `TripDetailScreen.kt` carefully because both branches changed it heavily.

## Build, Install, and Local Tooling

Git may not be on the default shell PATH. Use:

```powershell
& 'C:\Program Files\Git\cmd\git.exe' status
```

Build from:

```text
C:\Users\sinan\OneDrive\Desktop\cartrip-main
```

Build environment used:

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
```

The Google Maps API key is not committed. It was supplied from the original worktree's `local.properties` at build time. Do not print or commit it.

Typical build command:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Typical install command:

```powershell
$adb='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk\platform-tools\adb.exe'
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Verify installed version:

```powershell
& $adb shell dumpsys package com.cartrip.analyzer | Select-String -Pattern 'versionCode|versionName|lastUpdateTime|firstInstallTime'
```

Important: ADB sometimes loses the S25. If `adb devices -l` is empty, unplug/replug, unlock the phone, accept any USB debugging prompt, or run:

```powershell
& $adb kill-server
& $adb start-server
& $adb devices -l
```

## Version Timeline Since v2.21

Baseline:

- `a6c56ff Rev E: fold the validated fused detector into scoring (v2.21)`
- This is also the commit that `origin/main` and `ux-redesign-v1` pointed at before the mainline field-test worktree was created.

Restored pre-UX functional work:

- The phone had earlier been running a debug build with `versionName=2.26`, `versionCode=37`.
- Source on visible worktrees appeared to be `2.21 / 32`, which caused confusion.
- Investigation found a stash named `stash@{0}` (`pre-ux-redesign-v1 user changes`) containing the functional changes that produced the installed `2.26 / 37`.
- Those functional changes were restored into `cartrip-main`, not into the UX redesign worktree.

Final state of this field-test session:

- Mainline installed build is `2.34 / 45`.
- The mainline changes are committed in `03afb0d Rev F: field-test fixes and trip detail polish`.

## Field-Test Data Findings

The app database was pulled from the S25 during analysis. The database itself should not be committed.

### Trip `786`

Morning commute:

- Start/end: June 23, 2026, around 07:07 to 07:49 local time
- Distance: about 43.79 km
- GPS fixes: 2,488
- Analysis points: 2,221
- Motion samples: 1
- GPS continued until trip end, but `lastMotionAt` was essentially trip start.

Conclusion:

- This was not a general permission/hardware failure, because later trip `790` recorded about 91k motion samples.
- Likely cause: Android sensor callback starvation or sensor callback registration failure after service start.

Fix added:

- `RecordingService` now detects early motion starvation while GPS is alive.
- It unregisters/re-registers sensors up to three times if motion samples do not arrive after startup.

### Trip `790`

Most recent long trip at time of analysis:

- Distance: about 39.82 km
- Duration: about 2001 seconds
- GPS fixes: 2,002
- Motion samples: 91,326
- Motion rate: about 45.6 Hz
- GPS rate: about 1.0/s before the later faster GPS request change
- Speeding stored summary initially showed about `24%` and `peak 59 over`.

Findings:

- `24%` was substantially supported by the point data: 454 of 1910 covered moving samples were more than 3 km/h over limit.
- `peak 59 over` was not trustworthy. It came from isolated one-point speed-limit mismatches where a highway point briefly snapped to a nearby 50 km/h road while neighboring points were 100 km/h.

Fix added:

- `SpeedLimits.kt` smooths isolated speed-limit islands when the previous and next matched limits agree.
- After smoothing, trip `790` peak-over became roughly 25 km/h over, for example about 125 km/h in a 100 km/h zone.

## Main Code Changes Since v2.21

### `app/build.gradle.kts`

- Bumped to `versionName=2.34`, `versionCode=45`.
- Keeps Maps API key and Google Map ID via manifest placeholders from `local.properties` or environment variables.

### `SpeedLimits.kt`

Why:

- The speed-limit button/screen appeared stuck on long trips.
- Old lookup used one broad bounding box around the whole route.
- For long GTA-area trips this returned large Overpass responses and made Android-side JSON/matching expensive.

What changed:

- Route corridor boxes are generated along the route.
- Overpass queries are chunked.
- OSM ways are deduped.
- Nearest-way matching prunes by way bounding boxes.
- Isolated one-point limit mismatches are smoothed.
- Fail-soft diagnostics remain.

Important behavior:

- Moving points are points with speed at least 8 km/h.
- Speeding uses speed limit plus 3 km/h tolerance.
- Coverage is covered moving points divided by moving points.
- Speeding percent is speeding covered points divided by covered moving points.

Next work:

- Add better limit-transition handling around ramps and parallel roads.
- Store match diagnostics for each speed-limit refresh, such as Overpass endpoint, way count, coverage, and smoothing count.
- Consider requiring a minimum run length before peak-over can be reported.

### `TripDao.kt`

Added:

- `updateTripSpeedLimits(trip, points)` transaction.

Why:

- Aggregate trip fields and per-point speed limits must update together.
- Avoids a UI state where trip summary says speed limits exist but map/chart point data is stale or missing.

### `TripFinalizer.kt` and `TripViewModel.kt`

Restored/preserved:

- Speed-limit values survive reanalysis when possible.
- Reanalysis preserves speed-limit point annotations instead of wiping them unnecessarily.
- `DisplayEvents.kt` participates in loading/presenting cleaned events.

Why:

- Past trips may have speed-limit work already done.
- Reanalysis is for detector/sensor metric recalculation and should not force another speed-limit lookup unless required.

### `RecordingService.kt`

Changes:

- Sensor starvation restart mitigation.
- Faster Fused Location request: `500 ms` interval, `250 ms` fastest interval.
- Processes all locations in a Fused Location batch, not just `lastLocation`.

Why:

- Trip `786` showed motion callback starvation.
- More GPS fixes may improve temporal resolution for speed/acceleration/event timing.
- Android can deliver batched locations; using only `lastLocation` discards useful fixes.

Important caveat:

- The app can request faster GPS, but Android/Play Services/GNSS may still cap real delivery.
- Past trips cannot gain extra GPS fixes. Reanalysis can only use samples already recorded.

### `TripDataQuality.kt`

Changed:

- Data quality now reflects capture health only:
  - motion Hz,
  - GPS Hz,
  - GPS gaps.

Removed from top badge:

- `fusion X%`.

Why:

- `fusedConfidence` was not "percent of accelerometer data used."
- It is only forward-axis inference confidence.
- On steady highway trips with few strong accel/brake moments, it can be low even when motion capture is excellent.

### `FusedEventDetector.kt`

Restored/improved:

- Magnitude-first fused detector.
- Swerve reversal detection.
- Forward-axis confidence remains diagnostic only.

Current conceptual model:

- Use gravity vector to find "down".
- Horizontal acceleration magnitude detects candidate brake/accel moments.
- GPS speed slope classifies brake vs accel sign.
- Gyro yaw detects turns/swerves.
- Confidence is mainly about forward-axis inference, not event validity.

Important caveat:

- It is still "review-grade" for some UI/counts.
- Do not treat all fused events as final truth without validation.

### `DisplayEvents.kt`

New file.

Purpose:

- Cleans overlapping raw detector signals into a smaller human-facing event list.
- Clusters nearby signals.
- Filters weak/noisy signals.
- Suppresses bump echoes that would otherwise look like brake/accel events.

Used by:

- Trip detail map markers.
- Trip detail event list.
- New Driving card major-event summary.

### `TripDetailScreen.kt`

This file changed heavily.

Key changes:

- Auto speed-limit fetch no longer leaves `fetchingLimits` stuck due to missing `finally`.
- Manual speed-limit refresh uses `try/finally`.
- Route speed-limit coverage is computed from loaded points, not only aggregate trip row.
- Existing scored trips expose `Refresh speed limits`.
- Speeding line now reports:
  - time over limit,
  - percent of covered drive,
  - peak speed in matched speed-limit zone.
- Driving section no longer renders percentage bars for braking, turns, accel, or jerky.
- Driving section now summarizes notable major events from cleaned events:
  - hard braking,
  - hard acceleration,
  - sharp turns.
- The expanded beta detector text now says "review-grade counts" instead of "not scored yet".

Known cleanup:

- The old `FactorBar` helper remains unused in `TripDetailScreen.kt`. It can be removed in a cleanup commit.

### `TripMap.kt`

Changes:

- Added clickable event marker restoration.
- Added map event icons.
- Added red route overlay for speeding segments.
- Initial map camera now uses expanded bounds so the route occupies roughly two-thirds of the viewport rather than being tightly fit.

Why:

- User asked route maps to start zoomed out about 66% instead of fitting exactly.
- Tight route fits were visually cramped.

Potential refinement:

- Tune `visibleRouteFraction` in `relaxedBounds(...)`.
- Also consider applying a similar relaxed fit to `TripHeatMap.kt` if the user wants consistent map framing elsewhere.

### `TripScores.kt`

Current scoring concept:

- Speeding only counts when speed-limit coverage is at least 40%.
- Safety uses hard braking, turns, acceleration, speeding, severe over-limit, and peak g.
- Comfort blends GPS event rate with fused event rate based on motion sample rate.

Important note:

- `fusedConfidence` is not used to gate score trust because it is only forward-axis inference confidence.
- Motion sample rate gates sensor trust.

Potential improvement:

- Replace raw fused event count blending with validated hybrid events.

### `GuideScreen.kt`

Updated wording:

- Removed stale hard-coded "~1 Hz GPS" and "~25 Hz accelerometer" language after location and motion behavior changed.

## UX Redesign Branch Notes

The user explicitly said at the start of this session:

```text
focus on the main branch, not the ux redesign for this.
```

That instruction was followed.

The UX redesign worktree is at:

```text
C:\Users\sinan\OneDrive\Desktop\cartrip
```

It contains a large UI redesign across Home, Trip Detail, Trip List, Guide, and MainActivity, plus new files for onboarding/premium/vehicle/fuel features. It has not been reconciled with Rev F mainline fixes.

If asked to resume UX:

1. Protect the dirty UX work first:
   - commit it to `ux-redesign-v1`, or
   - stash it with a clear message, or
   - copy it to a new branch/worktree.
2. Do not merge UX into `main` blindly. Both sides changed `TripDetailScreen.kt`.
3. Prefer a new integration branch from `main`.
4. Port UX screen-by-screen.
5. Preserve:
   - `SpeedLimits.kt` route-corridor lookup,
   - `TripDao.updateTripSpeedLimits(...)`,
   - speed-limit preservation in finalizer/viewmodel,
   - `DisplayEvents.kt`,
   - sensor-stall recovery,
   - faster/batched location handling,
   - data-quality changes,
   - trip map relaxed bounds and event markers.

## GNSS Raw Measurements / Google GNSSLogger Findings

The user enabled Android developer option:

```text
Allow GNSS Raw Measurements
```

This does not automatically change Car Trip Analyzer. It only allows apps that register for raw GNSS APIs to receive raw measurements.

Current app status:

- The app does not yet register `GnssMeasurementsEvent.Callback`.
- The app does not yet store raw GNSS measurements.
- The app does not yet implement weighted-least-squares GNSS positioning or RINEX export.

S25 capability check via `adb shell dumpsys location` showed:

- GNSS Manager capabilities include `MEASUREMENTS`.
- It also reports `SATELLITE_PVT`, `MEASUREMENT_CORRECTIONS`, `MEASUREMENT_CORRECTIONS_FOR_DRIVING`, and multi-frequency signal types.
- Google GNSSLogger was visible as `com.google.android.apps.location.gps.gnsslogger`.
- GNSSLogger had registered GNSS measurement callbacks and received historical GNSS measurement events.

Meaning:

- The S25 appears capable of raw GNSS measurement capture.
- Car Trip Analyzer can add GNSS diagnostics and logging.
- This is not the same as automatically making the normal Android location route more accurate.

What Google GNSSLogger does conceptually:

- Registers for Android raw GNSS measurements.
- Logs receiver clock and satellite measurements.
- Tracks constellation/signal data.
- Can support post-processing such as pseudorange, Doppler, weighted-least-squares, and RINEX-like analysis.
- It is mainly a diagnostics/research logger, not a drop-in navigation provider.

Recommended GNSS integration plan:

### Phase 1 - GNSS quality layer

Add lightweight capture during trips:

- `GnssStatus.Callback`
  - satellite count,
  - satellites used in fix,
  - constellation types,
  - average/top C/N0,
  - L1/L5 availability when available.
- `GnssMeasurementsEvent.Callback`
  - measurement event rate,
  - measurement count,
  - constellation/signal summaries,
  - pseudorange-rate or Doppler availability,
  - accumulated delta range availability.

Store compact summaries, not every raw measurement initially.

Use this for:

- Better data-quality badge.
- Detecting urban canyon / weak sky view.
- Explaining unreliable route/speed data.
- Dynamically tuning Kalman measurement noise.

### Phase 2 - optional raw log export

Add a user/debug option to export raw GNSS logs for a trip.

Potential formats:

- app-native CSV,
- GNSSLogger-compatible text-ish logs,
- later RINEX if needed.

Do not store huge raw GNSS logs by default unless retention and file size are handled.

### Phase 3 - deeper GNSS/IMU fusion

Potential future work:

- Use GNSS Doppler/pseudorange-rate as an independent velocity check.
- Use IMU to bridge short GNSS gaps.
- Compare Android Fused Location against raw GNSS-derived velocity/position.
- Add RTK/PPP/corrections only if the user wants a larger research-grade positioning project.

Important warning:

- Full raw-GNSS positioning is nontrivial. It needs satellite ephemeris/time handling, measurement corrections, outlier rejection, coordinate transforms, and potentially external correction data.
- The high-value first step is quality/diagnostics, not replacing Android Fused Location.

## Current Bugs / Cleanup Items

### High priority

- Remove unused `FactorBar` helper from `TripDetailScreen.kt`.
- Rebuild after cleanup and install if requested.
- Consider committing this handoff doc if not already committed.
- Push `main` to remote if the user wants Claude Code on another machine/session to fetch it.

### Medium priority

- Refresh README: it still says osmdroid/no API key, but current app uses Google Maps and requires a Maps API key placeholder.
- Add speed-limit refresh diagnostics in UI or logs.
- Add a "speed-limit confidence" line: coverage, source, refreshed time.
- Add route speed-limit match debug view for bad OSM snaps.
- Add sensor restart diagnostics to trip quality or export so callback starvation can be confirmed.

### Lower priority

- Apply relaxed map bounds to `TripHeatMap.kt` if desired.
- Add a settings screen for speed tolerance, event thresholds, map zoom padding, and raw GNSS logging.
- Add retention controls for raw motion/GNSS data.

## Potential Next Features

### Validated hybrid sensor detector

Goal:

- Convert the beta sensor detector into a validated hybrid detector.

Proposed model:

- GPS: route context, speed trend, brake-vs-accel sign.
- Accelerometer: true horizontal g and event timing.
- Gyro: yaw/turn evidence.
- Vertical acceleration: suppress bump-induced false brake/turn spikes.

Implementation idea:

- Add an event-review pass that labels each event:
  - GPS-confirmed,
  - sensor-only strong,
  - likely bump/road-noise echo,
  - ambiguous.
- Store reviewed/hybrid events separately from raw detector events.
- Score only reviewed/hybrid events.

### GNSS quality and diagnostics

See GNSS section above.

First useful UI:

- "GNSS quality: strong/moderate/weak"
- satellite count and used-in-fix count
- C/N0 signal summary
- L5/dual-frequency availability
- measurement event rate

### Trip data repair/recompute tools

Possible buttons:

- Reanalyze trip.
- Refresh speed limits.
- Recompute route coloring only.
- Export raw diagnostic bundle.

### Better speed-limit semantics

Ideas:

- Speeding duration by road class.
- Peak over-limit with minimum run length.
- Peak speed/limit ratio.
- Display "70 in a 40" as more severe than "130 in a 100" when ratio is worse.
- Separate highway/arterial/local speeding callouts.

### Better field-test workflow

Add a developer/debug screen:

- current app version,
- Maps key present/missing,
- motion sample rate live,
- GPS update rate live,
- GNSS measurement callback active/inactive,
- last sensor restart attempt,
- database export button.

## Important Behavioral Notes

- Existing trips show new UI immediately if the needed data is already in Room.
- Existing trips need `Refresh speed limits` to recompute speed-limit matching with the newest OSM matcher.
- Existing trips do not need `Re-analyze` for speed-limit matching.
- `Re-analyze` is for detector/sensor metric recomputation from already-saved raw locations/motions.
- Past trips cannot benefit from the new faster GPS request because missing fixes were never recorded.
- Past trips with no motion samples cannot gain accelerometer-derived metrics from reanalysis.

## Files Most Likely To Be Touched Next

```text
app/src/main/java/com/cartrip/analyzer/record/RecordingService.kt
app/src/main/java/com/cartrip/analyzer/analysis/TripAnalyzer.kt
app/src/main/java/com/cartrip/analyzer/analysis/FusedEventDetector.kt
app/src/main/java/com/cartrip/analyzer/analysis/MotionFusion.kt
app/src/main/java/com/cartrip/analyzer/cloud/SpeedLimits.kt
app/src/main/java/com/cartrip/analyzer/data/Entities.kt
app/src/main/java/com/cartrip/analyzer/data/AppDatabase.kt
app/src/main/java/com/cartrip/analyzer/data/TripDao.kt
app/src/main/java/com/cartrip/analyzer/ui/TripDetailScreen.kt
app/src/main/java/com/cartrip/analyzer/ui/TripMap.kt
app/src/main/java/com/cartrip/analyzer/ui/TripDataQuality.kt
app/src/main/java/com/cartrip/analyzer/ui/DisplayEvents.kt
```

If adding GNSS:

- Add new Room entities/table(s) for compact GNSS summaries.
- Add migrations in `AppDatabase.kt`.
- Add callbacks in `RecordingService.kt`.
- Add export columns in `ExportData.kt` and/or `TripExcel.kt`.
- Add UI in `TripDataQuality.kt` or a new diagnostics card.

## Suggested First Task For Claude Code Tomorrow

Start with a small cleanup/validation pass:

1. Confirm `cartrip-main` is clean and on `main`.
2. Remove unused `FactorBar` from `TripDetailScreen.kt`.
3. Update README to reflect Google Maps/API key reality.
4. Build `:app:assembleDebug`.
5. If S25 is connected, install and verify version.
6. Commit the cleanup.

Then pick one larger direction:

- GNSS quality layer, or
- hybrid sensor detector validation, or
- UX redesign integration.
