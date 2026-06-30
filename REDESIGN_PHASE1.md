# UX Premium Redesign — Phase 1 Handoff (engine extraction)

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
the UI module**. So the next enrichment is the **`TripScores` move** (safety/comfort/pace): relocate `TripScores.from`
from `app/ui` into `:core-engine/analysis` mirroring `StressScore` (drop its Compose `Color`; move `TripScoresTest`),
then surface it. `TripLabeler` is a later, heavier slice (needs the per-point list + carries a stale hardcoded home).
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
