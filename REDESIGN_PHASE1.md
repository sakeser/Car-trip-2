# UX Premium Redesign — Phase 1 Handoff (engine extraction)

> **HANDOFF FOR THE NEXT AGENT — read this first.**
> Two hard rules for this phase:
> 1. **Full Phase 1B engine extraction has NOT started.** Only Phase 0 + Phase 1A + this prep are done.
> 2. **Do NOT rename packages.** Keep `com.cartrip.analyzer.*` everywhere during extraction. Renaming +
>    moving modules + Room/KSP/manifest changes all at once is too much risk in one step. Rename later, if ever.

---

## Where we are

- **Branch:** `ux-premium-modular-v1` (local only — NOT pushed, NOT merged to `main`).
- **Last code commit:** `d0ee440` — the `record` → MainActivity decouple; completes the Phase 1B-prep decoupling
  (the earlier R-decouple was not sufficient — see "record decouple" below).
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

Phase 1B-prep decoupling **DONE** (both the `R` class and `MainActivity`; see below). The engine-bound packages
(`analysis / cloud / data / record / export / settings`) now reference **no** app `R`, `ui`, `MainActivity`, or
`TripApp` in code — independently re-verified by grep on 2026-06-29. The package cluster itself has **not** moved
yet — that is the remaining Phase 1B work.

> **Lesson (2026-06-29):** the original R-decouple report claimed `record` was free to move, but `record` still
> imported `com.cartrip.analyzer.MainActivity` (4 notification content-intents). Always **independently grep the
> engine packages for app/ui/MainActivity/TripApp references right before the move** — do not trust a prior audit.
> One cosmetic residue remains: a KDoc link `[com.cartrip.analyzer.ui.VehicleScreen]` in `settings/VehiclePrefs.kt`;
> KDoc is ignored by kotlinc (no build impact), tidy it when `settings` moves.

## record decouple — DONE ✅ (R class + MainActivity)

**Part 2 — MainActivity (`d0ee440`, 2026-06-29):** `record` also imported `com.cartrip.analyzer.MainActivity` for
4 notification "tap to open" `PendingIntent`s (`AutoRecordWatchService` ×1, `RecordingService` ×3). Replaced each
`Intent(this, MainActivity::class.java)` with `packageManager.getLaunchIntentForPackage(packageName)` (same launcher
activity, identical tap-to-open behavior) and dropped the import. `record` now has zero app references.

**Part 1 — the R class (below):**

What was changed (the only `R` usage in any would-be-engine package, now resolved):

| File | Was | Now |
|---|---|---|
| `record/AutoRecordController.kt` | `R.mipmap.ic_launcher` (+ app `R` import) | `context.applicationInfo.icon`; app `R` import removed |
| `record/AutoRecordWatchService.kt` | `R.drawable.ic_stat_record` (+ app `R` import) | `EngineR.drawable.engine_ic_stat_record`; import = `com.cartrip.engine.R as EngineR` |
| `record/RecordingService.kt` | 2× `R.mipmap.ic_launcher`, 1× `R.drawable.ic_stat_record` (+ app `R` import) | 2× `applicationInfo.icon`, 1× `EngineR.drawable.engine_ic_stat_record`; import = `com.cartrip.engine.R as EngineR` |

New resource: `core-engine/src/main/res/drawable/engine_ic_stat_record.xml` — byte-for-byte copy of the original
self-contained vector, renamed to avoid collision. `applicationInfo.icon` == `@mipmap/ic_launcher` (manifest
`android:icon`), so the three launcher-icon notifications are unchanged. **No adaptive launcher icon copied.**

Verified: `grep "com.cartrip.analyzer.R|R\." record/` → CLEAN (no app-resource dependency in `record`). Full relocate
build (`:core-engine` + `:app` tests + `:app:assembleDebug`) green; cross-module reference to `com.cartrip.engine.R`
from the (still-in-`app`) `record` classes resolves; no output leaked to the OneDrive tree.

> **⚠️ On-device visual check still recommended** (device connected + branch builds green this session, but the
> notification render/tap was NOT visually verified on-device — the S25 holds the owner's live data + active test
> rides, so the WIP branch APK was not installed over it without the owner): start a trip → confirm the recording
> FGS notification shows the record-dot icon; trigger auto-record → confirm "Auto-record on" shows it; and the three
> launcher-icon notices still render. **Also tap each notification** to confirm it still opens the app — the
> content-intent moved from `MainActivity::class.java` to the package launch intent in `d0ee440`. The icons are a
> vector in the engine + the system launcher icon, so the render should be visually identical. Best done as an
> owner-assisted pass (RecordingService is `exported=false`, so a trip can only be started by tapping the UI).

## Recommended next step

Phase 1B-prep is now fully done (`R` + `MainActivity` decoupled, boundary verified clean), so the remaining Phase 1B
work is the actual cluster move. **Before moving, independently re-grep the engine packages for
app/`ui`/`MainActivity`/`TripApp` references** (a prior audit missed one — see the Lesson above). Proceed per the
audit table below — move `analysis + data + cloud + record + export` (+ `settings`) into `:core-engine` as one unit.
Note the cluster is mutually interdependent, so the code move is essentially **one atomic commit** (deps + KSP/Room +
`schemas/` + manifest move with it); the engine-package tests can follow as a second commit. When `record` physically
moves, `engine_ic_stat_record.xml` is already in the engine, so it just works; the `EngineR` alias can become a plain
same-module `R` reference at that point if desired.

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

## Related redesign requirement (later phase — not Phase 1)
A proper top-level **Settings** area is now a documented redesign requirement (owner-requested 2026-06-29):
consolidate today's scattered options (Home Options sheet + the 8+ `*Prefs` stores) into a Settings system with
sections Recording / Vehicle & Fuel / Maps & Display / Insights / Privacy & Data / Premium-Account. Full spec in
`ROADMAP_NEW.md` → "Settings architecture". Relevant here because the `com.cartrip.analyzer.settings` package (Phase 1A)
is its natural backend home, and the Premium/Account section is driven by the `:core-engine` Entitlements seam. Lands
after the engine extraction + new UI shell — **do not implement during Phase 1.**

## ⚠️ Reminders
- **Full Phase 1B extraction has NOT started.** The package cluster has not moved.
- **Do NOT rename packages** (`com.cartrip.analyzer.*` stays).
- Do NOT start the Material 3 UI redesign.
- Do NOT push or merge. Work in small green commits on `ux-premium-modular-v1`.
- Always build via the relocate workaround; never plain `gradlew` (OneDrive file locks + tree pollution).
