# UX Premium Redesign — Phase 1 Handoff (engine extraction)

> **HANDOFF FOR THE NEXT AGENT — read this first.**
> Two hard rules for this phase:
> 1. **Full Phase 1B engine extraction has NOT started.** Only Phase 0 + Phase 1A + this prep are done.
> 2. **Do NOT rename packages.** Keep `com.cartrip.analyzer.*` everywhere during extraction. Renaming +
>    moving modules + Room/KSP/manifest changes all at once is too much risk in one step. Rename later, if ever.

---

## Where we are

- **Branch:** `ux-premium-modular-v1` (local only — NOT pushed, NOT merged to `main`).
- **Last code commit:** `91e1c80` — Phase 1A (move VehiclePrefs). The doc commits after it are docs only.
- **Build state:** green. `:core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` succeeds via the
  OneDrive relocate workaround; no build output leaks into the synced tree.
- **App unchanged behaviorally:** v3.36 / build 147, Room schema v22.

## What Phase 0 did

- Created the new branch off `main` (`3d3905a`), additive only; current app preserved as the working oracle.
- Scaffolded an empty `:core-engine` Android library (`namespace com.cartrip.engine`) holding **only** the freemium
  **Entitlements seam** (`Entitlements` / `PremiumFeature` / `AlwaysPremiumEntitlements` — everything unlocked, no
  Play Billing, no paywall). Wired `:app` → `:core-engine`.
- Proved the key risk: **multi-module + the OneDrive relocate workaround work together** (each module's output goes to
  `cartrip-build-out/<module>`; nothing written into the OneDrive tree).
- Report: `REDESIGN_PHASE0.md`.

## What Phase 1A did

- Untied the only upward (engine→ui) dependency: moved `ui/VehiclePrefs.kt` → new
  `com.cartrip.analyzer.settings` package (`settings/VehiclePrefs.kt`). Package line only — SharedPreferences name
  `cartrip_vehicle`, all keys/defaults, and fuel behavior preserved (git saw a 91% rename).
- Updated import in `cloud/GasPrice.kt`; added imports to `InsightsScreen`, `TripDetailScreen`, `VehicleScreen`.
- **Result:** `analysis / cloud / data / record / export` now import **nothing** from `ui`.

## Exact current status

Phase 1B-prep investigation complete (this doc). **No code changed in this prep step — docs only.** The `record`
package still references the app `R` class; that decoupling is the recommended first move of Phase 1B (below).

## Exact `record` R references found (the only `R` usage in any would-be-engine package)

| File:line | Reference | Used as | Notification |
|---|---|---|---|
| `record/AutoRecordController.kt:160` | `R.mipmap.ic_launcher` | `setSmallIcon` | "Drive detected" start fallback |
| `record/AutoRecordWatchService.kt:192` | `R.drawable.ic_stat_record` | `setSmallIcon` | "Auto-record on" watcher FGS |
| `record/RecordingService.kt:1071` | `R.mipmap.ic_launcher` | `setSmallIcon` | "Couldn't auto-start your trip" |
| `record/RecordingService.kt:1093` | `R.mipmap.ic_launcher` | `setSmallIcon` | "Trip not recorded" |
| `record/RecordingService.kt:1126` | `R.drawable.ic_stat_record` | `setSmallIcon` | recording FGS notification |

Plus 3 `import com.cartrip.analyzer.R` lines: `AutoRecordController.kt:16`, `AutoRecordWatchService.kt:25`,
`RecordingService.kt:29`.

**Two resources only:**
- `res/drawable/ic_stat_record.xml` — a **self-contained vector** (record-circle, white tint; references 0 other
  resources). Copyable into `:core-engine/res/drawable/` later with **zero behavior change**.
- `@mipmap/ic_launcher` — the **adaptive launcher icon** (`mipmap-anydpi-v26/ic_launcher.xml` → references a
  foreground vector + background). Copying the whole adaptive set into the engine is messy. Confirmed in the manifest:
  `android:icon="@mipmap/ic_launcher"`, so **`context.applicationInfo.icon` returns this exact resource id** — a clean
  drop-in for `setSmallIcon(Int)` with no resource copy.

## Recommended next step (do this BEFORE moving the `record` package)

Decouple `record` from the app `R` so the package can move cleanly. Smallest safe path:

1. **`ic_stat_record` (2 refs):** when `record` moves, copy `ic_stat_record.xml` into `:core-engine/res/drawable/`;
   the refs then resolve against `com.cartrip.engine.R`. (Or do the copy first and switch the import — but the vector
   only needs to live in whichever module owns `record`.)
2. **`ic_launcher` (3 refs):** replace `R.mipmap.ic_launcher` with `applicationInfo.icon`
   (`context.applicationInfo.icon` in `AutoRecordController`; `applicationInfo.icon` inside the Services). Equivalent
   result, removes the app-resource dependency, **no adaptive-icon copy needed.**
3. Once both are done, the 3 `import com.cartrip.analyzer.R` lines can be removed and `record` is `R`-free.

**Caution:** all five references are in foreground-service / auto-record notification code — the most fragile,
device-behavior-dependent area. After editing, **verify on-device** that each notification still shows its icon
(start a trip → recording notification; trigger auto-record → "Auto-record on"). This is why this prep step left the
code untouched: the change is best made and on-device-verified together, not blind.

After the `record` R-decoupling is green, proceed with the rest of Phase 1B per the audit table below.

## Phase 1B full extraction audit (for after the R-decoupling)

Move `analysis + data + cloud + record + export` (+ `settings`) into `:core-engine` as **one** cluster (they are
mutually interdependent). `:app` keeps `ui/` + `MainActivity`/`TripApp` and depends on `:core-engine`.

| # | Item | Finding / action |
|---|---|---|
| Manifest | Services/receiver are `.record.*`; provider is framework `FileProvider`. | Packages are kept, so `.record.*` names still resolve via the library on the classpath — manifest needs no change. (Optionally move `<service>` decls into the engine manifest later.) |
| Engine deps | room (×3), play-services-location (×1), okhttp (×5), fastexcel/org.dhatim (×1), play-services-auth (×2), coroutines (×8). **maps = 0 (UI-only).** | Move these to `:core-engine/build.gradle.kts`; expose what `ui` needs as `api(...)`. Maps stays in `:app`. |
| Room/KSP | `:app` owns the `ksp` plugin, `room-compiler`, `room.schemaLocation = $projectDir/schemas`, and `schemas/` (v21, v22). | Move KSP plugin + room-compiler + `schemaLocation` arg + the `schemas/` dir to `:core-engine`. Add `MigrationTestHelper` tests while v22 is fresh. |
| Tests | 22 engine-package tests (analysis/cloud/export/record) are **pure JVM** (no Robolectric/`android.*`); 13 ui tests. | Move the 22 with the code to `:core-engine/src/test`; ui tests stay in `:app`. `:core-engine` testImpl needs junit; verify `org.json` need for cloud JSON-parser tests at move time. |

## Validation command (run after ANY edit)

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :core-engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
Then confirm: build/test green; no `*/build` written into the OneDrive tree; no new `engine→ui` imports.

## ⚠️ Reminders
- **Full Phase 1B extraction has NOT started.** The package cluster has not moved.
- **Do NOT rename packages** (`com.cartrip.analyzer.*` stays).
- Do NOT start the Material 3 UI redesign.
- Do NOT push or merge. Work in small green commits on `ux-premium-modular-v1`.
- Always build via the relocate workaround; never plain `gradlew` (OneDrive file locks + tree pollution).
