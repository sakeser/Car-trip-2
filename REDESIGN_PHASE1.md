# UX Premium Redesign — Phase 1 Handoff (engine extraction)

> **HANDOFF FOR THE NEXT AGENT — read this first.**
> 1. **Phase 1B (the engine extraction) is COMPLETE** — `analysis/data/cloud/record/export/settings` + their
>    tests now live in `:core-engine`. Build green, 223 tests / 0 failures. See "Phase 1B — DONE" below.
> 2. **Do NOT rename packages.** They stay `com.cartrip.analyzer.*` inside `:core-engine` (deliberate — renaming
>    on top of the module move was judged too risky; rename later, if ever).
> 3. **One outstanding item before further work:** owner-assisted **S25 on-device verification** of recording /
>    auto-record / notification icons / notification tap-to-open (assemble + unit tests cannot prove it).

---

## Where we are

- **Branch:** `ux-premium-modular-v1` — **local only, NOT pushed, NOT merged** to `main` (11 commits ahead).
- **HEAD:** `c43506a`.
- **Build state:** green. `:core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` succeeds via the
  OneDrive relocate workaround; no build output leaks into the synced tree.
- **Tests:** **223 total / 0 failures / 0 errors**, redistributed after the move: **`:app` 83** (13 `ui` suites),
  **`:core-engine` 140** (22 engine suites + the `Entitlements` test).
- **App unchanged behaviorally:** v3.36 / build 147, Room schema v22.

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

## ⚠️ Outstanding before any further phase work

- **Owner-assisted S25 on-device verification (NOT done).** Assemble + unit tests cannot prove runtime behavior of
  the moved code. The `.record.*` services (`RecordingService`, `AutoRecordWatchService`, `BootReceiver`,
  `CarPresenceService`) now live in the **library**, while the **app** manifest still declares them by kept package
  name. Install the branch APK (owner present — it overwrites the live app on the data-bearing phone) and confirm:
  - **recording works** end-to-end: tap "Start trip" → the recording FGS notification appears with the **record-dot
    icon**; stop → trip saved.
  - **auto-record works**: trigger the charger/BT path → the "Auto-record on" notification shows its icon.
  - **notification tap-to-open**: tapping each notification opens the app's main screen (covers the `d0ee440`
    content-intent change from `MainActivity::class.java` → launch intent).
  - the three launcher-icon notices still render (icon decouple).
  `RecordingService` is `exported=false`, so a trip can only be started by tapping the UI — hence owner-assisted.
- Cosmetic: a KDoc link `[com.cartrip.analyzer.ui.VehicleScreen]` in `settings/VehiclePrefs.kt` is now a cross-module
  doc ref; kotlinc ignores KDoc (no build impact) — tidy when convenient.

## Recommended next steps (after the on-device check)

1. **(Optional) `MigrationTestHelper` Room migration tests** in `:core-engine` while schema v22 is fresh (needs an
   emulator/instrumented test) — locks the data contract.
2. **Define a thin engine-API facade** (a stable surface over what `TripViewModel` needs) so a future `:ui-next`
   never reaches into engine internals. **Do not start the Material 3 UI redesign or Play Billing yet.**

## Validation command (run after ANY edit)

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon
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
