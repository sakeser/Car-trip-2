# UX Premium Redesign — Phase 0 Report

**Branch:** `ux-premium-modular-v1`
**Date:** 2026-06-29
**Scope:** Safe groundwork only. No redesign, no engine rewrite. Current app preserved as the working oracle.

Strategy context: modular extraction (engine → reusable module, new UI built against it),
freemium with **billing deferred** (Entitlements seam now, Play Billing later), heavy-push pace,
**foreground-only** first public release. See the strategic report / memory `ux-redesign-v2-premium-track`.

---

## 1. What changed

Five small, non-destructive changes (one commit, all additive):

| Change | File(s) | Note |
|---|---|---|
| New empty engine module | `core-engine/build.gradle.kts`, `core-engine/src/main/AndroidManifest.xml` | Android library, `namespace com.cartrip.engine`, compileSdk 34 / minSdk 26 / JVM 17 — matches `:app`. Holds **no engine code yet.** |
| Entitlements seam | `core-engine/.../premium/Entitlements.kt` | `Entitlements` interface, `PremiumFeature` enum, `AlwaysPremiumEntitlements` (everything unlocked). No billing, no paywall. |
| Seam test | `core-engine/.../premium/EntitlementsTest.kt` | Guards the "all unlocked" Phase 0 contract. |
| Module wiring | `settings.gradle.kts` (`include(":core-engine")`), root `build.gradle.kts` (`com.android.library` plugin), `app/build.gradle.kts` (`implementation(project(":core-engine"))`) | Proves the seam is consumable from `:app`. |
| Ignore module build dirs | `.gitignore` | `/core-engine/build` + `**/build/` safety net. |

**Not touched:** analytics, Room entities/migrations, recording, auto-record, scoring, exports, any UI screen,
version/build number (still 3.36 / 147), schema (still v22). No Material 3 redesign started.

## 2. What still builds (verification)

| Check | Result |
|---|---|
| Baseline before changes (`:app:testDebugUnitTest :app:assembleDebug`) | **BUILD SUCCESSFUL**, tests green |
| OneDrive relocate workaround (single-module) | **Works** — output in `cartrip-build-out/app`, nothing written to `app/build` |
| After scaffold (`:core-engine:testDebugUnitTest :app:assembleDebug`) | **BUILD SUCCESSFUL** — engine compiled, EntitlementsTest passed, app linked + APK assembled |
| **Multi-module + relocate together** (the key Phase-1 risk) | **Works** — `:core-engine` output relocated to `cartrip-build-out/core-engine`; no `core-engine/build` in the OneDrive tree |
| App still runnable as the oracle | Yes — APK assembles unchanged in behavior |

Build command used (unchanged workaround):
```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' `
  :core-engine:testDebugUnitTest :app:assembleDebug --no-daemon
```

## 3. Repo state observations

- Single `:app` module, namespace `com.cartrip.analyzer`. AGP 8.5.2 / Kotlin 1.9.24 / compileSdk 34 / minSdk 26 / JVM 17.
- Source packages already mirror target modules: `analysis` (11), `cloud` (13), `data` (6), `record` (15), `export` (3), `ui` (38), plus `MainActivity`/`TripApp`. ~16.5k LOC main.
- **Test suite is healthy: ~37 unit-test files** (analysis/cloud/record/ui well covered). The older "zero tests" note is stale — good foundation for the validation track.
- Room schema export already configured (`schemas/` v21, v22 captured).
- Main app↔engine boundary today is `ui/TripViewModel.kt` (+ screen-level VMs in Insights/TripDetail/TripList).
- `app/build/` in the source tree is **stale residue** (Jun 22–28) from earlier non-relocated runs; gitignored, harmless.

## 4. Risks found

| Risk | Severity | Detail / mitigation |
|---|---|---|
| **`cloud/GasPrice.kt` imports `ui.VehiclePrefs`** | Med | The only "upward" dep (engine→ui). `VehiclePrefs` is a settings concern misfiled under `ui`. Move it into the engine in Phase 1 before/while extracting `cloud`. |
| `analysis` ↔ `data` interdependence (+ `record`/`export`/`cloud` cross-deps) | Low | They form one cohesive cluster → extract them **together** as a single `:core-engine`, not as five separate modules. |
| `data/SampleData.kt` reaches into `cloud` + `analysis` | Low | Sample/dev data generator tangles layers. Keep inside engine, or relocate to a dev source set later. |
| Split-package if engine code keeps `com.cartrip.analyzer.*` while in a module with `com.cartrip.engine` namespace | Med | Decide Phase-1 repackaging policy (keep packages vs. rename). Repackaging = large mechanical import churn but cleaner. Namespace ≠ package, but same package in two modules is discouraged. |
| `ACCESS_BACKGROUND_LOCATION` declared in manifest | Med (launch) | Foreground-only v1 decision means Phase 7 must gate/remove this for the public flavor and keep auto-record foreground-only. Not a Phase 0/1 concern. |
| Running Gradle **without** the relocate init script | Med | Recreates OneDrive file-lock failures and writes `*/build` into the tree. Always use the workaround. `**/build/` now gitignored as a safety net. |
| Unvalidated scored metrics (stress stop-and-go blind spot, outlier peak-G, ACWR suppressed <14d) | High (for paid) | Not a Phase 0 issue, but gates the paywall. Addressed by the Phase 2 validation track before any feature is gated. |

## 5. Proposed module extraction plan (Phase 1)

Target topology:
```
:core-engine   analysis + data (Room) + record + cloud + export   (one cohesive module)
:ui-legacy     current ui/ screens + MainActivity/TripApp          (safety net, kept runnable)
:ui-next       new Material 3 product UI                            (built later, against engine API)
:app           thin assembly + flavors (free/premium later)
```
`:cloud` stays *inside* `:core-engine` for now (it's entangled with data/record); promote it to its own module
only if a real need appears. The five-package cluster moves as a unit.

**Recommended Phase 1 order (each step builds green before the next):**
1. **Untie the one knot:** move `VehiclePrefs` out of `ui` into the engine (settings/data). Rebuild.
2. **Decide repackaging policy** (keep `com.cartrip.analyzer.*` to minimize churn vs. rename to `com.cartrip.engine.*`). Recommend: keep packages initially to reduce risk; rename opportunistically later.
3. **Move `analysis` + `data` + `cloud` + `record` + `export`** from `:app` into `:core-engine`, updating `:app` to depend on it. Keep Room schema export wired in the engine module.
4. **Add `MigrationTestHelper` migration tests** in the engine while the schema (v22) is fresh — locks the data contract before UI work.
5. **Define a thin engine API surface** (facade over `TripViewModel`'s data needs) so `:ui-next` never reaches into engine internals.
6. Keep `:ui-legacy` building throughout as the oracle; do not start `:ui-next` until the engine extraction is green and tested.

**Do NOT in Phase 1:** rewrite algorithms, change Room schema, alter recording/auto-record behavior, start the Material 3 redesign, or add Play Billing.

## 6. Exact next steps

- [x] Confirm Phase 0 (this report) is accepted.
- [x] Decide repackaging policy → **keep packages** `com.cartrip.analyzer.*` (no rename).
- [x] Phase 1 step 1: relocate `VehiclePrefs` (Phase 1A, `91e1c80`).
- [x] Phase 1 step 3: move the packages into `:core-engine` (Phase 1B, `e41312e` code + `c43506a` tests).
- [ ] Phase 1 step 4: add migration tests for schema v22. *(open — needs an emulator)*
- [ ] Phase 1 step 5: define engine API facade. *(open)*
- [ ] Then Phase 2 (validation track) can begin in parallel with Phase 3 (UI shell).

**Status (updated 2026-06-29): Phase 0 + 1A + 1B all complete and green on `ux-premium-modular-v1` (local, not
pushed). Engine extracted to `:core-engine`; 223 tests / 0 failures. Outstanding: owner-assisted S25 on-device check
of the moved recording/auto-record services + notifications. See `REDESIGN_PHASE1.md` for the full handoff.**
