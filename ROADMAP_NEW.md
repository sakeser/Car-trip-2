# Roadmap additions (owner-requested 2026-06-28, post-Batch-4)

New items from the owner, assessed + ordered by value × (1/risk). Worked top-down ("clear the log,
easiest/least-risk first"). Rev letters continue from CJ.

> **⚠️ FRONTIER (updated 2026-06-28 — source v3.31 / build 142, schema v21, 200 tests, all merged to `main`
> and pushed):** The authoritative plan lives in **`HANDOFF.md` §14 / §14.1** — read that first; this box is the
> quick status. Shipped since the CK–CN batch (v3.22): **Rev CO** doc-truth pass; **Rev CP** Drive Stress Score
> depth, **then `StressScore` + `TripKind` decoupled into `analysis/` (v3.26)**; **CR export-file retention**
> (v3.27); **Room migration-test FOUNDATION** (`exportSchema=true` + `schemas/.../21.json`, v3.27); **Places
> scaffold** (flag **OFF** / inert, v3.27); **Rev CP cont. (this session, v3.28–v3.31):** Trip-Detail
> **"Reset to automatic"** + a **visible OSM/ODbL attribution** credit (v3.28), a pure **shared bar-scale**
> helper / bar-sizing audit (v3.29), AI-export **Traffic** + **"When you drive"** sections (v3.30/v3.31), and
> export value-mapping tests. **On-device verified (S25 @ 3.31):** the "©" attribution renders correctly and the
> duration bars aren't edge-to-edge. **Still open (priority order):** **Past-Trips open affordance** — it's an intentional
> two-tap (tap once to preview, then tap the *same* trip again to open), **verified working on-device
> 2026-06-29**; the small wart is the second tap is easy to miss after the preview-line shift → make it more
> discoverable (lower priority, see §14 CP); **CQ** = *activate* Places (paid — needs the owner's go-ahead, a UI toggle,
> diagnostics, Geocoder-fallback wiring); **CR** = commercialization hardening (privacy policy, Data Safety,
> background-location / foreground-only) and **SQLCipher + biometric** at-rest encryption (gated, higher-risk —
> ask first). The Tier A–C items below are older backlog; cross-reference §14 + §13.3 before starting.

## Review notes (owner, 2026-06-29, post-Stress-v2) — solutions + build plan

Owner review of the recent Insights/chart/bar/trip-view updates + a new "drive stress built up over time"
concept. Assessment, solutions, and sequencing below; **pushback marked**. (Drive Stress v2 itself — Rev CS —
is shipped + device-verified; this is what's next.)

### Build plan (rev sequence, value x (1/risk))
- **Rev CV — Trip-view event defaults — DONE (v3.33, device-verified):** the "All events" list hides
  bumps/potholes by default (the Bumps chip reveals them); the list already opened by default. `ui/TripDetailScreen.kt`.
- **Rev CT — Insights chart & filter overhaul — MOSTLY DONE (v3.33, device-verified):** dynamic
  `1/3/7/30/All` days filter (default 7d, dropped 500 km) + Drive-Stress chart fixed **0..100** with y-axis
  labels, no min/max footer, x-axis label; the `TimeSeriesChart` rework also cleaned the fuel/traffic chart
  axes. **Still open (CT-fuel):** the Fuel **%-change-vs-30-day-baseline** chart + red/green shading + OEM
  (Tucson) reference line.
- **Rev CU — Bar-sizing pass 2:** finish the BarScale job across the remaining bars + investigate the
  you-vs-traffic edge. Small-medium.
- **Rev CW — Driver-Load / "Drive readiness" model (marquee R&D):** the cumulative, time-decaying load index
  + 24 h recovery forecast. Larger; design below; workshop calibration. The premium hook.
- _Misc (low priority):_ trip-end trim leaves a few seconds at 0 km/h on the tail — tighten
  `AutoStop.retrospectiveStopTime` to drop a trailing standstill (one observed case; fold into a polish rev).

### Item-by-item solutions
**Insights dynamic days filter (Rev CT).** Replace the `30 days / 500 km / All time` chips with
**`1d / 3d / 7d / 30d / All`** (matching the Past-Trips recency chips); the chosen window drives every Insights
section live (already windowed — widen the options + thread the window). _Pushback:_ a 1-day window is usually
0-2 drives (sparse) — keep it but **default 7d**; drop the niche `500 km` distance-window.

**Drive-Stress chart (Rev CT).** (a) **Fix the y-axis to 0..100** (full range, 0 at the x-axis) with labelled
gridlines (0/25/50/75/100) along the axis; **remove the "min X max Y" footer** (misleading on a fixed scale).
(b) **Label the x-axis:** the series is **per drive, oldest->newest** (one point per scorable drive, not per
km) — label "recent drives ->" or bucket by day for a true time axis. _Pushback:_ on a fixed 0..100 scale a
run of calm drives hugs the bottom (low contrast) — that's honest, and the **Driver-Load** curve (below) is
the better "trend over time" story. Needs a `TimeSeriesChart` variant with `yRange=0..100` + axis labels.

**Fuel & Cost chart (Rev CT).** Same y-axis treatment; plot **% change vs a 30-day rolling baseline** (=0%
line) so it reads "better/worse than your norm" (keep absolute L/100km in the caption); **smooth harder**
(longer trailing window); **red/green fill** when worse/better than the 30-day baseline; optional **OEM
reference line** from the Tucson combined L/100km rating (the `FuelEstimator.Vehicle` knows the vehicle).
_Pushback:_ "% change" needs an anchor — use the rolling 30-day mean as 0%, OEM as a separate reference line.

**Bars — auto-sizing pass 2 (Rev CU).** `BarScale` (v3.29) already gives the ETA bar a ~80% nice axis and
de-edge-to-edged the duration bars, but the owner still sees you-vs-traffic near full width: **(1) re-check
`EtaCompare` on-device** (likely the animated wipe, or actual ~= axis max — ensure the ~80% cap holds + the
scale is legible); **(2) apply BarScale to the rest** — the "When you drive" daypart bars (busiest daypart
fills full width today), `PeakLimitBar`, `SpeedingShareBar`, Insights mini-bars — each with a **minimum
visible bar**, ~80-90% max fill, a labelled scale, and padding. Leave the 0-100 score bars (full = 100 is correct).

**Trip-view event defaults (Rev CV).** Default the **"All events" list open** (confirm post Rev CE) and the
**Bumps/potholes filter OFF** so bumps don't clutter the default view — initial event-filter state in `ui/TripDetailScreen.kt`.

### ⭐ Driver-Load / "Drive readiness" — cumulative, time-decaying stress model (Rev CW, design v0.1)

The owner's concept: a single number that **builds with recent stressful driving and decays with time** (rest),
so "you may have to wait to lower it." This is a real, **evidence-based** pattern — a recency-weighted
cumulative load with exponential decay — not a per-trip score and not an average.

**Evidence base (established models this maps onto):**
- **Acute:Chronic Workload Ratio (ACWR), EWMA form** (Williams et al., Br J Sports Med 2017, PMID 28003238):
  injury risk rises when recent ("acute") load outpaces the longer ("chronic") baseline; the EWMA version
  weights recent loads more + decays older ones — more sensitive than rolling averages (optimal ratio
  ~0.8-1.5, risk climbs >1.5). Exactly "are you driving more/harder than your norm lately."
- **Banister fitness-fatigue (impulse-response) model:** state = slow-decaying "fitness" minus fast-decaying
  "fatigue", each an exponentially-weighted sum of impulses. Our load = the **fatigue** term (fast decay).
- **Two-process model of sleep regulation** (Borbely): homeostatic pressure builds with wake, **decays
  exponentially** with rest — same leaky-integrator math.
- **Driver fatigue / time-on-task** (FMCSA hours-of-service evidence): crash risk rises with consecutive +
  cumulative driving hours and insufficient rest — grounds "driving too much recently = elevated risk."

**Model (v0.1):**
- Each drive deposits a **load impulse** `I_i = (stress_i / 100) * durationHours_i` (stress-weighted driving
  time: a calm 5-min hop ~ 0; a 45-min stressful crawl = large).
- **Driver Load** `L(t) = sum_i I_i * exp(-(t - t_i) / TAU)` — an exponentially-weighted leaky-integrator sum:
  **additive**, accumulates per drive, **decays with time** (same math as the ACWR "acute" term).
- **TAU ~ 1.2 days** (half-life ~20 h): a drive 3 days old contributes `exp(-3/1.2) ~ 8%` ("little
  difference" — matches the owner). Tunable; calibrate over time.
- **Hourly + 24 h forecast:** with no further driving `L(t+h) = L(t) * exp(-h/TAU)` — show the recovery curve
  + "back to baseline by ~9pm".
- **Scale to 0-100** ("Driver Load" / inverse "Readiness"), calibrated so a normal commute peaks moderate, a
  heavy multi-stressful-drive day peaks high.
- **Phase 2 (the evidence-backed flag): ACWR overlay** — acute EWMA (TAU~1.5 d) vs chronic EWMA (TAU~21 d);
  ratio >1.5 = "well above your recent norm." Surfaces overload independent of the absolute scale.

**UX:** an Insights "Driver Load / Readiness" gauge + the 24 h recovery curve + a one-line read. Pure /
computable from the trip list (stress + duration + timestamp) — **no schema change**, forecastable.

**Disclaimer (important):** a heuristic "driving load" indicator from the user's own data — **NOT a medical /
fitness-to-drive assessment**. Real fatigue depends on sleep, which the app doesn't measure ("time not
driving" is only a proxy for recovery). Frame as wellness/awareness, not a safety certification.

**Workshop questions (calibrate over time):** what Load level reads "high / consider resting"? Does a mid-day
non-driving gap count as recovery (yes, via decay)? Should night / very-early driving deposit more load?
Include the ACWR "vs your norm" flag in v1 or defer to phase 2?

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
- **Decouple `StressScore` out of `ui` — ✅ DONE (v3.26/build 137).** `StressScore` + `TripKind` (both pure)
  now live in `analysis/`; `color()` is `ui/StressColors`; `export/ExportData` imports `analysis.StressScore`
  (export→ui dependency removed). Tests moved to the `analysis` package; 179 green.
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
