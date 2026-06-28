# Roadmap additions (owner-requested 2026-06-28, post-Batch-4)

New items from the owner, assessed + ordered by value × (1/risk). Worked top-down ("clear the log,
easiest/least-risk first"). Rev letters continue from CJ.

> **⚠️ FRONTIER (updated 2026-06-28):** CK–CN shipped in **v3.22**. A second-reviewer (Codex) audit then
> produced **Rev CO** (source bumped to 3.23/build 134) — a doc-truth + low-risk-correctness pass (docs reconciled to 3.23/build 134/schema
> v21; sample GNSS cleanup; AI-export labels; quick-toggle background-location gate; export-schema refresh;
> fuel-week wording). A **second re-review** then produced small follow-ups (system-guide `.docx` marked
> non-authoritative; HANDOFF "uncommitted"/test-count fixed; auto-record "foreground-only" label; export
> `UserTripName` rename; share guard) **plus Rev CP additions** (decouple `StressScore` out of `ui`; optional
> `GeneratedTripLabel` export column). **The authoritative plan for what's next (CP migration tests +
> StressScore decouple / CQ Places / CR commercialization) now lives in `HANDOFF.md` §14 / §14.1** — read
> that first. The Tier A–C items below are still valid backlog; cross-reference §14 + §13.3 before starting.

## ⭐ Drive Stress Score — depth (owner-requested 2026-06-28, HIGH interest)
The owner wants the Drive Stress Score made far more prominent and trended over time. Scope (own rev,
pairs with the **`StressScore` decouple** in HANDOFF §14.1 — do the new pure logic in the non-UI module):

- **Trip hero placement.** Surface stress in the **trip-detail top hero**, not the compact line lower down
  (`ui/TripDetailScreen.kt` ~L966-972). Show band + score with `StressScore.color` (green→red). ⚠️ It's the
  **inverse** of Safety/Comfort/Pace (higher = worse), so style it distinctly so the green=good convention
  isn't confused (e.g. a labeled "stress" chip/ring, not a 4th score ring).
- **Stress normalized by km.** Add a per-km stress read so a long highway cruise and a short crawl compare
  fairly (the composite is currently per-trip, partly duration-weighted). Add e.g. `StressScore.perKm(trip)`
  (pure, unit-tested) or a normalized field; decide whether the hero shows the composite, the per-km, or both.
- **Insights trend over time (last 30 days).** Replace/augment the single average `StressSummaryRow`
  (`ui/InsightsScreen.kt:383`) with a **time series** of stress per trip/day over the window, **smoothed with
  an EMA or trailing average** (mirror `FuelInsights` weekly trailing-avg + `ui/Charts.kt`
  `TimeSeriesChart`/`MiniSparkline`). Goal: "how has my driving stress evolved?"
- **Evolution visual + delta.** A sparkline/trend chip on the Insights stress card and the full chart, plus a
  "this window vs previous" delta (reuse the Rev CE delta-strip pattern).

Data/notes: computed from stored `TripEntity` aggregates → **no schema change** for per-trip or the trend
(the trend just aggregates across trips). EMA/trailing-avg + per-km are pure/testable. Needs enough trips to
read as a trend (owner's data is ~1 week now; grows over time). Re-validate calibration by DB-replay.

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
