# UX Premium Redesign — Phase 1 (engine extraction)

**Branch:** `ux-premium-modular-v1`
**Policy (agreed with Codex review):** keep `com.cartrip.analyzer.*` packages — **no renames** during extraction.
Combining package churn with module/Room/KSP/manifest moves in one step is too much risk. Rename later if ever.

---

## Phase 1A — DONE (2026-06-29)

Untied the only upward (engine→ui) dependency from the Phase 0 audit.

- Moved `ui/VehiclePrefs.kt` → `settings/VehiclePrefs.kt` (new `com.cartrip.analyzer.settings` package).
  Package line changed only; SharedPreferences name `cartrip_vehicle`, all keys/defaults, fuel behavior preserved.
- Updated import in `cloud/GasPrice.kt`; added imports to `InsightsScreen`, `TripDetailScreen`, `VehicleScreen`
  (`UiPrefs` only mentions it in a comment).
- **Result:** `analysis/cloud/data/record/export` now import nothing from `ui`. Build + `:app`/`:core-engine`
  unit tests green via relocate workaround; no output leaked to the OneDrive tree. No hidden coupling exposed.

## Phase 1B — engine extraction (NOT started; audit below)

Target: move `analysis + data + cloud + record + export` (+ `settings`) from `:app` into `:core-engine` as **one**
cohesive cluster (they are mutually interdependent — splitting five modules now is unrealistic). `:app` keeps
`ui/` + `MainActivity`/`TripApp` and depends on `:core-engine`.

### Pre-move risk audit (answers to Codex's questions)

| # | Question | Finding | Action at extraction |
|---|---|---|---|
| Q1 | `R` imports inside engine packages? | **Only `record`** uses `R`: `R.drawable.ic_stat_record` + `R.mipmap.ic_launcher` (notification icons), in `RecordingService`, `AutoRecordWatchService`, `AutoRecordController`. None elsewhere. | Move `ic_stat_record` (+ `ic_stat_record` related drawables) into `:core-engine/res`; for the launcher icon use `context.applicationInfo.icon` at runtime (no `R` ref) or inject the res id from `:app`. Smallest real friction in the whole move. |
| Q2 | Manifest service/provider refs need adjustment? | Services/receiver all in `record`: `.record.RecordingService`, `.record.AutoRecordWatchService`, `.record.BootReceiver`, `.record.CarPresenceService`. `<provider>` is framework `androidx.core.content.FileProvider` (not engine). | Because packages are **kept** (`com.cartrip.analyzer.record.*`), the app manifest's `.record.X` names still resolve via the library on the classpath. Optionally move these `<service>` decls into `:core-engine`'s manifest (cleaner; manifests merge). Low risk. |
| Q3 | Libs only in `:app` that `:core-engine` will need? | room (×3 files), play-services-location (×1), okhttp (×5), fastexcel/org.dhatim (×1), play-services-auth (×2), coroutines (×8). **play-services-maps = 0** (UI-only). | Move these deps to `:core-engine/build.gradle.kts`; expose what `ui` needs as `api(...)`. Maps stays in `:app`. |
| Q4 | Room schema export / KSP move? | `:app` has the `ksp` plugin, `room-compiler`, `room.schemaLocation = $projectDir/schemas`, and `schemas/com.cartrip.analyzer.data.AppDatabase/` (v21, v22). | Move the KSP plugin + room-compiler + `schemaLocation` arg + the `schemas/` dir to `:core-engine`. Add `MigrationTestHelper` tests in the engine while schema is fresh (v22). |
| Q5 | Tests move or stay? | 22 engine-package test files (analysis/cloud/export/record) vs 13 ui tests. Engine tests are **pure JVM** (no Robolectric/`android.*`). | Move the 22 engine-package tests to `:core-engine/src/test`; ui tests stay in `:app`. `:core-engine` testImpl needs junit; verify `org.json` need for cloud JSON-parser tests at move time. |

### Verdict
Phase 1B still looks safe and mostly mechanical. The only item needing real thought is Q1 (notification icons in
`record`). Everything else is config/dependency relocation made easier by the no-rename policy.

### Suggested Phase 1B step order (each green before the next)
1. Relocate `record`'s notification icons / drop `R` refs (Q1) — keep in `:app` first, verify behavior, then move res with the package.
2. Move third-party deps + KSP/Room schema config to `:core-engine` build file (Q3/Q4).
3. Move the package cluster (+ tests) into `:core-engine`; add `:core-engine`→`:app` `api` exposure where `ui` needs it (Q5).
4. Adjust manifest if declaring services in the engine module (Q2); otherwise leave as-is.
5. Full relocate build + on-device smoke (recording, auto-record, export) — these are the highest-risk behaviors.
6. Add migration tests (v22).

**Status: Phase 1A complete and green. Phase 1B audited, not started.**
