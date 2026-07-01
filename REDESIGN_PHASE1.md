# UX Premium Redesign — Phase 1 Handoff (engine extraction)

---

## ⭐ NEXT-AGENT HANDOFF (2026-07-01) — premium `:ui-next` UI: state + next priorities

**Read this first; the older phase notes below are historical detail.** You are continuing the premium UI
redesign (the ChatGPT/owner build spec, kept in Downloads as `CarTrip_UX_Redesign_Build_Spec_for_Claude.md`;
its vision = a Material-3 **5-tab** product: **Drive · Trips · Insights · Map · More**, map-first, plain-English,
local-first). Legacy `app/ui` is the **working oracle — do not break it**; `:ui-next` is the new premium UI,
still **debug-gated** (Home → Options → Diagnostics → "Open :ui-next trip list (preview)").

### Where things stand
- **Everything is on `main`.** The whole premium-modular redesign was merged (`ux-premium-modular-v1` → `main`,
  merge commit `3dcb781`, pushed). ⚠️ **One newer commit is on LOCAL `main` and NOT pushed: `589d8fd`** (Trip Line
  + You-vs-Traffic, below) — confirm with `git log origin/main..main`; push needs explicit owner OK.
- **Version 3.54 / build 165, Room schema v22** (no schema change in any recent UI work).
- **Map routes coloured by Smoothness (2026-07-01, commit `cce481a`, S25 PASS):** Map-hub route polylines are
  coloured by their trip's Smoothness (green/amber/red, ScoreChip thresholds) with a Smooth/OK/Rough legend. No
  new data (reuses `TripSummary.smoothnessScore`).
- **Interactive Map (2026-07-01, commit `bdb6219`, S25 PASS):** Map-hub route polylines are now clickable
  (tagged with trip id) — tap a route -> opens that trip's detail. No new gateway.
- **Map Events/trouble-spots layer (2026-07-01, commit `1388e17`, S25 PASS):** layer chips are toggles now
  (Routes + Events; Speeding/Heatmap still disabled placeholders). The Events layer overlays recent-drive events
  as colour-coded pins (red brake / orange accel / violet corner / yellow rough road), lazy-loaded + capped at
  250. **Smallest gateway:** `TripEvent` gained `lat`/`lon`/`hasPosition`, correlated to the nearest analysis
  sample by time (<=15 s) in `getEvents` — mirrors the legacy TripMap snap. Rough/dense by design (clustering is a
  later refinement).
- **Trip Detail "At a glance" stats grid (2026-07-01, commit `9778ff4`, S25 PASS):** a read-only grid of raw
  measured stats (top speed, avg moving speed, moving vs idle time, hard brake/accel/corner counts) via a new
  engine-api `TripStats` value type + `TripSummary.stats` (mapped from existing `TripEntity` fields). **Known
  rough edge (flagged, deferred):** the grid's hard-X counts use `TripEntity.hardXCount` (threshold-based) while
  the Trip Line ticks use `drive_events` (softer detections) — they can disagree under the shared "Hard ..."
  labeling; reconciling touches event/threshold semantics the owner wants kept stable.
- **Insights v2 charts (2026-07-01, commit `5d95624`, S25 PASS):** the Insights tab gained two read-only chart
  surfaces — **Distance by day** (7-day vertical bar chart) + **When you drive** (time-of-day daypart bars),
  windowed by the shared recency chips. Pure aggregation (`InsightsMetrics.kt`: `dailyDistanceKm`/`daypartCounts`
  via `java.time`, `nowMs`+`zone` params for deterministic tests) + charts (`InsightsCharts.kt`). No new gateways.
- **Breadth batch — full 5-tab shell (2026-07-01, commit `b587073`, S25 PASS):** the shell is now the spec's
  **Drive / Trips / Insights / Map / More** bottom nav. New rough, read-only surfaces: `DriveScreen` (placeholder
  recording home — recording stays in legacy until a `RecordingController` gateway + M1), `MapHubScreen` (real
  data: overlays the recent drives' routes via `getRoute`, downsampled + framed; Trouble-spots/Heatmap chips are
  disabled placeholders), `MoreScreen` (settings hub placeholders). Trip Detail gained a real **Events** list
  (`getEvents` -> kind + m:ss) and an **Efficiency "coming soon"** pillar. No new engine-api gateways.
- **Health-tab v2 (2026-07-01, commit `55dd432`, S25 PASS):** the Health tab now has the shared recency chips +
  a windowed Driving-Intelligence overview (N scorable drives / km, avg Smoothness + Demand) + a **Smoothness
  trend sparkline** + drive mix. Pure aggregation/visualization of existing scores (`DrivingHealth.kt`:
  `drivingHealth()` + tests) — no scoring/explanation logic (owner deferred the Drive-Stress explainer while the
  model may change). `RecencyFilterRow` extracted to a shared file (Trips + Health both use it).
- **Map-interaction batch (2026-07-01, commit `f12e5d3`, S25 PASS):** 1-finger map pan + Trip Line<->map scrub
  sync (see priority #1 below). `TripTrackPoint` gained `lat`/`lon`/`hasPosition`.
- **Trips-tab v2 (2026-07-01, commit `80a5ccd`, S25 PASS):** recency filter chips (24h/3d/7d/30d/All) + a
  drive-only window summary bar + per-filter empty state + "Walk / non-drive" rows. `TripSummary` gained
  `isDrive`; pure `:ui-next` `TripWindow.kt` (`RecencyWindow`/`inWindow`/`windowSummary`) does the filtering.
  **`EngineBoundaryTest` hardened to also forbid `analysis.*` imports in `:ui-next`** (was a documented-intent
  gap). Codex did the pure logic+tests and a diff review.
- **Latest batch (2026-07-01, commits `589d8fd` + fix `65b6af8`, build-green, S25 PASS):** Trip Detail depth — the
  **Trip Line** (`TripLine.kt`, a Canvas speed-vs-time chart: filled speed curve + dashed posted-limit + coloured
  event ticks + adaptive legend) and **You-vs-Traffic** (`YouVsTraffic.kt`, verdict + proportional
  You/Typical/Free-flow bars). New engine-api reads: `TripRepository.getTrack`/`getEvents` (+
  `TripTrackPoint`/`TripEvent`/`TripEventKind` value types, pure unit-tested mappers) and
  `TripSummary.etaTrafficSeconds`/`etaFreeFlowSeconds` (gated to real drives with a fetched ETA). Codex-reviewed.
  **⚠️ On-device bug caught + fixed (`65b6af8`):** `AnalysisPointEntity.t`/`DriveEventEntity.t` are a **monotonic
  recording clock (elapsedRealtime), NOT epoch ms** — the first cut offset from the trip's epoch `startTime`, so
  every point clamped to x=0 (chart was a vertical line). Fix = derive the x-origin from the earliest sample `t`
  (`TripDao.getFirstAnalysisPointTime`); regression test uses a non-epoch base. **S25 PASS on trips 1190 (amber
  "1 min slower") + 1189 (red "7 min slower", stop-and-go dip visible); every value matched DB-replay.** **This
  knocks out most of priority #1's detail sections; still open on the detail: map<->timeline scrub sync, an
  explicit Drive-Stress explainer, efficiency pillar.**
- **Modules:** `:app` (legacy UI + host), `:core-engine` (all engine: analysis/data/cloud/record/export/settings
  + the `com.cartrip.engine.api` seam), `:ui-next` (premium Compose UI). Packages stay `com.cartrip.analyzer.*`
  inside the engine — **do not rename.**

### What `:ui-next` has today (all S25-verified)
- **App shell:** `TripsNextRoot` → `HomeShell` = one `Scaffold` with a **bottom nav (Trips / Health)** + top bar;
  trip data observed once and shared; trip **detail is a full-screen drill-in** (nav route `detail/{id}`).
- **Trips tab** (`TripListContent`): premium rows — date, `km · duration`, the **Drive-Quality verdict**, a
  green=good **Smoothness** `ScoreChip`. Tap → detail.
- **Trip Detail** (`TripDetailNextScreen`): **map-first** — `TripMapHero` (route polyline + start/end markers,
  framed to the route) → date/distance/duration summary → the **Driving Intelligence** card (Drive-Quality
  headline + Smoothness & Demand pillars via `PillarRow`/`ScoreChip`/`StressChip`). **Efficiency pillar is
  deliberately omitted** (needs a vehicle profile the engine-api mapper can't hold).
- **Health tab** (`InsightsContent`): a Driving-Intelligence overview aggregated from `TripSummary` — avg
  Smoothness + Demand pillars + a Drive-Quality "drive mix" count.
- Own theme `CarTripNextTheme` (teal-on-deep-neutral, dark/light). ASCII source only (Cp1252 trap; build glyphs
  from code points, e.g. `MIDDOT = 0x00B7.toChar()`).

### Engine-API surface you build against (the ONLY way `:ui-next` reaches the engine)
`com.cartrip.engine.api`:
- `TripRepository.create(context)` → `observeTrips(): Flow<List<TripSummary>>`, `suspend getTrip(id)`,
  `suspend getRoute(id): List<RoutePoint>` (1 Hz track lat/lon, invalid fixes dropped),
  `suspend getTrack(id): List<TripTrackPoint>` (speed timeline: offsetSeconds-from-start + speedKmh +
  nullable OSM limit), `suspend getEvents(id): List<TripEvent>` (typed hard-brake/accel/corner/road ticks on
  the same x-axis). Both use pure mappers (`toTrack`/`toEvents`/`eventKindOf`) unit-tested in
  `TripSummaryMapperTest`.
- `TripSummary` (DTO, no Room leak): id, start/end epoch, distanceMeters, durationSeconds, `stressScore`/
  `stressBand` (= Demand), `smoothnessScore`/`smoothnessBand`, `driveQuality` (the conditional headline).
- `RoutePoint(lat, lon)`.
- Pure value types that stay public: `DrivingIntelligence`, `StressScore`, `TripScores`, `DriverLoad`,
  `FuelEstimator`, `StopAndGo`, `TripKind` (in `analysis`), but **`:ui-next` must NOT import `analysis.*`** — the
  `EngineBoundaryTest` (a source scan in `:ui-next` test) fails the build if `:ui-next` imports
  `com.cartrip.analyzer.{data,cloud,record,export,settings,ui}`. Derive band words / colours locally in `:ui-next`.
- **Add gateways only when a screen needs one** (the last one added was `getRoute` for the map). Documented but
  not built: `RecordingController`, `SyncGateway`, `ExportGateway`, `SettingsStore`/vehicle gateway.

### Build / validate / device (the workflow — do NOT use plain gradlew)
OneDrive relocate workaround (output goes to `cartrip-build-out/<module>`, nothing leaks into the tree):
```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :ui-next:testDebugUnitTest :core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
Grep the log for `BUILD SUCCESSFUL` (the pipe can mask Gradle failures). **~237 tests green.** APK →
`C:\Users\sinan\cartrip-build-out\app\outputs\apk\debug\app-debug.apk`. **Bump `versionCode`/`versionName` in
`app/build.gradle.kts` before the install-facing assemble.** Batch device testing: local Gradle while iterating,
one S25 pass at the end of a coherent batch (ADB at `...\cartrip-build-tools\android-sdk\platform-tools\adb.exe`;
screencap to a non-OneDrive path; the `:ui-next` map/UI needs a real device — it can't be unit-verified).

### Known gaps / carry-forward
- `8f6c44e` (map-first detail) is **committed locally on `main` but not pushed.**
- `:ui-next` still doesn't replace legacy: **no Drive (record) tab, no Map tab, no More/Settings, no efficiency
  pillar, no fuel/cost on the detail.** (DONE since this note: Trip Line + events + you-vs-traffic on the detail,
  map<->timeline scrub sync, 1-finger map pan, Trips-tab recency filters + summary + non-drive rows, and Health-tab
  windowed overview + smoothness trend.) **Drive-Stress explainer: DEFERRED by the owner** (scoring/explanation
  model still subject to change — don't build UI explaining it yet).
- Naming: `:ui-next` has no trip **route names** yet (`TripSummary` has none) — the detail headline uses the
  date. Real names come from legacy `GeoNamer`/`TripLabeler` (presentation-domain, not engine) — a later gateway.
- Do NOT reopen: **Speed-Interruption / Traffic-Wave** (real-data calibration decided *no new detector*) and
  **Driving-Intelligence scoring tuning** (needs owner-labeled trips), unless a UI need directly requires it.

### Recommended next UI priorities (from the spec, highest visible value first)
1. **Trip Detail depth (signature work).** ✅ **DONE (`589d8fd`, `f12e5d3`):** the **Trip Line**
   (speed/limit/events timeline) + **You-vs-Traffic**, plus **1-finger map pan** (legacy `mapTouched` scroll-gate)
   and the **map<->Trip Line scrub sync** (tap/drag the timeline -> a cyan marker tracks the same sample along the
   route + a speed readout; `TripTrackPoint` now carries lat/lon + `hasPosition`). All S25-verified (trip 1190,
   North York->401->Yorkdale). **Still open on this screen:** (a) a plain-English **Drive-Stress explainer** (why
   this trip scored its Demand), (b) the **Efficiency pillar** (blocked on the vehicle gateway, see #3). This is
   now the polished, interactive signature screen.
   - **Map-marker gotcha (maps-compose 4.4.2):** there is NO `rememberUpdatedMarkerState`, and a fresh
     `MarkerState(pos)` each recomposition does NOT move the marker — keep one `rememberMarkerState()` and push
     positions in via `SideEffect`. A scrubber gesture needs a single `awaitPointerEventScope` loop (tap+drag);
     `detectHorizontalDragGestures` + `detectTapGestures` together conflict.
2. **Trips tab polish:** ✅ **DONE (`80a5ccd`):** recency filter chips (24h/3d/7d/30d/All) + a drive-only window
   summary + per-filter empty state + "Walk / non-drive" rows (via `TripSummary.isDrive` + pure `TripWindow`).
   **Still open (optional):** a frozen map preview header per row / thumbnail.
3. **Efficiency pillar** across detail + Health: add a **vehicle gateway** (`SettingsStore` exposing the
   `settings/VehiclePrefs` profile through engine-api) so `DrivingIntelligence.from(trip, vehicle)` can run in
   `:ui-next`; then show the 3rd pillar + fuel/cost.
4. **Map tab** (spec's 4th tab): ✅ **ROUGHED IN + INTERACTIVE + EVENTS (`b587073`, `bdb6219`, `1388e17`):**
   `MapHubScreen` overlays recent routes (`getRoute`); tap a route -> detail; the **Events/trouble-spots** layer
   plots position-enriched events (`TripEvent.lat/lon`). **Next depth:** cluster the dense pins; wire the
   **Speeding** + **Heatmap** placeholder chips (need aggregate read gateways / a speeding-segment source).
5. **Drive tab + recording** (biggest): ✅ **PLACEHOLDER IN (`b587073`, `DriveScreen`).** The real flow still
   needs a `RecordingController` gateway + the `RecordingState` surface + M1 (engine self-describing manifest)
   before `:ui-next` can host the foreground recording flow — do later.
6. **More/Settings** hub: ✅ **MENU ROUGHED IN (`b587073`, `MoreScreen`).** **Next:** wire each row to a real
   settings screen behind an engine-api `SettingsStore` gateway (start with Vehicle & fuel — it also unblocks the
   Efficiency pillar, #3). See the "Settings architecture" spec in `ROADMAP_NEW.md`.

Deeper detail on each phase + the engine-API facade decision is in the historical sections below.

---

> **HANDOFF FOR THE NEXT AGENT — read this first.**
> 1. **Phase 1B (the engine extraction) is COMPLETE** — `analysis/data/cloud/record/export/settings` + their
>    tests now live in `:core-engine`. Build green, 223 tests / 0 failures. See "Phase 1B — DONE" below.
> 2. **Do NOT rename packages.** They stay `com.cartrip.analyzer.*` inside `:core-engine` (deliberate — renaming
>    on top of the module move was judged too risky; rename later, if ever).
> 3. **On-device verification PASSED** (2026-06-29, S25): the relocated `.record.*` services resolve and run at
>    runtime; recording + both notification icons + tap-to-open all work. See "On-device verification" below.
> 4. **Phase 1 has begun:** the engine-API `TripRepository` seam + a `:ui-next` Compose module (first trip-list
>    screen) are built, pushed, and **render-verified on the S25**. See ":ui-next walking skeleton" below.

---

## Where we are

- **Branch:** `ux-premium-modular-v1` — **local only, NOT pushed, NOT merged** to `main`.
- **Last code commit:** `c43506a` (Phase 1B commit 2); everything since is docs-only. (Intentionally not pinning an
  exact HEAD / commit count here — it churns with every doc edit; use `git log` for the live value.)
- **Build state:** green. `:core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` succeeds via the
  OneDrive relocate workaround; no build output leaks into the synced tree.
- **Tests:** **223 total / 0 failures / 0 errors**, redistributed after the move: **`:app` 83** (13 `ui` suites),
  **`:core-engine` 140** (22 engine suites + the `Entitlements` test).
- **App unchanged behaviorally:** v3.36 / build 147, Room schema v22.
- **On-device (S25, 2026-06-29):** PASSED — moved `RecordingService` runs as an FGS, both notification icons render,
  notification tap-to-open works, saved trips render. See "On-device verification" below.

## What Phase 0 did

- Created the branch off `main` (`3d3905a`), additive only; current app preserved as the working oracle.
- Scaffolded the `:core-engine` Android library (`namespace com.cartrip.engine`) holding the freemium **Entitlements
  seam** (`Entitlements` / `PremiumFeature` / `AlwaysPremiumEntitlements` — everything unlocked, no Play Billing, no
  paywall). Wired `:app` → `:core-engine`.
- Proved the key risk: **multi-module + the OneDrive relocate workaround work together** (each module's output goes to
  `cartrip-build-out/<module>`; nothing written into the OneDrive tree). Report: `REDESIGN_PHASE0.md`.

## What Phase 1A did

- Untied the only upward (engine→ui) dependency: moved `ui/VehiclePrefs.kt` → new
  `com.cartrip.analyzer.settings` package. SharedPreferences name `cartrip_vehicle`, all keys/defaults, and fuel
  behavior preserved. Result: `analysis / cloud / data / record / export` import **nothing** from `ui`.

## What Phase 1B-prep did (the decoupling)

`record` was the last engine package coupled to `:app`. Both couplings were removed before the move:

- **R class:** `record/AutoRecordController.kt` (`R.mipmap.ic_launcher` → `applicationInfo.icon`),
  `AutoRecordWatchService.kt` + `RecordingService.kt` (`R.drawable.ic_stat_record` →
  `EngineR.drawable.engine_ic_stat_record`, a byte-for-byte vector copy now in the engine). `applicationInfo.icon` ==
  `@mipmap/ic_launcher`, so the launcher-icon notifications are unchanged.
- **MainActivity (`d0ee440`):** 4 notification "tap to open" `PendingIntent`s built `Intent(this,
  MainActivity::class.java)`. Replaced each with `packageManager.getLaunchIntentForPackage(packageName)` (same
  launcher activity = MainActivity, identical tap-to-open behavior), dropping the import.

> **Lesson:** the original R-decouple report wrongly claimed `record` was free to move — it still imported
> `MainActivity`. Always **independently grep the engine packages for app/`ui`/`MainActivity`/`TripApp` references
> right before a module move** — do not trust a prior audit.

## Phase 1B — DONE ✅ (the cluster extraction)

Two commits, each green via the relocate build:

- **`e41312e` — commit 1: extract the engine into `:core-engine`.** `git mv` the 6-package cluster
  (`analysis`, `data`, `cloud`, `record`, `export`, `settings` — 49 `.kt`) plus the Room `schemas/` from `:app` into
  `:core-engine`. Packages KEPT as `com.cartrip.analyzer.*` (50 of 51 files are pure renames). `:app` keeps `ui/` +
  `MainActivity`/`TripApp` and depends on `:core-engine`.
- **`c43506a` — commit 2: move the engine tests.** The 22 engine-package test files (analysis 12, cloud 4, export 2,
  record 4) → `:core-engine/src/test`; the 13 `ui` test suites stay in `:app`. Pure renames.

**Build/dep changes that landed with the move:**
- `:core-engine` gained: the `com.google.devtools.ksp` plugin + `room-compiler` + `room.schemaLocation` + the
  `schemas/` dir; Room exposed via **`api`** (app/ui consume DAO/entity/Flow); `implementation` of
  play-services-location/auth, okhttp, fastexcel; and (test) `org.json`.
- `:app` dropped the now-moved ksp plugin + room + okhttp + fastexcel; kept maps/compose/lifecycle/navigation and
  play-services-location/auth (each still used directly by one app/ui file).

**Four+ things the move surfaced (carry forward — they bite any module split):**
1. **The 6 packages are one dependency cycle** (analysis↔data, data↔cloud, …) → they had to move as **one atomic
   commit**; no subset compiles alone.
2. **`androidx.core:core-ktx:1.13.1`** — `record` uses `ContextCompat.registerReceiver` / `RECEIVER_*`; it reached
   `:app` only transitively, so `:core-engine` must declare it explicitly.
3. **`kotlinx-coroutines-android:1.7.3`** — `record/RecordingService` uses `Dispatchers.Main`; `lifecycle-*` (which
   provided coroutines-android transitively) stays in `:app`, so declare it explicitly in `:core-engine`.
4. **`internal` breaks across the module boundary** — `SpeedLimits.speedingSummary` was `internal` (module-scoped)
   and is called 13× from `app/ui/TripViewModel`; made it **public**. It was the only `internal` member in any moved
   package.
5. **`org.json` at test runtime** — the `cloud` JSON-parser tests exercise `org.json` via cloud code; Android stubs it
   to null in JVM unit tests, so `:core-engine` needs `testImplementation("org.json:json:20231013")`.

## On-device verification — PASSED ✅ (S25, 2026-06-29)

Installed the branch APK (`adb install -r`, data preserved; app stays v3.36/147) on the owner's S25 and ran an
owner-assisted pass. The runtime behavior that assemble + unit tests cannot prove is confirmed:

- ✅ **Relocated `.record.*` services resolve at runtime.** `dumpsys` showed `ServiceRecord{…
  com.cartrip.analyzer/.record.RecordingService}` `isForeground=true` (type LOCATION) — the app manifest's `.record.*`
  declaration correctly binds to the class now in `:core-engine`. Gyro/gravity sensors registered, GPS fixes
  accumulating.
- ✅ **Both notification-icon decouples render.** Recording notice small icon = `engine_ic_stat_record`
  (`:core-engine` drawable, merged as `0x7f060013`); the "Trip not recorded" notice small icon = `applicationInfo.icon`
  (launcher `mipmap 0x7f0a0000`). Both resolve and draw in the shade/status bar.
- ✅ **Notification tap-to-open works** (the `d0ee440` `getLaunchIntentForPackage` change): tapping the recording
  notification foregrounded `com.cartrip.analyzer/.MainActivity`.
- ✅ **Full `RecordingService` lifecycle** start → record → stop → finalize ran; finalize correctly rejected a
  stationary 0 m trip as too-short. **Saved trips render** normally in Past trips (moved `data`/`analysis` read path).
- ⚪ **`AutoRecordWatchService` (auto-record watcher) not exercised** this pass (optional, owner ended early) — but it
  reuses the exact engine-icon + `getLaunchIntentForPackage` patterns already verified in `RecordingService`, and the
  `d0ee440` edit to its single call site is identical. Low risk; verify opportunistically on a future charger/BT cycle.

Only remaining residue: a cosmetic KDoc link `[com.cartrip.analyzer.ui.VehicleScreen]` in `settings/VehiclePrefs.kt`
(cross-module doc ref; kotlinc ignores KDoc — no build impact). Tidy when convenient.

## Recommended next steps (after the on-device check)

1. **(Optional) `MigrationTestHelper` Room migration tests** in `:core-engine` while schema v22 is fresh (needs an
   emulator/instrumented test) — locks the data contract.
2. **Engine-API facade — plan decided, implementation deferred** (see the next section). Do **not** build it
   wholesale now; grow it with `:ui-next`. **Do not start the Material 3 UI redesign or Play Billing yet.**

## Engine-API facade — decision (advisory, owner-approved 2026-06-29)

Grounded in a measured read of the current boundary: `:app` imports **~39 engine symbols across 24 files**;
`TripEntity` is the lingua franca (12 files); **`TripViewModel` is the only DAO holder** (`AppDatabase.get(app)
.tripDao()`, exposes `StateFlow<List<TripEntity>>`); recording is driven from `MainActivity` via `Intent(…,
RecordingService::class).setAction(ACTION_START)` + the global `object RecordingState`; **`Entitlements` is not
consumed by `:app` yet**.

**Core principle — grow the facade *with* `:ui-next`, do not build it wholesale now.** A facade's seams can only be
designed correctly when a real second consumer pulls on them; with no `:ui-next` yet, up-front scaffolding would
ossify into the wrong shape. And while legacy `:app` still imports engine internals, the boundary **cannot be
compiler-enforced** (`internal` is module-scoped and `:core-engine` is one module) — so the facade is a *forward
contract for `:ui-next`, enforced by convention*; the internals can only be sealed (`internal`) once legacy `:app/ui`
is retired.

**Shape — small role-based gateways, NOT a god `EngineFacade`.** Provided by a thin container built once in the
`Application`; add each gateway only when `:ui-next` needs it:
- `TripRepository` — reactive trip reads (cold `Flow`s; the consumer does `.stateIn`) + a *curated* write surface
  (rename / setUserIsDrive / delete / specific updates — **not** a raw `updateTrip(entity)`).
- `RecordingController` — `start()` / `stop()` + `state: Flow<RecordingState.Live>` (wraps the Intent/Service/singleton).
- `SyncGateway`, `ExportGateway`, `SettingsStore` — added as needed.
- `Entitlements` — already exists (Phase 0 seam); the container is its natural provider. **Do not gate anything yet.**

Lives in a new `com.cartrip.engine.api` package inside `:core-engine` (a separate `:engine-api` module is blocked for
now: Room `@Entity` types can't easily live in an interface-only module).

**Stays PUBLIC (do not hide):** `TripEntity` and the pure analysis value types + stateless functions (`StressScore`,
`DriverLoad`, `FuelEstimator`, `GeoUtils`, `TripKind`, `DriveEvent`, `EventType`, `TrackPoint`, …). These *are* the
contract; wrapping pure functions is pointless indirection. Keeping `TripEntity` public is a deliberate tradeoff — a
later switch to a mapped UI model would be facade-breaking, so revisit only if the schema starts churning.

**First things to HIDE later (in value order):** `AppDatabase` / `TripDao` (the #1 leak — arbitrary reads/writes) →
recording-service mechanics (`RecordingService` Intents, `AutoRecordWatchService.start/stop`, the `RecordingState`
singleton) → cloud-sync internals (`TripSync` / `CloudSync` / `RoutesClient` / `GoogleAuth`) → export/settings.

**Do NOT abstract yet:** a parallel UI `Trip` model; the `ui/` presentation-domain helpers (`GeoNamer`,
`HomeDetector`, `TripLabeler`, `DisplayEvents`, `TripBuckets`, `TripDataQuality`, `EventHotspots` — where they belong
is a separate question); `Entitlements` wiring.

**First real implementation step:** introduce **`TripRepository` only when the first `:ui-next` screen needs trip
data** — as that screen's data source, one small green commit. Not before.

**Future boundary test (the only thing that keeps the facade honest while visibility can't):** a JVM unit test that
scans `:ui-next` sources and asserts they import only `com.cartrip.engine.api.*` + public value types — **never**
`com.cartrip.analyzer.{data,cloud,record,export,settings}` internals. Add it alongside the first `:ui-next` code.

## :ui-next walking skeleton — DONE + render-verified (S25, 2026-06-30)

The facade decision above is now in motion — first slices built, pushed, and verified end-to-end:

- **`9b5489e` — engine-API seam:** `com.cartrip.engine.api.TripRepository` (read-only: `observeTrips():
  Flow<List<TripSummary>>`, `getTrip(id)`), a **`TripSummary` DTO** (owner-chosen over exposing Room's `TripEntity`,
  so `:ui-next` never imports persistence types), an `internal` DAO-backed impl + an `internal` pure mapper (the
  mapper is unit-tested directly rather than faking the many-method Room `TripDao`).
- **`1d0604c` — `:ui-next` module (walking skeleton):** a new Compose / Material 3 **library** (`com.cartrip.uinext`)
  depending on `:core-engine` only. `TripListNextScreen` renders real `TripSummary` rows (date/time, distance,
  duration) from `TripRepository`. Hosted from legacy `:app` behind a **debug entry** (a `DebugScreen` button → new
  `"uinext"` nav route); legacy screens untouched. `EngineBoundaryTest` (source-scan) guards the import boundary.
  Built via the now-**3-module** relocate toolchain; `ui-next/build` does not leak into the OneDrive tree.
- **`cd1f7a6` — tap-to-detail slice:** rows are clickable → an internal `navigation-compose` `NavHost`
  (`TripsNextRoot`, list → `detail/{id}`) → `TripDetailNextScreen`, which loads the tapped trip via
  `TripRepository.getTrip(id)` and shows the basic `TripSummary` fields. The host (`:app`) just calls
  `TripsNextRoot(onExit)` and never sees the inner screens; the boundary test auto-covers the new files (still passes).
- **`901c66c` — first-pass premium theme / shell:** `:ui-next` no longer inherits the legacy host theme — it has its
  own `CarTripNextTheme` (a restrained teal-on-deep-neutral palette, dark + light via `isSystemInDarkTheme`), a shared
  `NextScaffold` (`CenterAlignedTopAppBar` + back arrow), a 3-state list (loading spinner / empty / premium rows: date
  title + "distance · duration" subline + trailing chevron) and a detail built from an elevated card + divider. The
  middle dot is `0x00B7.toChar()` (ASCII source — Cp1252 trap). Behavior unchanged (`observeTrips` / `getTrip` / nav);
  `:ui-next` gained `material-icons-core`. Build green, boundary test still passes.
- **`3c6afc2` — Drive Stress score on the row + detail (first row-enrichment):** `TripSummary` gained
  `stressScore: Int?` / `stressBand: String?`, derived in the pure `toSummary()` mapper via the already-engine-side
  `StressScore.from(entity)` (null for non-drives / too-short → chip hidden). A new `:ui-next` `StressChip` renders a
  compact green→amber→red score pill whose **palette is owned by `:ui-next`** (mirrors `ui.StressColors` thresholds
  25/45/65; imports nothing legacy), shown on each list row and as a "Drive stress" detail line. **No helper move was
  needed** — `StressScore` was already public in `:core-engine`, so the engine-API boundary stayed clean (the guard
  test still passes). Relocate build green (3-module unit tests + `:app:assembleDebug`, 0 failures), no OneDrive leak.

**On-device render PASS (S25, 2026-06-30):** installed the branch APK; via Diagnostics → "Open :ui-next trip list",
the new Material 3 list rendered the **real** trips from the existing DB — values match the database (e.g. trip 1189
= 45.8 km / 44:23; 1190 = 7.7 km / 14:28), no crashes, and the **legacy screens still open normally** afterward. The
walking-skeleton loop (new module → engine API → Room → real data on a screen) is closed. **Tap-to-detail
(`cd1f7a6`) was also tap-through-verified on the S25:** tapping a row opened the detail with values matching the DB
(trip 1195 → #1195 / Mon Jun 29 6:44 p.m. / 3.4 km / 6:32), and Back returned to the list — confirming the
`getTrip(id)` lookup and the internal nav (and its back-stack) at runtime.

**Premium theme/shell visual PASS (S25, 2026-06-30):** the `901c66c` slice was visual-checked on device — the middle
dot renders correctly (no mojibake), and the restrained teal theme, the top app bar + back arrow, the premium rows
with trailing chevron, and the elevated detail card all render as intended in both list and detail. Confirms the
`:ui-next`-owned `CarTripNextTheme` (no legacy-host-theme inheritance) at runtime; functionality unchanged.

**Drive Stress chip PASS (S25, 2026-06-30, `3c6afc2`):** owner-directed on-device check — all items pass. The chip
renders **only for scorable drives**: the non-drive trip 1184 (1.3 km / 21:49) shows **no chip** while every
neighbouring drive does (cross-checked against the pulled Room DB). Values match the documented DB-replay anchors
(trip 1187 = 40, trip 1189 = **78**) and the bands are correct (23 "Calm", 35 "Moderate", 78 "High stress"). Tapping a
row opens the detail with a matching "Drive stress" line (trip #1181 → **35 / Moderate**, same as its row). Chip
shape/colour/contrast read correctly in **dark theme** (list + detail) as well as light, the middle-dot separator
renders (no mojibake), and the **legacy screens still work** (main, Diagnostics, Past trips with map + Safe/Comf/Pace
+ labels). NB: a **pre-existing, unrelated** crash was found in the *legacy* trip list's custom scrollbar
(`TripListScreen.kt:660` — `coerceIn(0f, trackH - thumbH)` throws on a transient zero-height draw frame); it is **not**
in any `:ui-next` / stress-chip code and is tracked separately (fix deferred, owner-decided).

**Next slices (per screen, no god facade):** the row now shows the Drive Stress score (`3c6afc2`). The placement
question is settled by the `StressScore` precedent — **pure scoring/label logic → `:core-engine` `analysis`; colour →
the UI module**. ✅ **The `TripScores` move is DONE (Rev CX, 2026-06-30):** `TripScores.from` now lives in
`:core-engine/analysis` (Compose `Color` dropped; green=good colour → new `ui/ScoreColors`; `TripScoresTest` moved
engine-side). It shipped alongside the new pure `analysis/DrivingIntelligence.kt` three-pillar model, surfaced in the
**legacy** `app/ui` screens (Trip Detail / Insights / AI export). ✅ **`:ui-next` adoption STARTED (Rev CX):**
`TripSummary` gained `smoothnessScore` / `smoothnessBand` / `driveQuality` (computed vehicle-free in the pure
`toSummary()` mapper via `DrivingIntelligence.from(this)`), and `TripDetailNextScreen` renders a Driving
Intelligence hero (Drive Quality headline + Smoothness & Demand pillar rows, new green=good `ScoreChip`).
**Efficiency is deferred in `:ui-next`** — it needs a vehicle profile the engine-api mapper can't hold (the
boundary test forbids `:ui-next` importing `settings.VehiclePrefs`); add it when a **`SettingsStore` / vehicle
gateway** lands, then show the third pillar + enrich the list row. `TripLabeler` is a later, heavier slice (needs
the per-point list + carries a stale hardcoded home).

✅ **`:ui-next` premium app shell (v3.41/build 152, S25-verified 2026-07-01):** graduated from a single
list+detail flow to a **bottom-nav shell (Trips / Health)** — one `Scaffold` in `TripsNextRoot` → `HomeShell`
(top bar + `NavigationBar`), trip data observed once and shared by both tabs; detail stays a full-screen
drill-in. The **Health tab** (`InsightsNextScreen`) is a Driving-Intelligence overview aggregated purely from
`TripSummary` (avg Smoothness + Demand pillars via the shared `PillarRow`/`ScoreChip`/`StressChip`, plus a
Drive-Quality "drive mix" count) — no vehicle needed, efficiency still deferred, band words derived locally
(boundary-clean). Device-verified: 43 scorable drives, Smoothness 88 / Demand 41, mix 26 easy-smooth / 16
smooth-under-pressure / 1 demanding-rough; tab switching + `EngineBoundaryTest` green.

**`:ui-next` map-first Trip Detail (v3.42/build 153, on `main`; build green, ON-DEVICE PASS 2026-07-01):**
implements the UX-spec's #1 principle ("map first, numbers second") on the detail screen. **S25-verified:** the
route map renders (real Google tiles, blue route polyline + start/end markers framed to the route) above the
date/distance/duration summary and the Driving Intelligence card — confirming the Maps API key propagates across
the merged manifest from the host `:app`.
Added the first read gateway beyond summaries: engine-API **`RoutePoint`** value type + **`TripRepository.getRoute(id)`**
(maps the persisted 1 Hz `AnalysisPointEntity` track to lat/lon, drops zero/invalid fixes; pure `toRoute()` mapper,
unit-tested). New **`TripMapHero`** (`maps-compose` added to `:ui-next`; API key from the host app's merged
manifest) renders the route polyline + start/end markers, framed to the route on `onMapLoaded`. `TripDetailNextScreen`
restructured: map hero -> date/distance/duration summary card -> the DI card. Boundary still clean (map SDK imports
allowed; only `com.cartrip.engine.api.*` from the engine). Committed on local `main` as `8f6c44e` (**push pending
owner OK** at handoff time). Efficiency pillar still deferred (vehicle gateway).

Then more screens; add gateways (`RecordingController`, `SyncGateway`, `ExportGateway`, `SettingsStore`) only as a
screen needs them. **M1** (engine self-describing manifest) before `:ui-next` hosts recording; **M3** (Room migration
tests) before any schema change.

## Validation command (run after ANY edit)

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :ui-next:testDebugUnitTest :core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
Then confirm: `BUILD SUCCESSFUL` (the build pipe can mask failures — grep for it); 223 tests / 0 failures in
`cartrip-build-out/{app,core-engine}/test-results/`; no fresh `*/build` in the OneDrive tree. *(cmd-redirected logs
are UTF-16; if invoking via background `cmd /c`, anchor the project dir with `gradlew.bat -p <dir>` — a bare relative
`gradlew.bat` lost the cwd.)*

## Related redesign requirement (later phase — not Phase 1)
A proper top-level **Settings** area is a documented redesign requirement (owner-requested 2026-06-29): consolidate
the scattered options (Home Options sheet + the 8+ `*Prefs` stores) into a Settings system. Full spec in
`ROADMAP_NEW.md` → "Settings architecture". The `com.cartrip.analyzer.settings` package (now in `:core-engine`) is its
natural backend home; the Premium/Account section is driven by the `:core-engine` Entitlements seam. Lands after the
new UI shell — **do not implement during Phase 1.**

## ⚠️ Reminders
- **Do NOT rename packages** (`com.cartrip.analyzer.*` stays).
- Do NOT start the Material 3 UI redesign or add Play Billing.
- Do NOT push or merge. Work in small green commits on `ux-premium-modular-v1`.
- Always build via the relocate workaround; never plain `gradlew` (OneDrive file locks + tree pollution).
