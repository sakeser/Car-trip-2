# Revision History and Handoff

This file is the working handoff for the main branch. The UX redesign worktree was intentionally not used for the June 23, 2026 field-test fixes.

For the full Claude Code continuation brief, including UX worktree notes, GNSS/raw-measurement findings, and a prioritized next-step backlog, see `CLAUDE_CODE_HANDOFF.md`.

## Current phone build

- Package: `com.cartrip.analyzer`
- Installed on S25: `versionName=2.47`, `versionCode=58`
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
