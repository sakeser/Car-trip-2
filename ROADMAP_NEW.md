# Roadmap additions (owner-requested 2026-06-28, post-Batch-4)

New items from the owner, assessed + ordered by value × (1/risk). Worked top-down ("clear the log,
easiest/least-risk first"). Rev letters continue from CJ.

> **⚠️ FRONTIER (updated 2026-06-28):** CK–CN shipped in **v3.22**. A second-reviewer (Codex) audit then
> produced **Rev CO** (source bumped to 3.23/build 134) — a doc-truth + low-risk-correctness pass (docs reconciled to 3.23/build 134/schema
> v21; sample GNSS cleanup; AI-export labels; quick-toggle background-location gate; export-schema refresh;
> fuel-week wording). A **second re-review** then produced small follow-ups (system-guide `.docx` marked
> non-authoritative; HANDOFF "uncommitted"/test-count fixed; auto-record "foreground-only" label; export
> `UserTripName` rename; share guard). **Rev CP (v3.24/build 135) then shipped the Drive Stress Score depth**
> (hero pill, km-weighted average, Insights smoothed trend — see the section just below for shipped-vs-open).
> **Still open** (not yet built): the `StressScore` **decouple** out of `ui`, Room **migration tests**, an
> optional `GeneratedTripLabel` export column, then **CQ** Places / **CR** commercialization. **The
> authoritative plan lives in `HANDOFF.md` §14 / §14.1** — read that first. The Tier A–C items below are still
> valid backlog; cross-reference §14 + §13.3 before starting.

## ⭐ Drive Stress Score — depth (owner-requested, HIGH interest)

### ✅ Shipped in Rev CP (v3.24/build 135)
- **Trip hero placement.** `StressHeroPill` headlines the trip-detail hero — a labeled pill on the green→red
  stress scale (NOT a 4th green=good ring, since higher = worse). Replaced the old compact "Drive stress:" line.
- **Km-weighted average.** `StressScore.kmWeightedAvg(trips)` — each trip's 0..100 score weighted by distance
  so long stressful drives count proportionally more. ⚠️ This is a km-**weighted average on the 0..100 band**,
  NOT a per-km burden rate (it does not divide stress by distance). UI labels it "km-weighted."
- **Insights trend.** `StressTrendCard` replaced the single-average `StressSummaryRow`: km-weighted headline +
  a **trailing-average-smoothed** `TimeSeriesChart` of how stress evolved + a delta vs the previous window
  (rise = red). Pure `series`/`trailingAvg` helpers, unit-tested.

### ⏳ Still open (CP follow-ups)
- **Decouple `StressScore` out of `ui`** (this rev deepened it — `series`/`kmWeightedAvg`/`trailingAvg` were
  added to `ui.StressScore`, and `export/ExportData` imports it). Move the pure logic to `analysis/`, leave
  only `color()` in `ui/`. A real multi-file refactor (~5 callers) — propose the plan + risk before doing it.
- **EMA vs trailing-average tuning** + per-user re-calibration as data grows (the trend currently uses a
  5-trip trailing average; an EMA may read smoother). Re-validate calibration by DB-replay.
- **(Optional) a true per-distance "burden" metric** — if we actually want stress *per km* (not a weighted
  average), that needs a defined stress-per-distance unit; design first. Not the same as `kmWeightedAvg`.

Notes: computed from stored `TripEntity` aggregates → **no schema change**. Needs enough trips to read as a
trend (owner's data was ~1 week at ship; grows over time).

## Tier A — easy, low risk (UI polish)
- **CK — Past-trips filter compaction + scroll affordance. ✅ DONE (v3.22).** Custom compact chips + an
  always-visible list scrollbar thumb. `ui/TripListScreen.kt`.
- **CL — Smart bar sizing. ✅ PARTIAL (v3.22).** You-vs-traffic now scales to a nice round-minute axis
  (~80% fill, minute scale). **Still TODO:** apply the same to `SpeedingShareBar`, `PeakLimitBar`
  (`ui/TripDetailScreen.kt`), `ui/Charts.kt` (`TimeSeriesChart`/`DivergingBarChart`), and `DurationBar`
  (`ui/TripListScreen.kt`) — nice max + headroom + **minimum** bar + scale labels, no edge-to-edge / near-zero.
- **CN — AI-readable export + share. ✅ DONE (v3.22).** `ui/AiInsightsExport.kt` builds a compact markdown
  summary (no raw GPS) shared via the Insights "Share for AI insights" button.

## Tier B — medium (analysis / value), low–medium risk
- **CM — POI-aware endpoint naming. ⏸ PAUSED — owner exploring the paid Google option.** The free on-device
  `Geocoder` rarely returns business names ("IKEA"), so a free version is best-effort only. The reliable path
  is **Places API (New)**. **Cost for a user like the owner: ~$0–3/month** (likely $0 within Google's free
  per-SKU allowance) with endpoint-only queries + aggressive cell caching — full analysis in **HANDOFF §13.4**.
  When enabled: new `cloud/Places.kt` + extend the `GeoNamer` cell cache (HANDOFF §11.4).

## Tier C — larger, higher risk (do deliberately, verify carefully)
- **CO — Encrypt-at-rest + biometric lock. ⏸ PAUSED — queued for a focused rev.** SQLCipher-encrypt the
  local DB (location history is sensitive) and gate app access behind BiometricPrompt. Needs a one-time
  encrypted-DB migration of the existing plaintext `cartrip.db`, a key in the Android Keystore, and a graceful
  fallback (device-credential when no biometrics). Pairs with the commercialization "secure the data" item and
  is a **pre-launch** privacy headline. Higher risk (DB layer + key management) — full plan in **HANDOFF
  §13.3 / §13.5**. Do not rush; verify the migration on-device.

## Commercialization / Play Store launch
Detailed phased roadmap + premium tiers in **HANDOFF §12 and §13.5**: pre-launch hardening (battery, CO
encryption, polish, AI coaching from the CN export), compliance (background-location disclosure +
foreground-only default, privacy policy, Data Safety), monetization infra (replace Sheets with a real
account/backend storing only compact summaries; Play Billing), and the premium dashboard (stress/drawdown
trends — built; Places destination analytics; cohort comparisons; AI coaching).

## Cross-cutting principle (owner): always look for novel, innovative ways to present and add value/analysis.
Examples to fold in opportunistically: AI coaching from the CN export; stress/drawdown trends over time;
"your worst-traffic times/routes"; per-destination analytics once POI naming (CM) lands.
