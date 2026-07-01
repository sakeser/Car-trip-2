# Merge-Readiness Review — `ux-premium-modular-v1` → `main`

_Reviewed 2026-07-01 at HEAD `3dc60d1` (v3.41 / build 152, Room schema v22). Device evidence is the
already-recorded S25 verification — no new device passes were run for this review._

## Verdict

**READY to merge to `main`**, with **one trivial reconciliation** (a duplicate scrollbar-fix hunk, below).
The branch is architecturally large but low-risk to merge, because the biggest new surface — the `:ui-next`
premium module — is **debug-gated** (reachable only via Diagnostics → "Open :ui-next preview"), so merging
does **not** expose incomplete premium UI to users. The user-facing changes (Driving Intelligence in the legacy
screens, the Connected-Features privacy toggle) are all device-verified.

> This review recommends the merge and lists the mechanics; it does **not** perform it. Merging `main` needs
> the owner's explicit go-ahead.

## Branch inventory

- **33 commits ahead of `main`, 0 behind local `main`** (clean fast-forward candidate from local main).
- Net change: **127 files, +4270 / −112**. Three Gradle modules: `:app`, `:core-engine`, `:ui-next`.
- Commit arc (bottom → top): Phase 0 (`:core-engine` + Entitlements) → Phase 1A (`VehiclePrefs`→`settings`) →
  Phase 1B (engine extraction) → engine-API `TripRepository`/`TripSummary` seam → `:ui-next` walking skeleton →
  theme/shell → Drive-Stress chip → **Rev CX (Driving Intelligence)** → Connected-Features privacy →
  Speed-Interruption sweep (evidence, decided: no new detector) → **`:ui-next` Trips/Health shell**.

## What merging actually changes for users

| Area | User-visible? | Status |
|---|---|---|
| Engine extraction into `:core-engine` (Phase 0–1B) | No — architectural, behavior-preserving | S25-verified (recording lifecycle, saved trips render) |
| **Driving Intelligence** in legacy Trip Detail / Insights / AI export (Rev CX) | **Yes** | S25-verified (3.38–3.39) |
| **Connected Features** toggle (default ON) + `autoSync` default OFF for fresh installs | **Yes** (privacy) | S25-verified (3.40) |
| Diagnostics "Export trip tracks (CSV)" | Debug-only | S25-verified (3.40) |
| `:ui-next` module (list/detail/Health shell) | **No — debug-gated** | S25-verified (3.41) but not user-reachable |

## Build / test status

- Last full 3-module relocated build: **BUILD SUCCESSFUL**, `:app:assembleDebug` OK.
- **~236 unit tests** pass (`:app` 81, `:core-engine` 154, `:ui-next` 1 incl. `EngineBoundaryTest`).
- **No schema change** this branch beyond what's already on main's lineage — stays **v22**, so no new Room
  migration risk is introduced by merging.
- Engine-boundary guard green: `:ui-next` imports only `com.cartrip.engine.api.*`.

## Device verification (recorded — not re-run)

| Slice | Build | Result |
|---|---|---|
| Phase 1B engine extraction | — | S25 PASS 2026-06-29 (services resolve, recording, saved trips) |
| `:ui-next` skeleton / theme / stress chip | — | S25 PASS 2026-06-30 |
| Rev CX Driving Intelligence (legacy + `:ui-next` detail/list) | 3.38–3.39 | S25 PASS 2026-06-30/07-01 |
| Connected Features toggle + CSV export | 3.40 | S25 PASS 2026-07-01 |
| `:ui-next` Trips/Health shell | 3.41 | S25 PASS 2026-07-01 |

## The one reconciliation point

`origin/main` has `bf031b8` ("Fix legacy trip-list scrollbar crash") — a **cherry-pick of the same fix** the
branch carries as `bfa68bf` (identical 3-line guard in `TripListScreen.kt`). Local `main` is 1 commit behind
`origin/main`. So before merging: **fast-forward local `main` from `origin/main` first**, then merge the branch.
The scrollbar hunk exists on both sides identically → git will either auto-resolve or raise a trivial add/add
conflict resolved by keeping the guard once (no behavior change either way).

## Recommended merge mechanics (owner runs, after explicit go-ahead)

```bash
git checkout main
git pull --ff-only origin main          # brings bf031b8 (the cherry-picked scrollbar fix)
git merge --no-ff ux-premium-modular-v1 # 33 commits; resolve the trivial TripListScreen dedup if prompted
# rebuild + test via the relocate workaround, then:
git push origin main
```

A `--no-ff` merge preserves the well-documented phase/Rev narrative. A squash is possible but would discard the
Phase 0→1B / Rev CX history that the docs reference by commit.

## Open items — track, but NOT merge blockers

- **`:ui-next` is incomplete** (no Efficiency pillar, no recording, no settings) — but debug-gated, so it can
  merge safely and mature on `main`. Next: Efficiency via a vehicle/`SettingsStore` gateway; then recording
  (`RecordingController` gateway + M1 manifest).
- **DI threshold-tuning question** — a short crawl reads "Demand 23 / Easy" despite "49% crawling" (correct
  Stress-v2 output; feels slightly off). Presentation-safe; scoring untouched. Needs owner-labeled trips to tune
  `DEMAND_HIGH_AT` / the Stress weighting — deliberately deferred.
- **Connected Features on the owner's device** keeps its existing `autoSync=ON` (the default flip only affects
  fresh installs; existing choices are preserved by design).
- **Speed-Interruption / Traffic-Wave detector** — decided **NO** after real-data calibration (prod `Drawdowns`
  fires 55 events / 19 of 51 trips; not blind). Closed unless a display-only insight is later wanted.

## Explicitly NOT on this branch (Play-launch blockers, separate from merge)

Merging to `main` is **not** the same as being ready for a Play release. Still open (per ADVISORY §4 / HANDOFF
§12): privacy policy + Play **Data Safety** form, **background-location** foreground-only default + disclosure
(CR), **SQLCipher + biometric** at-rest encryption (CO), **Places** activation (CQ). None block the merge; all
block a public release.

## Bottom line

Merge is low-risk and recommended. The heavy new module is debug-gated; the user-facing changes are verified;
tests and build are green; no schema migration is added. The only mechanical step is fast-forwarding local
`main` first so the duplicate scrollbar fix reconciles cleanly.
